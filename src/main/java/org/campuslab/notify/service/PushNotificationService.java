package org.campuslab.notify.service;

public interface PushNotificationService {
    void send(String recipient, String title, String body);
}