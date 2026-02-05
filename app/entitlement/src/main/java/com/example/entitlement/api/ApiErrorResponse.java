/*
 * どこで: Entitlement API
 * 何を: エラーレスポンスの共通フォーマットを定義する
 * なぜ: クライアントがエラー原因を識別しやすくするため
 */
package com.example.entitlement.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApiErrorResponse(ApiErrorCode code, String message) {}
