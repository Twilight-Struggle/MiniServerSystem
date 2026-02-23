## TODO
### “本番らしさ” を出すための最低限

Namespace 分離（例：game-local）

Readiness/Liveness を全サービスに実装

HPA はローカルでは擬似でもよい（メトリクス前提の設計・設定を置く）

PodDisruptionBudget / Resource requests/limits を置く

NetworkPolicy は（可能なら）最低限の方針だけでも置く

Observability: OpenTelemetry で trace を出す（Jaeger/Tempo をローカルに立てる）

### k8s
#### NetworkPolicy で境界を可視化
特定アプリ→DB のみ許可

Istio(mTLS, timeout, retry, CB)
ログ、メトリクス、トレース
デプロイ
Profile Aggregate(gateway-bff)←account + entitlement + matchmaking

### Static Analysis
SpotBugs の EI_EXPOSE_REP/EI_EXPOSE_REP2 は防御的コピーまたはコピー可能な依存の再ラップで対応する
