/*
 * どこで: Entitlement API
 * 何を: Idempotency-Key 競合(409)を表す例外を定義する
 * なぜ: 同一キーで異なるリクエストを検出するため
 */
package com.example.entitlement.api;

public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException(String message) {
    super(message);
  }
}
