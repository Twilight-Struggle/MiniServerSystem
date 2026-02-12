/*
 * どこで: app/account/src/main/java/com/example/account/model/AccountStatus.java
 * 何を: Account の利用状態を表す列挙型
 * なぜ: 認可判定と運用オペレーション(停止/再開)で状態を一貫して扱うため
 */
package com.example.account.model;

public enum AccountStatus {
    ACTIVE,
    SUSPENDED
}
