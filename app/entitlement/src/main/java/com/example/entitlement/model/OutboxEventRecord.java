/*
 * どこで: Entitlement ドメインモデル
 * 何を: outbox_events の publish 対象レコードを表す
 * なぜ: Publisher が DB 行から必要情報だけを扱えるようにするため
 */
package com.example.entitlement.model;

import java.util.UUID;

public record OutboxEventRecord(
    UUID eventId, String eventType, String aggregateKey, String payloadJson, int attemptCount) {}
