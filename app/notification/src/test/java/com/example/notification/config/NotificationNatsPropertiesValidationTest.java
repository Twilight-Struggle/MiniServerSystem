/*
 * どこで: Notification 設定のバリデーションテスト
 * 何を: NotificationNatsProperties の Bean Validation を検証する
 * なぜ: 起動時に不正な NATS 設定を検出できるようにするため
 */
package com.example.notification.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationNatsPropertiesValidationTest {

    private static final String SUBJECT = "entitlement.events";
    private static final String STREAM = "entitlement-events";
    private static final String DURABLE = "notification-entitlement-consumer";
    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(2);
    private static final Duration ACK_WAIT = Duration.ofSeconds(10);
    private static final int MAX_DELIVER = 10;

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validationPassesWhenAllFieldsValid() {
        NotificationNatsProperties properties =
                new NotificationNatsProperties(SUBJECT, STREAM, DURABLE, DUPLICATE_WINDOW, ACK_WAIT, MAX_DELIVER);

        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void validationFailsWhenAckWaitIsZero() {
        NotificationNatsProperties properties =
                new NotificationNatsProperties(SUBJECT, STREAM, DURABLE, DUPLICATE_WINDOW, Duration.ZERO, MAX_DELIVER);

        assertFalse(validator.validate(properties).isEmpty());
    }

    @Test
    void validationFailsWhenMaxDeliverIsZero() {
        NotificationNatsProperties properties =
                new NotificationNatsProperties(SUBJECT, STREAM, DURABLE, DUPLICATE_WINDOW, ACK_WAIT, 0);

        assertFalse(validator.validate(properties).isEmpty());
    }
}
