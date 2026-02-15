# Account と Gateway-BFF の初期実装 + OIDC 連携(Keycloak)

## 目標
- `account` サービスをユーザー正本として追加する
- `gateway-bff` にログイン導線と認証済みユーザー取得APIを追加する
- 外部Google依存を避け、ローカル/CIで Keycloak を OIDC Provider として使えるようにする
- 最低限の RBAC と監査ログを保持できる状態まで持っていく

## 全体データフロー
1. ブラウザが `GET /login` (`gateway-bff`) を呼ぶ
2. `gateway-bff` は `302 /oauth2/authorization/keycloak` を返し、Spring Security が OIDC 認可フローを開始
3. Keycloak 認証後、`/login/oauth2/code/keycloak` に戻る(コールバック処理はSpring Securityが担当)
4. `GET /v1/me` で `Authentication` を `OidcPrincipalMapper` が `OidcClaims` に正規化
5. `AccountResolveClient` が claims から業務ユーザーへ解決し、`accountStatus=ACTIVE` のみ通す
6. `MeResponse` として `userId/accountStatus/roles` を返す

## Account app
### 入口API
#### 同定(解決)
- `POST /identities:resolve`
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

### セキュリティ
- `account` 側は現時点で全エンドポイント `permitAll`

## Gateway-BFF app
### 入口API
- `GET /login`: Keycloak認可エンドポイントへ遷移させるための 302 を返す
- `GET /v1/me`: 認証済みユーザー情報を返す
- `GET /v1/users/{userId}/profile`: account/entitlement/notification の集約用スケルトン

### 認証・認可方針
- Spring Security + `spring-boot-starter-oauth2-client`
- `oauth2Login()` で Authorization Code Flow を利用
- 未認証アクセス:
  - `/v1/me` は `401`
- 許可済みパス:
  - `/`, `/login`, `/error`, `/actuator/health`, `/actuator/info`
- logout:
  - `POST /logout`、CSRFありで `204`、CSRFなしは `403`

### OIDC claims 正規化
`OidcPrincipalMapper` で以下を抽出:
- `provider`(registration id)
- `sub`, `email`, `email_verified`, `name`, `picture`
- `issuer`, `aud`, `exp`, `nonce`

### Account 連携
- `OidcAuthenticatedUserService` が `OidcClaims` を `AccountResolveClient` に渡して業務ユーザーへ解決
- 現時点の `AccountResolveClient` は暫定実装:
  - `userId=subject`, `accountStatus=ACTIVE`, `roles=[USER]` を返す
  - 実HTTP連携( `account` の `/identities:resolve` 呼び出し)は未実装

### 後方互換の扱い
- 旧JSONベースログイン処理 (`OidcLoginService`, `OidcTokenVerifier`, `OidcCallbackService`) は
  `@Deprecated(forRemoval=true)` とし、実処理はSpring Security側へ移譲

## Keycloak導入(ローカル/CI)
### インフラ
- `deploy/kustomize/infra/base/keycloak.yaml`
  - Keycloak `Deployment` / `Service` を追加
  - `quay.io/keycloak/keycloak:26.0`, `start-dev`
- `deploy/kustomize/infra/base/keycloak-ingress.yaml`
  - `keycloak.localhost` でブラウザアクセス可能にする

### アプリ設定
- `gateway-bff`:
  - `spring.security.oauth2.client.registration.keycloak`
  - `spring.security.oauth2.client.provider.keycloak.issuer-uri=${OIDC_ISSUER}`
- Helm values:
  - local: `OIDC_ISSUER=http://keycloak.localhost/realms/miniserversystem`
  - ci: `OIDC_ISSUER=http://keycloak:8080/realms/miniserversystem`
  - 共通で `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET` を注入

### 運用手順
- `docs/task/deploy.md` に以下を追加
  - Keycloak 起動確認手順
  - Realm/Client/User の初期設定
  - `/login` -> Keycloak -> `/v1/me` 疎通確認
  - OIDC well-known / certs の切り分け確認

## テスト方針と実装
- `account`:
  - Service層の単体テスト(同定、競合時フォールバック、suspend監査ログ)
  - Repository統合テスト(Testcontainers + Postgres)
- `gateway-bff`:
  - Controllerテスト(`/login`, `/v1/me`)
  - Security統合テスト(401/302/logout時のCSRF挙動)
  - OIDC claims 正規化、認証ユーザー解決の単体テスト

## 既知のギャップ(次フェーズ)
- `gateway-bff` -> `account` の実HTTP連携が未接続(現在はスタブ)
- `account` のエンドポイント認可は未実装(`permitAll`)
- `profile` 集約はプレースホルダー(Map返却)で実データ統合は未着手
