# 構成目標
## お題
**「ミニ汎用ゲームサーバー基盤：Account + Entitlement + Matchmaking + Notification」**

狙い：小さく作っても“設計→運用→改善”まで語れる題材にする。 

### 主要ユースケース(最小でも成立する形)
1. ログイン：外部IdP(Google等)でOIDCログイン → 自サービスのセッション発行(BFFやAPI gatewayが担当)
2. 権利(Entitlement)付与：購入/付与イベントを受けて、ユーザに「DLC/サブスク権利」を即時反映(RDBが正)
3. マッチメイク：ユーザが“キュー参加”→ マッチ成立 → セッション生成
4. 通知：マッチ成立/権利更新を通知(WebhookやWebPush等。非同期でat-least-once＋重複排除)

### 補強したいスキル
- データ層
    - RDB、NoSQL、KVSへの経験はそこまで深くない
- オブザーバビリティ
    - 概念のみの理解で、具体的運用がない
- セキュリティ
    - 実装経験がない

## 必要と考えられるコンポーネント
- BFF/API Gateway(HTTP/JSON)→
    - 認証・認可、レート制限、集約、クライアント差分吸収
    - 認証認可セキュリティBFF/API Gateway学習用
- Account Service(OIDC連携、ユーザプロファイル)
    - IdPの認証後、システム内部主キーに紐づける
    - 認可の学習にはユーザ情報を持つ必要があるためこれを作る
- Entitlement Service(権利の正本。RDB中心)
    - お題の中心1
    - 購入トランザクションはそれだけで1つのテーマであり複雑すぎるため扱わない
    - 購入サービスから届くイベントをAPI等で受け止める
    - Notification Service活用のため、権利付与時にアウトボックスで通知
- Matchmaking Service(状態管理はRedis、確定はRDB等)
    - TBD
- Notification Service(キュー購読、配信、リトライ、DLQ)
    - クライアントへの通知
    - 他のサービスからイベントを受け取って通知
- Event Bus / Queue(PubSub/SQS/Kafka/NATS)
- RDB + KVS