/*
 * どこで: IdempotencyLockKeyGenerator の単体テスト
 * 何を: 生成結果の決定性と入力差分の反映を検証する
 * なぜ: ロックキーが安定して同一キーに一致することを保証するため
 */
package com.example.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdempotencyLockKeyGeneratorTest {

  private static final String IDEM_KEY = "idem-1";
  private static final String IDEM_KEY_OTHER = "idem-2";
  private static final String IDEM_KEY_EMPTY = "";

  private final IdempotencyLockKeyGenerator generator = new IdempotencyLockKeyGenerator();

  @Test
  void generateIsDeterministic() {
    final long first = generator.generate(IDEM_KEY);
    final long second = generator.generate(IDEM_KEY);

    assertThat(first).isEqualTo(second);
  }

  @Test
  void generateChangesWhenKeyChanges() {
    final long first = generator.generate(IDEM_KEY);
    final long second = generator.generate(IDEM_KEY_OTHER);

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void generateHandlesEmptyKeyDeterministically() {
    final long first = generator.generate(IDEM_KEY_EMPTY);
    final long second = generator.generate(IDEM_KEY_EMPTY);

    assertThat(first).isEqualTo(second);
  }
}
