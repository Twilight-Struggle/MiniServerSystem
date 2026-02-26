/*
 * どこで: Matchmaking API
 * 何を: エラー応答の標準フォーマットを定義する
 * なぜ: 例外ハンドリング時のレスポンス形状を統一するため
 */
package com.example.matchmaking.api;

public record ApiErrorResponse(String code, String message) {}
