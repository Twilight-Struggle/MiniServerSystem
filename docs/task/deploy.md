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

