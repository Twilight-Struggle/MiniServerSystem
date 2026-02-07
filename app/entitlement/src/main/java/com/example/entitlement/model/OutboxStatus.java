/*
 * どこで: Entitlement outbox の状態管理
 * 何を: outbox_events.status の有効値を enum で表現する
 * なぜ: レイヤ内で不正な状態値を防ぎ、DB CHECK への依存を減らすため
 */
package com.example.entitlement.model;

// DBのCHECK制約と値を一致させ、アプリ側の型安全性を担保する。
public enum OutboxStatus {
  PENDING,
  IN_FLIGHT,
  PUBLISHED,
  FAILED
}
