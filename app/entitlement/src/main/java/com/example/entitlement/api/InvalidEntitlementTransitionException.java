/*
 * どこで: Entitlement API
 * 何を: 状態遷移の衝突(409)を表す例外を定義する
 * なぜ: 既に同じ状態の権利更新を明確に扱うため
 */
package com.example.entitlement.api;

public class InvalidEntitlementTransitionException extends RuntimeException {

    public InvalidEntitlementTransitionException(String message) {
        super(message);
    }
}
