/*
 * どこで: app/account/src/main/java/com/example/account/repository/UserRepository.java
 * 何を: users テーブルへの永続化操作を定義するリポジトリ
 * なぜ: Service 層から SQL 実装詳細を分離し、テスト容易性を高めるため
 */
package com.example.account.repository;

import com.example.account.model.UserRecord;
import java.util.Optional;

import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    /**
     * 役割:
     * - userId をキーに users レコードを取得する。
     *
     * 期待動作:
     * - レコードが存在する場合のみ Optional に値を入れて返す。
     * - 実装時は roles を別テーブルで管理する前提のため、このメソッドは users 本体に集中させる。
     */
    public Optional<UserRecord> findByUserId(String userId) {
        throw new UnsupportedOperationException("findByUserId is not implemented yet");
    }

    /**
     * 役割:
     * - 新規ユーザーを作成する。
     *
     * 期待動作:
     * - userId は呼び出し側で生成し、作成成功時は保存された値を返す。
     * - 実装時は createdAt/updatedAt を同一時刻で初期化し、status は ACTIVE 固定にする。
     */
    public UserRecord insert(UserRecord user) {
        throw new UnsupportedOperationException("insert is not implemented yet");
    }

    /**
     * 役割:
     * - ユーザープロフィール(displayName/locale)を更新する。
     *
     * 期待動作:
     * - 部分更新を想定し、null 値の扱い(維持/クリア)を仕様として固定する。
     * - 返却値は更新後の最新状態とする。
     */
    public UserRecord updateProfile(String userId, String displayName, String locale) {
        throw new UnsupportedOperationException("updateProfile is not implemented yet");
    }

    /**
     * 役割:
     * - アカウント状態を更新する(主に ACTIVE/SUSPENDED)。
     *
     * 期待動作:
     * - 対象ユーザーが存在しない場合は Optional.empty を返す。
     * - 実装時は冪等に同一状態への更新を許容するかどうかをテストで固定する。
     */
    public Optional<UserRecord> updateStatus(String userId, String status) {
        throw new UnsupportedOperationException("updateStatus is not implemented yet");
    }
}
