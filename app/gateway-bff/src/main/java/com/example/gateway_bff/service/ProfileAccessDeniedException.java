/*
 * どこで: Gateway-BFF サービス層
 * 何を: profile 集約APIの参照拒否を表現する
 * なぜ: 本人以外の profile 参照を 403 へ正規化するため
 */
package com.example.gateway_bff.service;

public class ProfileAccessDeniedException extends RuntimeException {

  public ProfileAccessDeniedException(String message) {
    super(message);
  }
}
