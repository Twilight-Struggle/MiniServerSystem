# Account と Gateway-BFF の初期実装 + OIDC 連携(Keycloak)

## 目標
- `account` サービスをユーザー正本として追加する
- `gateway-bff` にログイン導線と認証済みユーザー取得 API を追加する
- 外部 Google 依存を避け、ローカル/CI で Keycloak を OIDC Provider として使えるようにする
- `gateway-bff -> account` を内部 API として接続し、最低限の認可・障害ハンドリングを入れる

## 全体データフロー(最終)
1. ブラウザが `GET /login` (`gateway-bff`) を呼ぶ
2. `gateway-bff` は `302 /oauth2/authorization/keycloak` を返す
3. Keycloak 認証後、`/login/oauth2/code/keycloak` に戻る(コールバックは Spring Security が処理)
4. `GET /v1/me` で `Authentication` を `OidcPrincipalMapper` が `OidcClaims` に正規化
5. `AccountResolveClient` が `POST /identities:resolve` を `account` に呼び出す
6. `account` が `provider+subject` を同定し、`userId/accountStatus/roles` を返す
7. `gateway-bff` は `accountStatus=ACTIVE` のみ許可し、`MeResponse` を返す

## Account app
### 入口 API
#### 同定(解決)
- `POST /identities:resolve`
- header: `X-Internal-Token` (デフォルト。`account.internal-api.header-name` で変更可能)
- request
```json
{
  "provider": "keycloak",
  "subject": "sub-123",
  "email": "user@example.com",
  "emailVerified": true,
  "name": "Taro Yamada",
  "picture": "https://..."
}
```
- response
```json
{
  "userId": "<uuid>",
  "accountStatus": "ACTIVE",
  "roles": ["USER"]
}
```

#### ユーザー参照/更新
- `GET /users/{userId}`
- `PATCH /users/{userId}` (`displayName`, `locale` を更新)

#### 管理者操作
- `POST /admin/users/{userId}:suspend?reason=...`
- Header: `X-Actor-User-Id` 必須
- 成功時 `204 No Content`

#### エラー応答
- `IllegalArgumentException` -> `400 BAD_REQUEST`
- `UnsupportedOperationException` -> `501 NOT_IMPLEMENTED`
- body: `{"code":"...","message":"..."}`

### 同定ロジック
- `provider + subject` を一意キーとして `identities` から検索
- 既存同定あり:
  - `email` / `email_verified` を更新して既存 `user_id` を返す
- 同定なし:
  - `user_id = UUID.nameUUIDFromBytes(provider + ":" + subject)` で決定論的に生成
  - `users` を `insertIfAbsent`、`identities` を insert、`account_roles` に `USER` を付与
- 競合時(`identities` insert が一意制約違反):
  - 再読込して勝者レコードを返す(並行リクエストの収束)

### データモデル(Flyway V1)
- `users(user_id PK, display_name, locale, status[ACTIVE|SUSPENDED], created_at, updated_at)`
- `identities(provider, subject PK, user_id FK, email, email_verified, created_at)`
- `account_roles(user_id, role PK, created_at)`
- `audit_logs(id PK, actor_user_id, action, target_user_id, metadata_json, created_at)`
- index:
  - `identities_user_id_idx`
  - `audit_logs_target_created_idx`

### セキュリティ(更新点)
- `POST /identities:resolve`:
  - `InternalApiAuthenticationFilter` で `X-Internal-Token` を検証
  - 一致時のみ `ROLE_INTERNAL` 付与
  - 未設定/不一致時は `403`
- `GET/PATCH /users/{userId}`:
  - `UserOwnershipAuthorizationManager` で所有者のみ許可
  - `ROLE_ADMIN` は他人の `userId` にもアクセス可能
- `/admin/**`:
  - `ROLE_ADMIN` 必須
- `/`, `/error`, `/actuator/health`, `/actuator/info` は `permitAll`

## Gateway-BFF app
### 入口 API
- `GET /login`: Keycloak 認可エンドポイントへ遷移させるための `302`
- `GET /v1/me`: 認証済みユーザー情報を返す
- `GET /v1/users/{userId}/profile`: account/entitlement/matchmaking の集約スケルトン

### 認証・認可方針
- Spring Security + `spring-boot-starter-oauth2-client`
- `oauth2Login()` で Authorization Code Flow を利用
- 未認証アクセス:
  - `/v1/me` は `401`
- 許可済みパス:
  - `/`, `/login`, `/error`, `/actuator/health`, `/actuator/info`
