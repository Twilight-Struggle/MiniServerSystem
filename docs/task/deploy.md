# deploy
## フォルダ構成
```
repo/
  services/
    gateway-bff/                # Spring Cloud Gateway 等(HTTP/JSON)
    account/                    # OIDC 連携・ユーザプロファイル
    entitlement/                # RDB 正本
    matchmaking/                # Redis + 状態管理
    notification/               # queue 購読・配信・DLQ
  libs/
    proto/                      # *.proto(単一の正本)
    common/                     # 共通(エラー型、トレース、認可 helper 等)
  deploy/
    helm/
      charts/                   # 各サービスChart等
      environments/
        local/values.yaml
        ci/values.yaml
  infra/
    postgres/                   # ローカル用(Helm values 等)
    redis/
    nats/                       # Event Bus
    keycloak/                   # ローカル IdP
　tools/
    skaffold/
  .github/workflows/
    ci.yaml
  Makefile                      # make up / make dev / make test-e2e 等
  README.md
```

## k8s環境
Rancher Desktopのk3sを利用(containerd)

## ローカル開発
1. k3dでクラスター作成(Rancher Desktop)
2. infraを Helm で導入
3. アプリは Skaffold で自動 rebuild/deploy
4. Gateway 1つのエンドポイントで動作確認

### ローカルのイメージレジストリ
k3s(Rancher Desktopの軽量k8s)

## ローカルでの疑似 CI/CD
### CI
GitHub Actions 上で kind を立てて ""k8s E2E" を回す

PR ごとに
- unit test
- container build
- kind クラスター作成
- manifests/helm を適用
- E2E(Gateway 叩いてログイン→権利付与→マッチ→通知を通しで行う)

kind クラスターを GitHub Actions で作る Actionが使える

### CD
コンテナを GHCR に push + マニフェストを更新
- CIのmain マージなどでghcr.io/<org>/<svc>:<tag> を push
- PRでdeploy/(Helm values など)の image tag を更新
- ローカル側は Argo CD 等で GitHub を監視して自動反映

## ローカルKeycloak手順
### 目的
- Google OIDC の代わりに、ローカルの Keycloak を IdP として使う
- `gateway-bff` の `/login` から Keycloak ログイン画面へ遷移できる状態を作る

### 1. インフラ起動
```bash
make infra-up
```

確認:
```bash
kubectl -n miniserversystem get pods
kubectl -n miniserversystem get svc keycloak
kubectl -n miniserversystem get ingress keycloak
```

### 2. Keycloak管理画面にアクセス
- URL: `http://keycloak.localhost/`
- 管理者ユーザー: `admin`
- 管理者パスワード: `admin`

`keycloak.localhost` が名前解決できない環境では、`/etc/hosts` に `127.0.0.1 keycloak.localhost` を追加する。

### 3. Realm / Client / User 初期設定
1. Realm を `miniserversystem` で作成する
2. Client を `gateway-bff` で作成する
3. Client 設定:
   - Client authentication: `ON`
   - Valid redirect URIs: `http://localhost:18080/callback`
   - Web origins: `http://localhost:18080`
4. Test user を1人作成し、パスワードを設定する

Client Secret は Keycloak の `Credentials` 画面で確認し、`deploy/helm/miniserversystem-platform/values-local.yaml` の `OIDC_CLIENT_SECRET` と一致させる。

### 4. アプリ起動とログイン確認
```bash
make dev
```

確認手順:
1. `http://localhost:18080/login` へアクセス
2. Keycloak ログイン画面へ遷移することを確認
3. テストユーザーでログイン
4. `callback` 後に 204 が返ることを確認

### 5. OIDC疎通確認(切り分け用)
以下が 200 で返ることを確認する:
- `http://keycloak.localhost/realms/miniserversystem/.well-known/openid-configuration`
- `http://keycloak.localhost/realms/miniserversystem/protocol/openid-connect/certs`

### CI方針
- CI環境も Google OIDC ではなく Keycloak を利用する
- `deploy/helm/miniserversystem-platform/values-ci.yaml` の `OIDC_*` は `keycloak` Service 向けURLで統一する
