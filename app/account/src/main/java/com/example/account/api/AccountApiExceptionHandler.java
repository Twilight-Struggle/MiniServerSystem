/*
 * どこで: app/account/src/main/java/com/example/account/api/AccountApiExceptionHandler.java
 * 何を: Account API の例外を標準エラー形式へ変換する
 * なぜ: 失敗時の契約を一定に保ち、呼び出し側の分岐を簡潔にするため
 */
package com.example.account.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AccountApiExceptionHandler {

    /**
     * 役割:
     * - 業務的に存在しないユーザーなど、リソース未発見を 404 へマッピングする。
     *
     * 期待動作:
     * - 例外メッセージをそのまま返却しつつ、コードを固定値で返す。
     * - 実装時は例外種別ごとに code を増やし、クライアント側の判定精度を高める。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ApiErrorResponse body = new ApiErrorResponse("BAD_REQUEST", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 役割:
     * - 未実装箇所の呼び出しを明示的に 501 相当として返す。
     *
     * 期待動作:
     * - スケルトン段階ではこのハンドラで失敗を可視化する。
     * - 実装完了後はこの例外が発生しない状態をテストで保証する。
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleNotImplemented(UnsupportedOperationException ex) {
        ApiErrorResponse body = new ApiErrorResponse("NOT_IMPLEMENTED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
    }
}
