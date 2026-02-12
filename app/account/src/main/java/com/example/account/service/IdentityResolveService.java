/*
 * どこで: app/account/src/main/java/com/example/account/service/IdentityResolveService.java
 * 何を: provider + subject から userId を解決/作成する業務ロジックを提供
 * なぜ: OIDC 連携時の同定ルールを一か所に集約するため
 */
package com.example.account.service;

import com.example.account.api.request.IdentityResolveRequest;
import com.example.account.api.response.IdentityResolveResponse;
import org.springframework.stereotype.Service;

@Service
public class IdentityResolveService {

    /**
     * 役割:
     * - identities を照会し、既存ユーザーならそのまま返却する。
     * - 見つからない場合は users/identities/roles を一貫性を保って新規作成する。
     *
     * 期待動作:
     * - provider + subject を同定キーとして扱い、email は補助属性として更新する。
     * - 同時初回ログイン競合時は一意制約違反を検出し、既存レコード再取得へフォールバックする。
     * - 応答には userId, accountStatus, roles を必ず含める。
     */
    public IdentityResolveResponse resolve(IdentityResolveRequest request) {
        throw new UnsupportedOperationException("resolve is not implemented yet");
    }
}
