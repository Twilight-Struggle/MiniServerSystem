# MiniServerSystem
## 目的
自分が補強したいと考えられる技術を使ったWebシステムを作る

## 補強したい部分
- データ層
    - RDB、NoSQL、KVSは業務でそこまで触らない
- オブザーバビリティ
    - 実務での使用経験はあるものの、体系的理解を補強したい
- セキュリティ
    - 業務ではOIDC系はそこまで触らない

「設計→実装→運用」のような流れを模擬する。
全体像(SLO起点、トレードオフ、段階的移行、運用手順)を重視。 

## お題
**「ミニ汎用ゲームサーバー基盤：Account + Entitlement + Matchmaking + Notification」**

狙い：小さく作っても“設計→運用→改善”まで語れる題材にする。 

### 主要ユースケース
1. ログイン：外部IdP（Keycloak）でOIDCログイン → 自サービスのセッション発行(gateway-bffが担当)
2. 権利（Entitlement）付与：購入/付与イベントを受けて、ユーザに「DLC/サブスク権利」を即時反映(RDBが正)
3. マッチメイク：ユーザが キュー参加 → マッチ成立
4. 通知：マッチ成立/権利更新を通知(WebhookやWebPush等。非同期でat-least-once＋重複排除)

## 必要と考えられるコンポーネント
- Gateway-BFF(HTTP/JSON)→
    - 認証・認可、レート制限、集約、クライアント差分吸収
    - 認証認可セキュリティGateway-BFF学習用
- Account Service(OIDC連携、ユーザプロファイル)
    - IdPの認証後、システム内部主キーに紐づける
    - 認可の学習にはユーザ情報を持つ必要があるためこれを作る
- Entitlement Service(権利の正本。RDB中心)
    - お題の中心1
    - 購入トランザクションはそれだけで1つのテーマであり複雑すぎるため扱わない
    - 購入サービスから届くイベントをAPI等で受け止める
    - Notification Service活用のため、権利付与時にアウトボックスで通知
- Matchmaking Service(状態管理はRedis)
    - マッチを希望するとユーザにチケットを配布
    - キューの上位から2人マッチ
    - マッチをNotificaitonへ通知
- Notification Service(キュー購読、配信、リトライ、DLQ)
    - クライアントへの通知
    - 他のサービスからイベントを受け取って通知
- Event Bus / Queue(NATS)
- RDB + KVS

[コンポーネント詳細](docs/review/components.md)