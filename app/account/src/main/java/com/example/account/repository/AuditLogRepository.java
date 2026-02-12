/*
 * どこで: app/account/src/main/java/com/example/account/repository/AuditLogRepository.java
 * 何を: audit_logs の書き込み操作を定義する
 * なぜ: 管理操作時の監査証跡を必須保存にするため
 */
package com.example.account.repository;

import com.example.account.model.AuditLogRecord;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

    /**
     * 役割:
     * - 監査ログを永続化する。
     *
     * 期待動作:
     * - suspend などの管理操作時に必ず呼び出される。
     * - 実装時は actor/target/action の欠落を DB 制約で防止する。
     */
    public void insert(AuditLogRecord auditLogRecord) {
        throw new UnsupportedOperationException("insert is not implemented yet");
    }
}
