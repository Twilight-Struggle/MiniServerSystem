package com.example.gateway_bff;

import com.example.common.config.TimeConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@Import(TimeConfig.class)
@RestController
public class GatewayBffApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayBffApplication.class, args);
  }

  @GetMapping("/")
  public String home() {
    return "gateway-bff: ok";
  }
}
