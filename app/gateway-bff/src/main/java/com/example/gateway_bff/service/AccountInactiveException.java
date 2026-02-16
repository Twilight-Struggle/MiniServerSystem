package com.example.gateway_bff.service;

public class AccountInactiveException extends RuntimeException {

  public AccountInactiveException(String message) {
    super(message);
  }
}
