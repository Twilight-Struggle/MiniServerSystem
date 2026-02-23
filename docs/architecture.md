# Architecture MiniServerSystem

- どこで: `docs/architecture.md`
- 何を: MiniServerSystem 全体（Gateway-BFF / Account / Entitlement / Notification / Keycloak / NATS）のアーキテクチャを定義する
- なぜ: 実装と運用判断の前提を 1 つの文書で共有し、サービス追加時の整合性崩れを防ぐ

## 1. この文書の責務

この文書が扱う内容:
- システム境界と主要コンポーネント
- 認証・認可と信頼境界
- 同期 API フローと非同期イベントフロー
- 整合性・冪等性・障害時の収束方針
- 主要な設計判断と既知のギャップ

この文書が扱わない内容:
- SLO/SLI/アラート閾値（`docs/slo.md`）
- 障害モードの網羅一覧（`docs/failure-modes.md`）
- 障害時の具体オペレーション（`docs/runbook.md`）

## 2. 目的と非ゴール

### 2.1 目的
- OIDC ログイン後に業務ユーザー（`account` の user）へ安定的に解決する
- ユーザー情報の正本を `account` に集約し、BFF は集約・仲介に徹する
- 権利状態の正本を `entitlement` に置き、変更イベントを `notification` に確実伝播する
- 再送・重複・一時障害を前提に、安全に最終収束する

### 2.2 非ゴール
- マルチリージョン厳密整合
- グローバルな厳密全順序
- 強固なサービス間ゼロトラスト（現状は共有トークン方式）

## 3. システム境界とコンポーネント

### 3.1 Context
- Browser/Client は `gateway-bff` にアクセスする
- `gateway-bff` は OIDC Provider（Keycloak）で認証を行う
- `gateway-bff` は内部 API として `account` を呼び出す
- `entitlement` はドメインイベントを NATS JetStream に publish する
- `notification` はイベントを購読し通知データを更新する

### 3.2 Container
- Gateway-BFF Service（OAuth2 Login, API Aggregation）
- Account Service（Identity Resolve, User Profile, Admin Action）
- Entitlement Service（権利正本 + Outbox Relay）
- Notification Service（購読処理 + 送信状態管理 + DLQ）
- Keycloak（OIDC Provider）
- NATS JetStream（イベントブローカー）
- PostgreSQL（Account / Entitlement / Notification それぞれの DB）

## 4. コンポーネント責務

### 4.1 Gateway-BFF
主責務:
- `/login` を入口に OIDC Authorization Code Flow へ誘導
- 認証後 principal から OIDC claims を抽出し `account` で業務ユーザー解決
- `/v1/me` で userId/accountStatus/roles を返却
- `/v1/users/{userId}` と `/v1/users/{userId}` PATCH を `account` へ委譲
- `/v1/users/{userId}/profile` の集約 API を提供（現時点はスケルトン）

実装上の重要点:
- `GatewaySecurityConfig` で `oauth2Login()` を有効化
- 未認証時 `/v1/me` は `401` を返す
- `/logout` は CSRF 前提で `204 No Content`（CSRF なしは `403`）
- `account` 呼び出し時に内部トークンと転送ユーザーヘッダーを付与

### 4.2 Account
主責務:
- `POST /identities:resolve` で `provider + subject` を業務 userId に解決
- `GET/PATCH /users/{userId}` でユーザー情報参照・更新
- `POST /admin/users/{userId}:suspend` で停止操作と監査ログ記録

実装上の重要点:
- 初回解決時は `UUID.nameUUIDFromBytes(provider:subject)` による決定論的 userId 生成
- 競合時（一意制約違反）は勝者レコード再読込で収束
- role 初期付与は `USER`
- suspend 時は `audit_logs` へ操作記録を永続化

### 4.3 Entitlement
主責務:
- 権利状態の正本管理
- 同一トランザクションで outbox へイベント記録
- Relay で JetStream へ publish

### 4.4 Notification
主責務:
- JetStream からイベント購読
- `processed_events` による重複排除
- `notifications` を最終的に `SENT` または `FAILED` へ収束
- 恒久失敗の DLQ 隔離

## 5. 認証・認可と信頼境界

### 5.1 外部境界（Browser ↔ Gateway-BFF）
- 認証方式は OAuth2/OIDC（Authorization Code Flow）
- 認証状態は Spring Security セッションで保持
- BFF 公開 API は未認証を `401` で拒否

### 5.2 内部境界（Gateway-BFF ↔ Account）
- 共有トークンヘッダー（既定 `X-Internal-Token`）で内部呼び出しを識別
- `GET/PATCH /users/{userId}` では追加ヘッダーを転送
- `X-User-Id`: 呼び出し主体 userId
- `X-User-Roles`: 呼び出し主体 roles（`,` 区切り）

