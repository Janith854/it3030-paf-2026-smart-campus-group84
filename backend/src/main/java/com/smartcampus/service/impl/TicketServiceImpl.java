package com.smartcampus.service.impl;

import com.smartcampus.exception.AccessDeniedException;
import com.smartcampus.exception.ResourceNotFoundException;
import com.smartcampus.model.Notification;
import com.smartcampus.model.Ticket;
import com.smartcampus.repository.TicketRepository;
import com.smartcampus.service.NotificationService;
import com.smartcampus.service.TicketService;
import com.smartcampus.util.FileStorageService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.smartcampus.model.User;
import com.smartcampus.repository.UserRepository;

/**
 * Module C — Maintenance & Incident Ticketing
 * Member 3: feature/tickets
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    @Override
    public Ticket createTicket(Ticket ticket, String userId, List<MultipartFile> images) {
        ticket.setReportedByUserId(userId);
        ticket.setStatus(Ticket.TicketStatus.OPEN);

        if (images != null && !images.isEmpty()) {
            if (images.size() > 3) {
                throw new RuntimeException("Maximum 3 images allowed");
            }
            List<String> fileNames = new ArrayList<>();
            for (MultipartFile image : images) {
                try {
                    fileNames.add(fileStorageService.saveFile(image));
                } catch (java.io.IOException ex) {
                    throw new RuntimeException("Failed to store image", ex);
                }
            }
            ticket.setImageAttachments(fileNames);
        }

        Ticket saved = ticketRepository.save(ticket);

        List<User> admins = userRepository.findByRole(User.Role.ADMIN);
        for (User admin : admins) {
            notificationService.createNotification(
                admin.getId(),
                "New Incident Ticket",
                "A new ticket categorized as " + saved.getCategory() + " has been raised.",
                Notification.NotificationType.TICKET_CREATED,
                saved.getId()
            );
        }

        return saved;
    }

    @Override
    public Ticket getTicketById(String id, com.smartcampus.security.UserPrincipal user) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));

        // Security check: Only the reporter, an Admin, or a Technician can view the ticket details
        boolean isAdmin = user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isTech = user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_TECHNICIAN"));
        boolean isOwner = ticket.getReportedByUserId() != null && ticket.getReportedByUserId().equals(user.getId());

        if (!isAdmin && !isTech && !isOwner) {
            throw new AccessDeniedException("You are not authorized to view this ticket");
        }

        return ticket;
    }

    @Override
    public List<Ticket> getMyTickets(String userId) {
        return ticketRepository.findByReportedByUserId(userId);
    }

    @Override
    public List<Ticket> getAllTickets(String status, String priority) {
        if (status != null && priority != null) {
            return ticketRepository.findByStatusAndPriority(
                Ticket.TicketStatus.valueOf(status), Ticket.Priority.valueOf(priority));
        }
        if (status != null) {
            return ticketRepository.findByStatus(Ticket.TicketStatus.valueOf(status));
        }
        if (priority != null) {
            return ticketRepository.findByPriority(Ticket.Priority.valueOf(priority));
        }
        return ticketRepository.findAll();
    }

    @Override
    public Ticket updateTicketStatus(String id, Ticket.TicketStatus status, String notes) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));

        // Security check: only assigned technician or admin can update
        // Note: For a real production app, we'd check against the SecurityContext principal here.
        
        ticket.setStatus(status);
        if (notes != null) {
            ticket.setResolutionNotes(notes);
        }
        Ticket saved = ticketRepository.save(ticket);

        notificationService.createNotification(
            saved.getReportedByUserId(),
            "Ticket Status Updated",
            "Your ticket '" + saved.getCategory() + "' is now " + status,
            Notification.NotificationType.TICKET_STATUS_CHANGED,
            saved.getId()
        );
        return saved;
    }

    @Override
    public Ticket assignTechnician(String id, String technicianId) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));

        ticket.setAssignedTechnicianId(technicianId);
        ticket.setStatus(Ticket.TicketStatus.IN_PROGRESS);
        Ticket saved = ticketRepository.save(ticket);

        notificationService.createNotification(
            saved.getReportedByUserId(),
            "Technician Assigned",
            "A technician has been assigned to your ticket: " + saved.getCategory(),
            Notification.NotificationType.TICKET_ASSIGNED,
            saved.getId()
        );

        // Notify Technician
        log.debug("Assigning Technician: {} to Ticket: {}", technicianId, saved.getId());
        boolean isUrgent = saved.getPriority() != null && 
                          (saved.getPriority() == Ticket.Priority.HIGH || saved.getPriority() == Ticket.Priority.CRITICAL);
        
        log.debug("Priority is urgent: {}", isUrgent);
        
        notificationService.createNotification(
            technicianId,
            isUrgent ? "URGENT Maintenance Assigned" : "New Ticket Assigned",
            isUrgent ? "Critical priority ticket requires immediate attention: " + saved.getCategory()
                     : "You have been assigned a new ticket: " + saved.getCategory(),
            isUrgent ? Notification.NotificationType.URGENT_PRIORITY_ALERT : Notification.NotificationType.TICKET_ASSIGNED,
            saved.getId()
        );
        log.debug("Technician notification created successfully");
        return saved;
    }

    @Override
    public Ticket addComment(String id, String userId, String content) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));

        if (ticket.getComments() == null) {
            ticket.setComments(new ArrayList<>());
        }

        Ticket.Comment comment = new Ticket.Comment();
        comment.setId(UUID.randomUUID().toString());
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now());
        ticket.getComments().add(comment);
        Ticket saved = ticketRepository.save(ticket);

        if (saved.getReportedByUserId() != null && !saved.getReportedByUserId().equals(userId)) {
            notificationService.createNotification(
                saved.getReportedByUserId(),
                "New Comment on Your Ticket",
                "Someone added a comment on: " + saved.getCategory(),
                Notification.NotificationType.TICKET_COMMENT_ADDED,
                saved.getId()
            );
        }

        // Notify Technician if user comments
        if (saved.getAssignedTechnicianId() != null && !saved.getAssignedTechnicianId().equals(userId)) {
            notificationService.createNotification(
                saved.getAssignedTechnicianId(),
                "New User Comment",
                "User added a new comment to your assigned ticket: " + saved.getCategory(),
                Notification.NotificationType.TICKET_COMMENT_ADDED,
                saved.getId()
            );
        }
        return saved;
    }

    @Override
    public Ticket updateComment(String ticketId, String commentId, String userId, String content) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        if (ticket.getComments() == null) {
            throw new ResourceNotFoundException("Comment not found: " + commentId);
        }

        Ticket.Comment comment = ticket.getComments().stream()
            .filter(c -> c.getId().equals(commentId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        if (!comment.getUserId().equals(userId)) {
            throw new AccessDeniedException("You can only edit your own comments");
        }

        comment.setContent(content);
        return ticketRepository.save(ticket);
    }

    @Override
    public Ticket deleteComment(String ticketId, String commentId, String userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        if (ticket.getComments() == null) {
            throw new ResourceNotFoundException("Comment not found: " + commentId);
        }

        Ticket.Comment comment = ticket.getComments().stream()
            .filter(c -> c.getId().equals(commentId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        if (!comment.getUserId().equals(userId)) {
            throw new AccessDeniedException("You can only delete your own comments");
        }

        ticket.getComments().removeIf(c -> c.getId().equals(commentId));
        return ticketRepository.save(ticket);
    }
}
