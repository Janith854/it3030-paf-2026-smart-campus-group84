package com.smartcampus.service.impl;

import com.smartcampus.exception.AccessDeniedException;
import com.smartcampus.exception.ResourceNotFoundException;
import com.smartcampus.model.Notification;
import com.smartcampus.repository.NotificationRepository;
import com.smartcampus.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Module D — Notifications
 * Member 4: feature/auth
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final com.smartcampus.repository.UserRepository userRepository;

    @Override
    public Notification createNotification(String userId, String title, String message,
                                           Notification.NotificationType type, String referenceId) {
        
        // Preference Check
        com.smartcampus.model.User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            com.smartcampus.model.User.NotificationPreferences prefs = user.getNotificationPreferences();
            boolean shouldSend = true;
            
            switch (type) {
                case BOOKING_APPROVED: shouldSend = prefs.isBookingApproved(); break;
                case BOOKING_REJECTED: shouldSend = prefs.isBookingRejected(); break;
                case TICKET_STATUS_CHANGED: shouldSend = prefs.isTicketStatusChanged(); break;
                case TICKET_COMMENT_ADDED: shouldSend = prefs.isNewCommentOnTicket(); break;
                case TICKET_ASSIGNED: shouldSend = prefs.isTechnicianAssigned(); break;
                case GENERAL: 
                case URGENT_PRIORITY_ALERT: shouldSend = prefs.isGeneralAlerts(); break;
            }
            
            if (!shouldSend) {
                log.debug("Notification skipped due to user preference: {}", type);
                return null;
            }
        }

        log.debug("Creating notification for user: {} - Title: {}", userId, title);
        Notification n = new Notification();
        n.setUserId(userId);
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type);
        n.setReferenceId(referenceId);
        n.setCreatedAt(java.time.LocalDateTime.now());
        Notification saved = notificationRepository.save(n);
        log.debug("Notification saved with ID: {}", saved.getId());
        return saved;
    }

    @Override
    public List<Notification> getMyNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndRead(userId, false);
    }

    @Override
    public Notification markAsRead(String id, String userId) {
        Notification n = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        if (!n.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Notification not found: " + id);
        }
        n.setRead(true);
        return notificationRepository.save(n);
    }

    @Override
    public void markAllAsRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndRead(userId, false);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    @Override
    public void deleteNotification(String id, String userId) {
        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        if (!notification.getUserId().equals(userId)) {
            throw new AccessDeniedException("You can only delete your own notifications");
        }
        notificationRepository.delete(notification);
    }
}
