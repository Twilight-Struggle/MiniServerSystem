/*
 * どこで: app/account/src/main/java/com/example/account/service/UserService.java
 * 何を: 一般ユーザー情報の参照/更新を提供
 * なぜ: API 層からビジネスルールを分離し、テスト可能性を高めるため
 */
package com.example.account.service;

import com.example.account.api.request.UserPatchRequest;
import com.example.account.api.response.UserResponse;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    /**
     * 役割:
     * - 指定 userId のプロフィール/状態/ロールをまとめて返す。
     *
     * 期待動作:
     * - users と roles の取得結果を API 返却形式へ整形する。
     * - 対象未存在時は 404 相当の例外へ変換する。
     */
    public UserResponse getUser(String userId) {
        throw new UnsupportedOperationException("getUser is not implemented yet");
    }

    /**
     * 役割:
     * - displayName と locale を最小単位で更新する。
     *
     * 期待動作:
     * - 更新対象項目のみを変更し、他属性は維持する。
     * - 入力値の許容範囲(空文字/長さ/locale 形式)を検証し、不正値は 400 で返す。
     */
    public UserResponse patchUser(String userId, UserPatchRequest request) {
        throw new UnsupportedOperationException("patchUser is not implemented yet");
    }
}
