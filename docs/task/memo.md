## TODO
### “本番らしさ” を出すための最低限

Namespace 分離（例：game-local）

Readiness/Liveness を全サービスに実装

HPA はローカルでは擬似でもよい（メトリクス前提の設計・設定を置く）

PodDisruptionBudget / Resource requests/limits を置く

NetworkPolicy は（可能なら）最低限の方針だけでも置く

Observability: OpenTelemetry で trace を出す（Jaeger/Tempo をローカルに立てる）

### OIDC（外部 IdP）をローカルでどう扱うか

最短: Google OIDC をそのまま使い、localhost/Ingress にリダイレクトを通す（手軽）

成果物として強い: Keycloak をローカルに立てて “外部 IdP っぽい” 依存として扱う（障害切り分けや運用の説明がしやすい）

### CIとディレクトリ構造
Jib のキャッシュ（build/jib-cache）を CI で actions/cache する

各サービスの build.gradle.kts を共通化

### k8s
#### NetworkPolicy で境界を可視化
特定アプリ→DB のみ許可



SLO
e2e
デプロイ
メトリクス + RUNBOOK
Istio(mTLS, timeout, retry, CB, fallback)
Profile Aggregate(gateway-bff)←account + entitlement + matchmaking

### Static Analysis
SpotBugs の EI_EXPOSE_REP/EI_EXPOSE_REP2 は防御的コピーまたはコピー可能な依存の再ラップで対応する
