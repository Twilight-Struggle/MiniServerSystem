package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProfileAggregateServiceTest {

  @Test
  void aggregateRejectsBlankUserId() {
    final ProfileAggregateService service = new ProfileAggregateService();
    assertThatThrownBy(() -> service.aggregateByUserId(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aggregateReturnsMaps() {
    final ProfileAggregateService service = new ProfileAggregateService();
    final var response = service.aggregateByUserId("user-1");
    assertThat(response.account()).containsEntry("user_id", "user-1");
  }
}
