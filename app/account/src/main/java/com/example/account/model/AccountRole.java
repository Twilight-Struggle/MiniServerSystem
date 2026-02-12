/*
 * どこで: app/account/src/main/java/com/example/account/model/AccountRole.java
 * 何を: Account のロールを表す列挙型
 * なぜ: 管理 API などの RBAC 判定を型安全に扱うため
 */
package com.example.account.model;

public enum AccountRole {
    USER,
    ADMIN,
    SUPPORT
}
