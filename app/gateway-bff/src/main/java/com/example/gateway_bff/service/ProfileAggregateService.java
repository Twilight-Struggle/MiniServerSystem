/*
 * どこで: app/gateway-bff/src/main/java/com/example/gateway_bff/service/ProfileAggregateService.java
 * 何を: Account/Entitlement/Matchmaking の集約を担当
 * なぜ: クライアント向けに複数 API 呼び出しを 1 回に集約するため
 */
package com.example.gateway_bff.service;

import com.example.gateway_bff.api.response.ProfileAggregateResponse;
import org.springframework.stereotype.Service;

@Service
public class ProfileAggregateService {

    /**
     * 役割:
     * - 指定 userId の各サービス情報を収集し、1 レスポンスへ統合する。
     *
     * 期待動作:
     * - Account/Entitlement/Matchmaking へ個別に問い合わせる。
     * - timeout/retry/circuit-breaker を適用し、部分失敗時の返却方針を統一する。
     * - 結果形式はクライアントが扱いやすい固定キーで返す。
     */
    public ProfileAggregateResponse aggregateByUserId(String userId) {
        throw new UnsupportedOperationException("aggregateByUserId is not implemented yet");
    }
}