- logout:
  - `POST /logout`、CSRF ありで `204`、CSRF なしは `403`

### OIDC claims 正規化
`OidcPrincipalMapper` で以下を抽出:
- `provider`(registration id)
- `sub`, `email`, `email_verified`, `name`, `picture`
- `issuer`, `aud`, `exp`, `nonce`

### Account 連携(更新点)
- `OidcAuthenticatedUserService` が `OidcClaims` を `AccountResolveClient` に渡して業務ユーザーへ解決
- `AccountResolveClient` は `RestClient` で `account` の `/identities:resolve` を実呼び出し
- 内部ヘッダーを付与:
  - header名: `ACCOUNT_INTERNAL_API_HEADER_NAME` (既定 `X-Internal-Token`)
  - token: `ACCOUNT_INTERNAL_API_TOKEN`
- 障害時の扱い:
  - account `401/403/5xx`、通信失敗、タイムアウト、不正レスポンスを `AccountIntegrationException` に正規化
  - `GatewayApiExceptionHandler` で `502/504` + エラーコードに変換
- `accountStatus != ACTIVE` は `AccountInactiveException` として `403 ACCOUNT_INACTIVE`

## Keycloak 導入(ローカル/CI)
### インフラ
- `deploy/kustomize/infra/base/keycloak.yaml`
  - Keycloak `Deployment` / `Service` を追加
  - `quay.io/keycloak/keycloak:26.0`, `start-dev`
- `deploy/kustomize/infra/base/keycloak-ingress.yaml`
  - `keycloak.localhost` でブラウザアクセス可能

### アプリ設定
- `gateway-bff`:
  - `spring.security.oauth2.client.registration.keycloak`
  - `spring.security.oauth2.client.provider.keycloak.authorization-uri=${OIDC_AUTHORIZATION_URI}`
  - `spring.security.oauth2.client.provider.keycloak.token-uri=${OIDC_TOKEN_URI}`
  - `spring.security.oauth2.client.provider.keycloak.user-info-uri=${OIDC_USER_INFO_URI}`
  - `spring.security.oauth2.client.provider.keycloak.jwk-set-uri=${OIDC_JWK_SET_URI}`
- Helm values:
  - local:
    - `OIDC_AUTHORIZATION_URI=http://keycloak.localhost/realms/miniserversystem/protocol/openid-connect/auth`
    - `OIDC_TOKEN_URI=http://keycloak:8080/realms/miniserversystem/protocol/openid-connect/token`
    - `OIDC_USER_INFO_URI=http://keycloak:8080/realms/miniserversystem/protocol/openid-connect/userinfo`
    - `OIDC_JWK_SET_URI=http://keycloak:8080/realms/miniserversystem/protocol/openid-connect/certs`
  - ci:
    - `OIDC_AUTHORIZATION_URI=http://keycloak:8080/realms/miniserversystem/protocol/openid-connect/auth`
    - `OIDC_TOKEN_URI=http://keycloak:8080/realms/miniserversystem/protocol/openid-connect/token`
    - `OIDC_USER_INFO_URI=http://keycloak:8080/realms/miniserversystem/protocol/openid-connect/userinfo`
    - `OIDC_JWK_SET_URI=http://keycloak:8080/realms/miniserversystem/protocol/openid-connect/certs`
  - 共通で `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `ACCOUNT_INTERNAL_API_TOKEN` を注入

### 運用手順
- `docs/task/deploy.md` に以下を追加
  - Keycloak 起動確認手順
  - Realm/Client/User の初期設定
  - `/login` -> Keycloak -> `/v1/me` 疎通確認
  - OIDC well-known / certs の切り分け確認

## テスト方針と実装
- `account`:
  - Service 層単体テスト(同定、競合時フォールバック、suspend 監査ログ)
  - Repository 統合テスト(Testcontainers + Postgres)
  - Security テスト(内部トークン、所有者制御、admin 制御)
- `gateway-bff`:
  - Controller テスト(`/login`, `/v1/me`)
  - Security 統合テスト(401/302/logout 時の CSRF 挙動)
  - OIDC claims 正規化、認証ユーザー解決の単体テスト
  - `AccountResolveClient` の異常系テスト(401/403/5xx/timeout/invalid response)

## 既知のギャップ(次フェーズ)
- `/v1/users/{userId}/profile` はまだプレースホルダー(Map返却)で、実データ集約は未着手
- `account` の内部 API 認証は共有トークン方式のため、将来は mTLS/JWT などへ強化余地あり
- `gateway-bff` から account 呼び出しのリトライ/サーキットブレーカーは未実装
