package com.example.account.api;

import com.example.account.api.request.IdentityResolveRequest;
import com.example.account.api.response.IdentityResolveResponse;
import com.example.account.service.IdentityResolveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class AccountIdentityController {

  private final IdentityResolveService identityResolveService;

  @PostMapping("/identities:resolve")
  public ResponseEntity<IdentityResolveResponse> resolveIdentity(
      @RequestBody IdentityResolveRequest request) {
    return ResponseEntity.ok(identityResolveService.resolve(request));
  }
}
