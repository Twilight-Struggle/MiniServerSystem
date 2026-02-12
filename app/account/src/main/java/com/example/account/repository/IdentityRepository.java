/*
 * どこで: app/account/src/main/java/com/example/account/repository/IdentityRepository.java
 * 何を: identities テーブルへの永続化操作を定義する
 * なぜ: provider + subject での同定ロジックを一か所に集約するため
 */
package com.example.account.repository;

import com.example.account.model.IdentityRecord;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {

    /**
     * 役割:
     * - provider + subject をキーに既存 identity を取得する。
     *
     * 期待動作:
     * - account 解決の最初の照会ポイントとして利用する。
     * - 実装時は provider の正規化(大文字小文字)ルールをここで統一する。
     */
    public Optional<IdentityRecord> findByProviderAndSubject(String provider, String subject) {
        throw new UnsupportedOperationException("findByProviderAndSubject is not implemented yet");
    }

    /**
     * 役割:
     * - 新規 identity を作成する。
     *
     * 期待動作:
     * - provider + subject の一意制約違反時は呼び出し側が再読込できる例外を返す。
     * - 実装時は createdAt を DB 時刻かアプリ時刻かで統一する。
     */
    public IdentityRecord insert(IdentityRecord identity) {
        throw new UnsupportedOperationException("insert is not implemented yet");
    }

    /**
     * 役割:
     * - 可変属性(email/email_verified)を最新 claims で更新する。
     *
     * 期待動作:
     * - 同定キー(provider/subject)は変更しない。
     * - 変更がない場合も安全に呼び出せる冪等更新にする。
     */
    public void updateClaims(String provider, String subject, String email, boolean emailVerified) {
        throw new UnsupportedOperationException("updateClaims is not implemented yet");
    }
}
