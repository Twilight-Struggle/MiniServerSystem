package com.example.gateway_bff.service;

import com.example.gateway_bff.api.response.LoginRedirectResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OidcLoginService {

  public LoginRedirectResponse prepareLogin() {
    final String state = UUID.randomUUID().toString();
    final String nonce = UUID.randomUUID().toString();
    final String redirectUri = encode("http://localhost:18080/callback");
    final String url =
        "https://accounts.google.com/o/oauth2/v2/auth"
            + "?response_type=code"
            + "&scope="
            + encode("openid profile email")
            + "&client_id="
            + encode("dummy-client")
            + "&redirect_uri="
            + redirectUri
            + "&state="
            + encode(state)
            + "&nonce="
            + encode(nonce);
    return new LoginRedirectResponse(url, state);
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
