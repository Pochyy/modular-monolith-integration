package edu.cit.lariosa.shop;

import edu.cit.lariosa.events.OrderPlacedEvent;
import edu.cit.lariosa.events.OrderRejectedEvent;
import edu.cit.lariosa.inventory.InventoryService;
import edu.cit.lariosa.inventory.Product;
import edu.cit.lariosa.shop.dto.InventoryDto;
import edu.cit.lariosa.shop.dto.OrderHistoryDto;
import edu.cit.lariosa.shop.dto.OrderRequest;
import edu.cit.lariosa.shop.dto.OrderResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService,
                        OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        List<OrderRequest.OrderItemRequest> requestItems = request.getItems();

        // Aggregate quantities per product so duplicate line items can't
        // each pass validation against the same unreserved stock figure.
        Map<String, Integer> requestedQuantities = new LinkedHashMap<>();
        for (OrderRequest.OrderItemRequest item : requestItems) {
            requestedQuantities.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        String rejectionReason = null;
        List<OrderResponse.OrderItemResponse> itemOutcomes = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : requestedQuantities.entrySet()) {
            String productId = entry.getKey();
            int quantity = entry.getValue();
            Product product = inventoryService.getItem(productId);
            if (product == null) {
                rejectionReason = "Product not found: " + productId;
                itemOutcomes.add(new OrderResponse.OrderItemResponse(productId, "PRODUCT_NOT_FOUND"));
            } else if (quantity <= 0) {
                rejectionReason = "Invalid quantity for: " + productId;
                itemOutcomes.add(new OrderResponse.OrderItemResponse(productId, "INVALID_QUANTITY"));
            } else if (product.getStock() < quantity) {
                rejectionReason = "Insufficient stock for: " + productId;
                itemOutcomes.add(new OrderResponse.OrderItemResponse(productId, "INSUFFICIENT_STOCK"));
            } else {
                itemOutcomes.add(new OrderResponse.OrderItemResponse(productId, "AVAILABLE"));
            }
        }

        if (rejectionReason != null) {
            Order order = new Order("REJECTED", rejectionReason);
            for (OrderRequest.OrderItemRequest item : requestItems) {
                order.addItem(new OrderItem(item.getProductId(), item.getQuantity()));
            }
            orderRepository.save(order);

            List<InventoryDto> inventorySnapshot = buildInventorySnapshot(requestItems);

            List<OrderRejectedEvent.Item> eventItems = new ArrayList<>();
            for (OrderRequest.OrderItemRequest item : requestItems) {
                eventItems.add(new OrderRejectedEvent.Item(item.getProductId(), item.getQuantity()));
            }
            eventPublisher.publishEvent(new OrderRejectedEvent(rejectionReason, eventItems));

            return new OrderResponse(order.getOrderId(), "REJECTED", rejectionReason,
                    itemOutcomes, inventorySnapshot);
        }

        // Reserve against the aggregated quantities, and verify every
        // reservation actually succeeded — if validation and reality
        // disagree, throw so @Transactional rolls back any partial reserves.
        List<String> reserveFailures = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : requestedQuantities.entrySet()) {
            boolean ok = inventoryService.reserve(entry.getKey(), entry.getValue());
            if (!ok) {
                reserveFailures.add(entry.getKey());
            }
        }
        if (!reserveFailures.isEmpty()) {
            throw new IllegalStateException(
                    "Reservation failed after validation passed for: " + reserveFailures
                            + " — likely a duplicate product ID or concurrent order. Rolling back.");
        }

        Order order = new Order("CONFIRMED", "Order placed successfully");
        for (OrderRequest.OrderItemRequest item : requestItems) {
            order.addItem(new OrderItem(item.getProductId(), item.getQuantity()));
        }
        orderRepository.save(order);

        List<OrderResponse.OrderItemResponse> confirmedOutcomes = new ArrayList<>();
        for (OrderRequest.OrderItemRequest item : requestItems) {
            confirmedOutcomes.add(new OrderResponse.OrderItemResponse(item.getProductId(), "RESERVED"));
        }

        List<InventoryDto> inventorySnapshot = buildInventorySnapshot(requestItems);

        List<OrderPlacedEvent.Item> eventItems = new ArrayList<>();
        for (OrderRequest.OrderItemRequest item : requestItems) {
            eventItems.add(new OrderPlacedEvent.Item(item.getProductId(), item.getQuantity()));
        }
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId(), eventItems));

        return new OrderResponse(order.getOrderId(), "CONFIRMED", "Order placed successfully",
                confirmedOutcomes, inventorySnapshot);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return null;
        }
        if ("CANCELLED".equals(order.getStatus())) {
            return new OrderResponse(order.getOrderId(), "ALREADY_CANCELLED",
                    "Order is already cancelled", null, null);
        }

        if ("CONFIRMED".equals(order.getStatus())) {
            for (OrderItem item : order.getItems()) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus("CANCELLED");
        order.setReason("Order cancelled");
        orderRepository.save(order);

        List<InventoryDto> inventorySnapshot = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Product product = inventoryService.getItem(item.getProductId());
            if (product != null) {
                inventorySnapshot.add(new InventoryDto(product.getProductId(),
                        product.getName(), product.getStock()));
            }
        }

        List<OrderResponse.OrderItemResponse> itemOutcomes = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            itemOutcomes.add(new OrderResponse.OrderItemResponse(item.getProductId(), "RESTOCKED"));
        }

        return new OrderResponse(order.getOrderId(), "CANCELLED", "Order cancelled",
                itemOutcomes, inventorySnapshot);
    }

    public List<OrderHistoryDto> getAllOrders() {
        List<Order> orders = orderRepository.findAllByOrderByOrderIdDesc();
        List<OrderHistoryDto> result = new ArrayList<>();
        for (Order order : orders) {
            List<OrderHistoryDto.ItemDto> items = new ArrayList<>();
            for (OrderItem item : order.getItems()) {
                items.add(new OrderHistoryDto.ItemDto(item.getProductId(), item.getQuantity()));
            }
            result.add(new OrderHistoryDto(order.getOrderId(), order.getStatus(),
                    order.getReason(), order.getCreatedAt(), items));
        }
        return result;
    }

    private List<InventoryDto> buildInventorySnapshot(List<OrderRequest.OrderItemRequest> requestItems) {
        List<InventoryDto> snapshot = new ArrayList<>();
        for (OrderRequest.OrderItemRequest item : requestItems) {
            Product product = inventoryService.getItem(item.getProductId());
            if (product != null) {
                snapshot.add(new InventoryDto(product.getProductId(),
                        product.getName(), product.getStock()));
            }
        }
        return snapshot;
    }
}