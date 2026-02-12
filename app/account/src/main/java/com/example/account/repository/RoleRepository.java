/*
 * どこで: app/account/src/main/java/com/example/account/repository/RoleRepository.java
 * 何を: account_roles テーブル操作を定義する
 * なぜ: 初期ロール付与と RBAC 判定データの取得を分離するため
 */
package com.example.account.repository;

import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepository {

    /**
     * 役割:
     * - 指定ユーザーのロール一覧を取得する。
     *
     * 期待動作:
     * - 返却順は安定化(例: 昇順)させ、レスポンス差分のノイズを減らす。
     * - 実装時は未知ロールを無視するかエラーにするかを明確化する。
     */
    public List<String> findRolesByUserId(String userId) {
        throw new UnsupportedOperationException("findRolesByUserId is not implemented yet");
    }

    /**
     * 役割:
     * - 初期ロールを付与する。
     *
     * 期待動作:
     * - 初期実装では USER を必ず 1 件だけ付与する。
     * - 同じロールの重複付与はユニーク制約で防ぎ、冪等な操作にする。
     */
    public void grantInitialUserRole(String userId) {
        throw new UnsupportedOperationException("grantInitialUserRole is not implemented yet");
    }
}
