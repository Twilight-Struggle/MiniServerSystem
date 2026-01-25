/*
 * Where: Notification service layer
 * What: Applies retention policy for processed_events and notifications
 * Why: Prevent unbounded growth while keeping anomalous pending records
 */
package com.example.notification.service;

import com.example.notification.config.NotificationRetentionProperties;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.ProcessedEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationRetentionService.class);

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRetentionProperties properties;
    private final Clock clock;

    public void cleanup() {
        Instant now = Instant.now(clock);
        Instant threshold = now.minus(Duration.ofDays(properties.retentionDays()));
        int staleActiveCount = notificationRepository.countStaleActive(threshold);
        if (staleActiveCount > 0) {
            logger.error("notification retention found stale active records count={} threshold={}",
                    staleActiveCount,
                    threshold);
        }
        int deletedNotifications = notificationRepository.deleteSentOrFailedOlderThan(threshold);
        int deletedProcessedEvents = processedEventRepository.deleteOlderThan(threshold);
        logger.info("notification retention cleanup deleted notifications={} processedEvents={} threshold={}",
                deletedNotifications,
                deletedProcessedEvents,
                threshold);
    }
}
