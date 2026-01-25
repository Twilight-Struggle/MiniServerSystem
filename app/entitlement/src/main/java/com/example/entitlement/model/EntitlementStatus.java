/*
 * どこで: Entitlement ドメインモデル
 * 何を: 権利状態の列挙を定義する
 * なぜ: 状態遷移と永続化の整合性を保つため
 */
package com.example.entitlement.model;

public enum EntitlementStatus {
    ACTIVE,
    REVOKED
}
