package edu.cit.lariosa.notification;

import edu.cit.lariosa.events.LowStockEvent;
import edu.cit.lariosa.events.OrderPlacedEvent;
import edu.cit.lariosa.events.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    public void handleOrderPlaced(OrderPlacedEvent event) {
        String items = event.getItems().stream()
            .map(i -> i.getProductId() + " x" + i.getQuantity())
            .collect(Collectors.joining(", "));
        String message = "Order #" + event.getOrderId() + " CONFIRMED: " + items;
        notificationRepository.save(new Notification(message));
    }

    @EventListener
    public void handleOrderRejected(OrderRejectedEvent event) {
        String items = event.getItems().stream()
            .map(i -> i.getProductId() + " x" + i.getQuantity())
            .collect(Collectors.joining(", "));
        String message = "Order REJECTED (" + event.getReason() + "): " + items;
        notificationRepository.save(new Notification(message));
    }

    @EventListener
    public void handleLowStock(LowStockEvent event) {
        String message = "Low stock: " + event.getProductId() + " (" + event.getProductName()
                + ") has " + event.getRemainingStock() + " units remaining. Reorder needed.";
        notificationRepository.save(new Notification(message));
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }
}
