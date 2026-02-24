package com.example.notification.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationMatchmakingNatsPropertiesValidationTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void validationPassesWhenAllFieldsValid() {
    final NotificationMatchmakingNatsProperties properties =
        new NotificationMatchmakingNatsProperties(
            "matchmaking.events",
            "matchmaking-events",
            "notification-matchmaking-consumer",
            Duration.ofMinutes(2),
            Duration.ofSeconds(10),
            10);

    assertTrue(validator.validate(properties).isEmpty());
  }

  @Test
  void validationFailsWhenAckWaitIsZero() {
    final NotificationMatchmakingNatsProperties properties =
        new NotificationMatchmakingNatsProperties(
            "matchmaking.events",
            "matchmaking-events",
            "notification-matchmaking-consumer",
            Duration.ofMinutes(2),
            Duration.ZERO,
            10);

    assertFalse(validator.validate(properties).isEmpty());
  }

  @Test
  void validationFailsWhenMaxDeliverIsZero() {
    final NotificationMatchmakingNatsProperties properties =
        new NotificationMatchmakingNatsProperties(
            "matchmaking.events",
            "matchmaking-events",
            "notification-matchmaking-consumer",
            Duration.ofMinutes(2),
            Duration.ofSeconds(10),
            0);

    assertFalse(validator.validate(properties).isEmpty());
  }
}