### 5.3 Account 側の認可判定
- `POST /identities:resolve` は `ROLE_INTERNAL` 必須
- `/admin/**` は `ROLE_ADMIN` 必須
- `GET/PATCH /users/{userId}` は所有者一致で許可
- `ROLE_ADMIN` は所有者チェックをバイパス

## 6. データモデル（要点）

### 6.1 Account DB
- `users(user_id PK, display_name, locale, status, created_at, updated_at)`
- `identities(provider, subject PK, user_id FK, email, email_verified, created_at)`
- `account_roles(user_id, role PK, created_at)`
- `audit_logs(id PK, actor_user_id, action, target_user_id, metadata_json, created_at)`

インデックス:
- `identities_user_id_idx`
- `audit_logs_target_created_idx`

### 6.2 Entitlement DB
- `entitlements`: 権利状態の正本
- `idempotency_keys`: API 冪等制御
- `outbox_events`: publish 前後状態の管理

### 6.3 Notification DB
- `processed_events`: event_id 重複排除
- `notifications`: 通知状態管理
- `notification_dlq`: 恒久失敗イベント隔離

## 7. 主要フロー

### 7.1 ログインと `/v1/me`
1. Browser が `GET /login`（gateway-bff）
2. gateway-bff は `302 /oauth2/authorization/keycloak`
3. Keycloak 認証後 `/login/oauth2/code/keycloak` へ戻る
4. Browser が `GET /v1/me`
5. gateway-bff が principal を `OidcClaims` に正規化
6. gateway-bff が `account /identities:resolve` を内部トークン付きで呼び出す
7. account が userId/status/roles を返却
8. gateway-bff は `status == ACTIVE` のみ許可しレスポンス返却

### 7.2 ユーザー参照・更新
1. Browser が `GET/PATCH /v1/users/{userId}` を呼ぶ
2. gateway-bff が呼び出し主体を解決（OIDC → account resolve）
3. gateway-bff が `X-Internal-Token`, `X-User-Id`, `X-User-Roles` を付与して account 呼び出し
4. account が所有者または admin を許可し、結果を返却

### 7.3 管理者停止
1. 管理者権限主体が `POST /admin/users/{userId}:suspend` 実行
2. account が対象 user status を `SUSPENDED` へ更新
3. account が `audit_logs` に監査記録を挿入

### 7.4 権利更新イベント
1. 決済サービス等外部サービス が entitlement API へ grant/revoke
2. entitlement が同一 Tx で `entitlements` と `outbox_events` を更新
3. Relay が outbox claim 後に JetStream publish
4. notification が受信し `processed_events` で重複排除
5. 通知処理結果を `notifications` に反映、必要時 DLQ 隔離

## 8. 整合性・冪等性・失敗時収束

### 8.1 同期 API
- entitlement API は `Idempotency-Key` で再送安全化
- account identity resolve は `provider + subject` 一意制約で収束
- account resolve の並行生成は再読込フォールバックで単一 user に合流

### 8.2 非同期イベント
- outbox 採用で DB 更新とイベント記録の論理一貫性を担保
- publish は at-least-once 前提（重複は許容）
- consumer 側で `processed_events` により冪等吸収

### 8.3 BFF の外部障害吸収
- account 側の 401/403/5xx/タイムアウト/不正レスポンスを `AccountIntegrationException` に正規化
- API エラーは `502/504` などへ変換
- `accountStatus != ACTIVE` は `403 ACCOUNT_INACTIVE`
- Istio `VirtualService` で `gateway-bff -> account` に timeout（既定 `1s`）を設定し、遅延時に早期失敗させる

## 9. デプロイと設定

### 9.1 Keycloak（local/ci）
- Keycloak を infra に常設し OIDC Provider として利用
- local は `keycloak.localhost` からブラウザアクセス
- gateway-bff は `authorization/token/userinfo/jwk-set` の各 URI を環境変数で受ける

### 9.2 内部 API 設定
gateway-bff 側:
- `ACCOUNT_BASE_URL`
- `ACCOUNT_INTERNAL_API_HEADER_NAME`（既定 `X-Internal-Token`）
- `ACCOUNT_INTERNAL_API_TOKEN`
- `ACCOUNT_USER_ID_HEADER_NAME`（既定 `X-User-Id`）
- `ACCOUNT_USER_ROLES_HEADER_NAME`（既定 `X-User-Roles`）

account 側:
- `account.internal-api.header-name`
- `account.internal-api.token`
- `account.internal-api.user-id-header-name`
- `account.internal-api.user-roles-header-name`

## 10. 観測設計（責務のみ）

- 各サービスはメトリクス・ログ・トレースを出力する
- gateway-bff では OIDC 失敗と account 連携失敗を識別可能にする
- entitlement/notification では outbox 滞留・再試行・DLQ 増加を監視対象にする

