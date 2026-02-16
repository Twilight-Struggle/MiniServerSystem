package com.example.gateway_bff.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.gateway_bff.api.response.ProfileAggregateResponse;
import com.example.gateway_bff.service.ProfileAggregateService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfileAggregateController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProfileAggregateControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ProfileAggregateService profileAggregateService;

  @Test
  void getProfileReturnsAggregatedPayload() throws Exception {
    when(profileAggregateService.aggregateByUserId("user-1"))
        .thenReturn(new ProfileAggregateResponse(Map.of("user_id", "user-1"), Map.of(), Map.of()));

    mockMvc
        .perform(get("/v1/users/user-1/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.user_id").value("user-1"));
  }
}
