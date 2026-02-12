/*
 * どこで: app/account/src/main/java/com/example/account/service/AdminUserService.java
 * 何を: 管理者限定のユーザー操作(停止)を提供
 * なぜ: 監査ログ必須の高権限処理を専用サービスへ分離するため
 */
package com.example.account.service;

import org.springframework.stereotype.Service;

@Service
public class AdminUserService {

    /**
     * 役割:
     * - 対象ユーザーを SUSPENDED 状態へ遷移させる。
     * - 同時に監査ログを必ず 1 件記録する。
     *
     * 期待動作:
     * - actorUserId は認証コンテキスト由来を前提とし、空値は拒否する。
     * - 既に SUSPENDED の場合の扱い(冪等成功/409)を仕様で固定し、テストで担保する。
     * - DB 更新と監査ログ保存を同一トランザクションで実施する。
     */
    public void suspendUser(String actorUserId, String targetUserId, String reason) {
        throw new UnsupportedOperationException("suspendUser is not implemented yet");
    }
}