詳細は以下へ分離:
- `docs/slo.md`
- `docs/failure-modes.md`
- `docs/runbook.md`

## 11. 既知のギャップ

- `/v1/users/{userId}/profile` は現時点で実データ集約未実装（プレースホルダー）
- gateway-bff → account のリトライ/サーキットブレーカーは未導入（timeout のみ導入済み）
- 内部 API は Istio mTLS(STRICT) + AuthorizationPolicy でゼロトラスト化し、共有トークン方式はアプリレイヤーの追加ガードとして併用する

## 12. 主要設計判断

- 認証境界は BFF で一元化し、バックエンドは内部 API で分離する
- account をユーザー正本にし、OIDC subject と業務 userId の対応を吸収する
- イベント伝播は outbox + at-least-once + consumer 冪等で実運用耐性を優先する
- 障害時の可観測性と運用介入可能性（DLQ、監査ログ）を重視する

## 13. 関連ドキュメント

- OIDC + account/bff 詳細設計: `docs/design/20260215_1_account_bff_oidc.md`
- 信頼性目標: `docs/slo.md`
- 障害カタログ: `docs/failure-modes.md`
- 復旧手順: `docs/runbook.md`

## コンポーネント図

```mermaid
flowchart LR
  Browser[Browser / Client] -->|HTTPS| BFF

  subgraph Edge[Gateway-BFF]
    BFF[REST + OAuth2 Login]
    Mapper[OIDC Principal Mapper]
    AClient[Account Clients]
    BFF --> Mapper --> AClient
  end

  BFF -->|OIDC Auth Code| KC[Keycloak]
  AClient -->|Internal API| ACC[Account Service]

  subgraph AccountDB[PostgreSQL: Account DB]
    USERS[(users)]
    IDS[(identities)]
    ROLES[(account_roles)]
    AUDIT[(audit_logs)]
  end
  ACC --> USERS
  ACC --> IDS
  ACC --> ROLES
  ACC --> AUDIT

  Client2[Client/Admin Tool] -->|HTTP JSON| ENT[Entitlement Service]

  subgraph EntDB[PostgreSQL: Entitlement DB]
    ENTS[(entitlements)]
    OBOX[(outbox_events)]
    IDEM[(idempotency_keys)]
  end
  ENT --> ENTS
  ENT --> OBOX
  ENT --> IDEM

  ENT -->|publish| JS[NATS JetStream]
  JS --> NOTIF[Notification Service]

  subgraph NotifDB[PostgreSQL: Notification DB]
    PE[(processed_events)]
    NTF[(notifications)]
    DLQ[(notification_dlq)]
  end
  NOTIF --> PE
  NOTIF --> NTF
  NOTIF --> DLQ
```

## シーケンス図（ログイン〜業務ユーザー解決）

```mermaid
sequenceDiagram
  autonumber
  actor U as Browser
  participant B as Gateway-BFF
  participant K as Keycloak
  participant A as Account
  participant DB as Account DB

  U->>B: GET /login
  B-->>U: 302 /oauth2/authorization/keycloak
  U->>K: 認可リクエスト
  K-->>U: 認証後コールバック
  U->>B: GET /v1/me (session)
  B->>B: OIDC claims を正規化
  B->>A: POST /identities:resolve + X-Internal-Token
  A->>DB: identities(provider, subject) 解決/作成
  DB-->>A: user_id, status, roles
  A-->>B: userId/accountStatus/roles
  alt accountStatus = ACTIVE
    B-->>U: 200 MeResponse
  else accountStatus != ACTIVE
    B-->>U: 403 ACCOUNT_INACTIVE
  end
```

## シーケンス図（Entitlement イベント伝播）

```mermaid
sequenceDiagram
  autonumber
  actor C as Client/Admin Tool
  participant E as Entitlement API
  participant EDB as Entitlement DB
  participant R as Outbox Relay
  participant J as NATS JetStream
  participant N as Notification
  participant NDB as Notification DB

  C->>E: grant/revoke(Idempotency-Key)
  E->>EDB: Tx: entitlements + outbox_events + idempotency_keys
  E-->>C: 200/201

  loop relay polling
    R->>EDB: claim outbox (SKIP LOCKED + lease)
    R->>J: publish(event_id)
    alt success
      R->>EDB: mark PUBLISHED
    else failure
      R->>EDB: retry with backoff
    end
  end

  J-->>N: deliver event
  N->>NDB: INSERT processed_events(event_id)
  alt duplicate
    N-->>J: ACK
  else first
    N->>NDB: update notifications
    alt permanent failure
      N->>NDB: INSERT notification_dlq
    end
    N-->>J: ACK
  end
```
