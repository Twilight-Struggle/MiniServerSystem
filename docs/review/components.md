# components
## gateway-bff
- 認証・認可、レート制限、集約、クライアント差分吸収
- 認証認可セキュリティ

### 実装内容
- /loginから Spring security で認証、keycloakへリダイレクト
- /v1/me で account app で認証情報を登録、取得
- matchmaking へのアクセス認証後経由
- account, entitlement, matchmakingの情報を一度に取得するエンドポイント作成

## account
- IdPの認証後、システム内部主キーに紐づける

### 実装内容
- gateway-bffからのアクセスでuserを登録し、identity, roleと紐づけ
- ユーザ情報を返すAPI

## Entitlement
- 購入サービスから届くイベントをAPI等で受け止める
- Notification Service活用のため、権利付与時にアウトボックスで通知

### 実装内容
- grant/revoke APIでRDB(正本)と outbox に保存
- outboxからワーカーが作業中であるとclaimしNATSへ送信(時間切れでlease)
- 一定期間でoutboxをクリーンアップ

## Notification
- クライアントへの通知
- 他のサービスからイベントを受け取って通知

### 実装内容
- NATS からイベントをサブスクライブし、RDBへ
- RDBを作業中だとclaimしクライアントへ送信(ただのログ)
- RDBを一定期間でクリーンアップ

## Matchmaking
- マッチを希望するとユーザにチケットを配布
- キューの上位から2人マッチ
- マッチをNotificaitonへ通知

### 実装内容
- APIでユーザにチケットを発行し、Redisへ保存
- チケットをキューにinsert
- 定期的にキューから上位2人をマッチ(Lua scriptで原子的に)
- マッチしたらNotificationへ通知