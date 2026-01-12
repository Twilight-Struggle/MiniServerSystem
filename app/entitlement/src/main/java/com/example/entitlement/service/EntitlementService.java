/*
 * どこで: Entitlement サービス層
 * 何を: 権利の付与/剥奪と outbox/audit/idempotency の更新を担う
 * なぜ: トランザクション内で整合性を保つため
 */
package com.example.entitlement.service;

import com.example.common.TraceIds;
import com.example.entitlement.api.ApiErrorCode;
import com.example.entitlement.api.ApiErrorResponse;
import com.example.entitlement.api.EntitlementRequest;
import com.example.entitlement.api.EntitlementResponse;
import com.example.entitlement.api.EntitlementSummary;
import com.example.entitlement.api.EntitlementsResponse;
import com.example.entitlement.api.IdempotencyConflictException;
import com.example.entitlement.api.InvalidEntitlementTransitionException;
import com.example.entitlement.config.EntitlementIdempotencyProperties;
import com.example.entitlement.model.EntitlementAuditRecord;
import com.example.common.event.EntitlementEventPayload;
import com.example.entitlement.model.EntitlementRecord;
import com.example.entitlement.model.EntitlementStatus;
import com.example.entitlement.model.IdempotencyRecord;
import com.example.entitlement.repository.EntitlementAuditRepository;
import com.example.entitlement.repository.EntitlementRepository;
import com.example.entitlement.repository.IdempotencyKeyRepository;
import com.example.entitlement.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntitlementService {

    private static final String ACTION_GRANT = "GRANT";
    private static final String ACTION_REVOKE = "REVOKE";
    private static final String EVENT_GRANTED = "EntitlementGranted";
    private static final String EVENT_REVOKED = "EntitlementRevoked";
    private static final int SUCCESS_STATUS_CODE = HttpStatus.OK.value();
    private static final int CONFLICT_STATUS_CODE = HttpStatus.CONFLICT.value();

    private final EntitlementRepository entitlementRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final EntitlementAuditRepository auditRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final IdempotencyLockKeyGenerator lockKeyGenerator;
    private final RequestHasher requestHasher;
    private final ObjectMapper objectMapper;
    private final EntitlementIdempotencyProperties idempotencyProperties;
    private final Clock clock;

    @Transactional(noRollbackFor = InvalidEntitlementTransitionException.class)
    public EntitlementResponse grant(EntitlementRequest request, String idempotencyKey, String traceId) {
        return handleCommand(request, idempotencyKey, traceId, ACTION_GRANT, EntitlementStatus.ACTIVE, EVENT_GRANTED);
    }

    @Transactional(noRollbackFor = InvalidEntitlementTransitionException.class)
    public EntitlementResponse revoke(EntitlementRequest request, String idempotencyKey, String traceId) {
        return handleCommand(request, idempotencyKey, traceId, ACTION_REVOKE, EntitlementStatus.REVOKED, EVENT_REVOKED);
    }

    public EntitlementsResponse listByUser(String userId) {
        List<EntitlementSummary> items = entitlementRepository.findByUserId(userId).stream()
                .map(this::toSummary)
                .toList();
        return new EntitlementsResponse(userId, items);
    }

    private EntitlementResponse handleCommand(
            EntitlementRequest request,
            String idempotencyKey,
            String traceId,
            String action,
            EntitlementStatus status,
            String eventType) {
        String requestHash = requestHasher.hash(action, request);
        // 同一Idempotency-Keyの同時実行を直列化 (64-bit advisory lock)。
        long lockKey = lockKeyGenerator.generate(idempotencyKey);
        idempotencyKeyRepository.lockByKey(lockKey);
        // ロック取得後に再チェックし、登録済みなら同一レスポンスを再利用する。
        Optional<IdempotencyRecord> existing = idempotencyKeyRepository.findByKey(idempotencyKey);
        if (existing.isPresent()) {
            return reuseIdempotentResponse(existing.get(), requestHash);
        }
        Instant now = Instant.now(clock);
        String resolvedTraceId = resolveTraceId(traceId);
        try {
            // entitlements 更新は outbox/audit と同一トランザクションで実施する
            Optional<EntitlementRecord> record = upsertIfAllowed(request, status, now);
            if (record.isEmpty()) {
                throw new InvalidEntitlementTransitionException("already " + status.name());
            }
            String eventId = UUID.randomUUID().toString();
            String payloadJson = buildPayloadJson(record.get(), eventId, eventType, request, resolvedTraceId, now);
            // outbox に保存してから非同期 publish する
            outboxEventRepository.insert(
                    UUID.fromString(eventId),
                    eventType,
                    buildAggregateKey(record.get().userId(), record.get().stockKeepingUnit()),
                    payloadJson,
                    now);
            // 監査ログを保存し、後から操作の根拠を確認できるようにする
            auditRepository.insert(buildAuditRecord(record.get(), action, request, idempotencyKey, now));
            EntitlementResponse response = new EntitlementResponse(
                    record.get().userId(),
                    record.get().stockKeepingUnit(),
                    record.get().status().name(),
                    record.get().version(),
                    record.get().updatedAt());
            storeIdempotency(idempotencyKey, requestHash, SUCCESS_STATUS_CODE, response, now);
            return response;
        } catch (InvalidEntitlementTransitionException ex) {
            // 失敗応答も冪等に再利用できるよう、例外でも idempotency を保存する
            ApiErrorResponse errorResponse = new ApiErrorResponse(
                    ApiErrorCode.ENTITLEMENT_STATE_CONFLICT,
                    ex.getMessage());
            storeIdempotency(idempotencyKey, requestHash, CONFLICT_STATUS_CODE, errorResponse, now);
            throw ex;
        }
    }

    private EntitlementResponse reuseIdempotentResponse(IdempotencyRecord record, String requestHash) {
        if (!record.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("Idempotency-Key conflict");
        }
        try {
            if (record.responseCode() == SUCCESS_STATUS_CODE) {
                EntitlementResponse response = objectMapper.readValue(record.responseBodyJson(),
                        EntitlementResponse.class);
                return response;
            }
            if (record.responseCode() == CONFLICT_STATUS_CODE) {
                ApiErrorResponse errorResponse = objectMapper.readValue(record.responseBodyJson(),
                        ApiErrorResponse.class);
                throw toApiException(errorResponse);
            }
            throw new IllegalStateException("unsupported idempotency response code: " + record.responseCode());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse idempotency response", ex);
        }
    }

    private void storeIdempotency(String idempotencyKey,
            String requestHash,
            int responseCode,
            Object response,
            Instant now) {
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            Instant expiresAt = now.plus(Duration.ofHours(idempotencyProperties.ttlHours()));
            IdempotencyRecord record = new IdempotencyRecord(
                    idempotencyKey,
                    requestHash,
                    responseCode,
                    responseJson,
                    expiresAt);
            int updated = idempotencyKeyRepository.upsertIfExpired(record);
            if (updated == 0) {
                // 先頭で競合判定済みのため、未期限切れ衝突は不変条件違反として扱う。
                throw new IllegalStateException("idempotency invariant violated");
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize idempotency response", ex);
        }
    }

    private Optional<EntitlementRecord> upsertIfAllowed(
            EntitlementRequest request,
            EntitlementStatus status,
            Instant now) {
        if (status == EntitlementStatus.ACTIVE) {
            return entitlementRepository.upsertGrantIfNotActive(
                    request.userId(),
                    request.stockKeepingUnit(),
                    now,
                    request.reason(),
                    request.purchaseId(),
                    now);
        }
        if (status == EntitlementStatus.REVOKED) {
            return entitlementRepository.upsertRevokeIfNotRevoked(
                    request.userId(),
                    request.stockKeepingUnit(),
                    now,
                    request.reason(),
                    request.purchaseId(),
                    now);
        }
        throw new IllegalArgumentException("unsupported entitlement status: " + status);
    }

    private RuntimeException toApiException(ApiErrorResponse errorResponse) {
        if (errorResponse.code() == ApiErrorCode.ENTITLEMENT_STATE_CONFLICT) {
            return new InvalidEntitlementTransitionException(errorResponse.message());
        }
        if (errorResponse.code() == ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT) {
            return new IdempotencyConflictException(errorResponse.message());
        }
        if (errorResponse.code() == ApiErrorCode.BAD_REQUEST) {
            return new IllegalArgumentException(errorResponse.message());
        }
        throw new IllegalStateException("unsupported error code: " + errorResponse.code());
    }

    private String buildPayloadJson(
            EntitlementRecord record,
            String eventId,
            String eventType,
            EntitlementRequest request,
            String traceId,
            Instant occurredAt) {
        EntitlementEventPayload payload = new EntitlementEventPayload(
                eventId,
                eventType,
                occurredAt.toString(),
                record.userId(),
                record.stockKeepingUnit(),
                request.reason(),
                request.purchaseId(),
                record.version(),
                traceId);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize outbox payload", ex);
        }
    }

    private EntitlementAuditRecord buildAuditRecord(
            EntitlementRecord record,
            String action,
            EntitlementRequest request,
            String idempotencyKey,
            Instant now) {
        String detailJson = buildAuditDetail(request);
        return new EntitlementAuditRecord(
                UUID.randomUUID(),
                now,
                record.userId(),
                record.stockKeepingUnit(),
                action,
                request.reason(),
                request.purchaseId(),
                idempotencyKey,
                detailJson);
    }

    private String buildAuditDetail(EntitlementRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize audit detail", ex);
        }
    }

    private EntitlementSummary toSummary(EntitlementRecord record) {
        return new EntitlementSummary(
                record.stockKeepingUnit(),
                record.status().name(),
                record.version(),
                record.updatedAt());
    }

    private String resolveTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return TraceIds.newTraceId();
        }
        return traceId;
    }

    private String buildAggregateKey(String userId, String stockKeepingUnit) {
        return userId + ":" + stockKeepingUnit;
    }

}
