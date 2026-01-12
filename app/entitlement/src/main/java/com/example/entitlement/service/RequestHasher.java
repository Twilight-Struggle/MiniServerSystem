/*
 * どこで: Entitlement サービス補助
 * 何を: Idempotency 判定用のリクエストハッシュを生成する
 * なぜ: 同一キーで異なるリクエストを検出するため
 */
package com.example.entitlement.service;

import com.example.entitlement.api.EntitlementRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequestHasher {

    private final ObjectMapper objectMapper;

    public String hash(String action, EntitlementRequest request) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        // 順序を固定し、同一入力で同じ JSON が出るようにする
        canonical.put("action", action);
        canonical.put("user_id", request.userId());
        canonical.put("stock_keeping_unit", request.stockKeepingUnit());
        canonical.put("reason", request.reason());
        canonical.put("purchase_id", request.purchaseId());
        try {
            String json = objectMapper.writeValueAsString(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize request for idempotency", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
