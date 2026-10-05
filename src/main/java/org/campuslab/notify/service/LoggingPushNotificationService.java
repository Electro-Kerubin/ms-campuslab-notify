package org.campuslab.notify.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoggingPushNotificationService implements PushNotificationService {
    @Override
    public void send(String recipient, String title, String body) {
        log.info("Push provider not configured; notification logged for recipient={} title={}", recipient, title);
    }
}