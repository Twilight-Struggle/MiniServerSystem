/*
 * どこで: Notification デバッグ API
 * 何を: ユーザごとの通知一覧を取得する
 * なぜ: 動作確認と開発時の可視化のため
 */
package com.example.notification.api;

import com.example.notification.model.NotificationRecord;
import com.example.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/debug/notification")
@RequiredArgsConstructor
public class NotificationDebugController {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/inbox/{userId}")
    public NotificationInboxResponse inbox(@PathVariable("userId") String userId) {
        List<NotificationSummary> items = notificationRepository.findByUserId(userId).stream()
                .map(this::toSummary)
                .toList();
        return new NotificationInboxResponse(userId, items);
    }

    private NotificationSummary toSummary(NotificationRecord record) {
        try {
            JsonNode payload = objectMapper.readTree(record.payloadJson());
            return new NotificationSummary(
                    record.notificationId(),
                    record.eventId(),
                    record.type(),
                    record.status(),
                    record.createdAt(),
                    record.sentAt(),
                    payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("notification payload parse failure", ex);
        }
    }
}
