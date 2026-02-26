# TBD
## gateway-bff
- /meで毎回reolveを行うため、負荷が高い
    - セッション、イベントによるキャッシュ更新等で毎回の認証を抑える
- アプリの前段にあるため、timeout, retry, circuit breakerが重要となるが、ここはアプリ的には実装していない　？
    - resilience4j等で依存サービスが落ちた場合のCBやretryを入れる

## account
- 内部トークン認証とmTLSや/identities:resolveをingress遮断することなどでセキュリティとしているが、Authenticationは漏洩リスクが高い
    - JWTなどでガード

## Entitlement
- outboxがfailした時、Notificationと違いDLQに送っていない
    - DLQ等へ移して原因調査

## Notification
- Notificationはat-least-onceなので2重送信抑制には冪等キー(notification_id)などが必要
- @Transactionalはメソッド終了時に走るため、tryで捕まらない場合がまれにある
```
      eventHandler.handleEntitlementEvent(event);　←DB操作
      message.ack();
```
    - 以下のようなコードでコミットしたらackを強制
```
  TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
      @Override public void afterCommit() { msg.ack(); }
    }
  );
```

## Matchmaking
- idempotencyKeyが一致していても、attributes(マッチに使うデータ)が異なる場合壊れる
    - マッチ条件でハッシュ化して一致を確認
- Cancelした瞬間ワーカーがマッチしようとすると競合する
    - LuaスクリプトでCancelはstatus==QUEUEDのときのみにする等
- ワーカーの多重起動により、負荷が上がる
    - matchTwoがemptyならbackoffするなどの工夫が必要
- expires_at_millis=nilなど壊れたデータが来るとキューからは消えるがチケットは残る→チケットはあるのにマッチしない
    - 異常データはstatus=INVALID等にしてメトリクス化

## インフラ
- スケーラビリティや、PodDisruptionBudget / Resource requests/limits等のインフラリソース等には触れていない
- 負荷、障害注入テストはできていない
