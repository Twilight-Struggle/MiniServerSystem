/*
 * どこで: Entitlement サービス補助
 * 何を: Idempotency-Key から 64-bit advisory lock のキーを生成する
 * なぜ: hashtext(32-bit) の衝突による不要な直列化を避けるため
 */
package com.example.entitlement.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyLockKeyGenerator {

  // 64-bit advisory lock 用に SHA-256 の先頭 8byte を使う。
  static final int LOCK_KEY_BYTES = 8;

  public long generate(String idempotencyKey) {
    // SHA-256 の先頭 8byte を 64-bit 値として使い、衝突を実質的に無視できる水準にする。
    // 64-bit でも衝突は理論上あり得るが、直列化の誤共有が問題になる規模ではほぼ起きない。
    // 文字コードは環境差を避けるため UTF-8 を固定で使用する。
    final byte[] hashed = hash(idempotencyKey);
    // ByteBuffer は Big Endian が既定。言語間での再現性を優先して変更しない。
    return ByteBuffer.wrap(hashed, 0, LOCK_KEY_BYTES).getLong();
  }

  private byte[] hash(String idempotencyKey) {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      // JVM が SHA-256 を提供しない場合は実行環境の前提が崩れているため即失敗させる。
      throw new IllegalStateException("SHA-256 algorithm not available", ex);
    }
  }
}
