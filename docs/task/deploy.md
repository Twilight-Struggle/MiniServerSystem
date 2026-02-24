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

## Istio 導入時の前提
- sidecar はアプリ Pod のみに注入し、infra Pod（PostgreSQL/NATS/Redis/Keycloak）には注入しない
- 外部公開は Kubernetes Ingress ではなく Istio IngressGateway を利用する
- ローカル確認時の `localhost:18080` は `istio-system/istio-ingressgateway:80` への port-forward を利用する
- namespace 内通信は `PeerAuthentication STRICT` を適用する
- `AuthorizationPolicy` で以下を強制する
  - `gateway` は Istio IngressGateway からのみ許可
  - `account`/`entitlement`/`matchmaking` は `gateway` からのみ許可
  - ただし `entitlement` の `POST /v1/entitlements/grants` と `POST /v1/entitlements/revokes` は外部決済サービス想定のため IngressGateway から直接許可
  - `notification` は外部公開しない（inbound deny）
- PostgreSQL/NATS/Redis は非メッシュ依存なので `NetworkPolicy` で到達元を制限する

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
ローカルの infra overlay(`deploy/kustomize/infra/overlays/local`) は Keycloak realm import を有効化しており、以下を自動初期化する:
- Realm: `miniserversystem`
- Client: `gateway-bff` (secret: `fovHK8ZOOKXRpyMYYeShF0QQpIQbiIZe`)
- User: `test` (password: `test`)

通常は手動設定不要。カスタム値に変更したい場合のみ Keycloak 管理画面で上書きする。

### 4. アプリ起動とログイン確認
```bash
make dev
```

確認手順:
1. `http://localhost:18080/login` へアクセス
2. Keycloak ログイン画面へ遷移することを確認
3. `test / test` でログイン
4. 認証後に `GET http://localhost:18080/v1/me` が 200 でユーザー情報を返すことを確認

### 4.1 ローカルトレース確認（Jaeger）
- local overlay では `jaeger`（all-in-one）と `otel-collector` が起動する
- アプリは `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317` へ送信し、collector が `jaeger:4317` へ転送する
- Jaeger UI 確認手順:
```bash
kubectl -n miniserversystem port-forward svc/jaeger 16686:16686
```
ブラウザで `http://localhost:16686` を開く

### 4.2 ローカルメトリクス確認（Prometheus/Grafana）
- local overlay では `prometheus` と `grafana` が起動する
- Prometheus は `gateway/account/entitlement/matchmaking/notification` の `/actuator/prometheus` を scrape する
- 各アプリは `health/info/prometheus` を actuator 公開し、`application` タグと `http.server.requests` ヒストグラムを出力する
- Istio `STRICT mTLS` 下で scrape するため、`prometheus` は sidecar 注入を有効化し、`/actuator/prometheus` のみ AuthorizationPolicy で許可する
- Prometheus UI 確認手順:
```bash
kubectl -n miniserversystem port-forward svc/prometheus 9090:9090
```
ブラウザで `http://localhost:9090` を開く
- Grafana UI 確認手順:
```bash
kubectl -n miniserversystem port-forward svc/grafana 3000:3000
```
ブラウザで `http://localhost:3000` を開く（初期ログイン: `admin/admin`）

### 5. OIDC疎通確認(切り分け用)
以下が 200 で返ることを確認する:
- `http://keycloak.localhost/realms/miniserversystem/.well-known/openid-configuration`
- `http://keycloak.localhost/realms/miniserversystem/protocol/openid-connect/certs`

### CI方針
- CI環境も Google OIDC ではなく Keycloak を利用する
- `deploy/helm/miniserversystem-platform/values-ci.yaml` の `OIDC_*` は `keycloak` Service 向けURLで統一する
- CIのinfra overlay(`deploy/kustomize/infra/overlays/ci`)で Keycloak realm import を有効化し、以下を自動初期化する
  - Realm: `miniserversystem`
  - Client: `gateway-bff` (secret: `changeit`)
  - User: `test` (password: `test`)
- `.github/workflows/ci.yaml` の E2E job で `script/e2e/check-keycloak.sh` を実行し、well-known endpoint と token 発行で初期化完了を検証する
- `script/e2e/run.sh` では以下を通しで検証する
  - `script/e2e/tests/*.sh` を順次実行するオーケストレーターとして動作
  - Gateway/Keycloak の readiness
  - Entitlement 付与 API 実行後に、E2E スクリプトから Postgres に直接クエリし `notification.processed_events` 反映と `notification.notifications` 1件作成を確認する（Outbox -> NATS -> Notification 到達確認）
  - Notification 送信を意図的に失敗させ、再配送上限到達後に `notification.notification_dlq` へ隔離されること（`status=FAILED` / `attempt_count=maxAttempts`）を確認する
  - OIDC ログイン後に `GET /v1/me` で `myUserId` を取得できること
  - `GET /v1/users/{myUserId}` が `200` を返し、レスポンス `userId` が `myUserId` と一致すること
  - `GET /v1/users/{otherUserId}` が `403`で拒否されること

## Argo CD app-of-app（モノレポ構成）
### 目的
- 本リポジトリ単体で「すぐデプロイ可能」な状態を示す
- 通常の分離（アプリrepo / Argo CD repo）ではなく、ポートフォリオ用途として monorepo で完結させる

### 構成
- `deploy/argocd/project.yaml`
  - `AppProject`。対象repoとデプロイ先namespaceを明示的に制限する
- `deploy/argocd/root-app-local.yaml`
  - 親Application。`deploy/argocd/apps` を監視して子Applicationを一括管理する
- `deploy/argocd/apps/infra-local.yaml`
  - 子Application(Infra)。`deploy/kustomize/infra/overlays/local` を適用する
- `deploy/argocd/apps/platform-local.yaml`
  - 子Application(App)。`deploy/helm/miniserversystem-platform` + `values-local.yaml` を適用する

### 同期順序（重要）
- `infra-local`: `sync-wave: "0"`
- `platform-local`: `sync-wave: "1"`

infra（DB/NATS/Keycloakなど）を先に整え、その後にplatform（各アプリ）を同期する。

### 適用手順
```bash
kubectl apply -f deploy/argocd/project.yaml
kubectl apply -f deploy/argocd/root-app-local.yaml
```

Argo CD UI/CLI で `miniserversystem-root-local` を同期すると、子Applicationが連鎖的に同期される。

### 確認コマンド
```bash
kubectl -n argocd get applications
kubectl -n argocd get app miniserversystem-root-local
kubectl -n argocd get app miniserversystem-infra-local
kubectl -n argocd get app miniserversystem-platform-local
```

### 補足
- `targetRevision` は `main` 固定。PRプレビュー用途ではブランチ名に変更して運用できる
- `repoURL` は HTTPS を使用しているため、公開repoであれば追加の鍵登録なしで同期できる
