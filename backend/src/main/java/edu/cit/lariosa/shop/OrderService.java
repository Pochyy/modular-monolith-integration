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
import java.util.List;
import java.util.stream.Collectors;

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

        // Phase 1: Validate ALL items before reserving ANY
        String rejectionReason = null;
        List<OrderResponse.OrderItemResponse> itemOutcomes = new ArrayList<>();

        for (OrderRequest.OrderItemRequest item : requestItems) {
            Product product = inventoryService.getItem(item.getProductId());
            if (product == null) {
                rejectionReason = "Product not found: " + item.getProductId();
                itemOutcomes.add(new OrderResponse.OrderItemResponse(item.getProductId(), "PRODUCT_NOT_FOUND"));
            } else if (item.getQuantity() <= 0) {
                rejectionReason = "Invalid quantity for: " + item.getProductId();
                itemOutcomes.add(new OrderResponse.OrderItemResponse(item.getProductId(), "INVALID_QUANTITY"));
            } else if (product.getStock() < item.getQuantity()) {
                rejectionReason = "Insufficient stock for: " + item.getProductId();
                itemOutcomes.add(new OrderResponse.OrderItemResponse(item.getProductId(), "INSUFFICIENT_STOCK"));
            } else {
                itemOutcomes.add(new OrderResponse.OrderItemResponse(item.getProductId(), "AVAILABLE"));
            }
        }

        // If any item failed validation, reject the ENTIRE order
        if (rejectionReason != null) {
            Order order = new Order("REJECTED", rejectionReason);
            for (OrderRequest.OrderItemRequest item : requestItems) {
                order.addItem(new OrderItem(item.getProductId(), item.getQuantity()));
            }
            orderRepository.save(order);

            // Build inventory snapshot for response
            List<InventoryDto> inventorySnapshot = buildInventorySnapshot(requestItems);

            // Publish OrderRejected event
            List<OrderRejectedEvent.Item> eventItems = requestItems.stream()
                .map(i -> new OrderRejectedEvent.Item(i.getProductId(), i.getQuantity()))
                .collect(Collectors.toList());
            eventPublisher.publishEvent(new OrderRejectedEvent(rejectionReason, eventItems));

            return new OrderResponse(order.getOrderId(), "REJECTED", rejectionReason,
                    itemOutcomes, inventorySnapshot);
        }

        // Phase 2: All items passed validation — reserve ALL
        for (OrderRequest.OrderItemRequest item : requestItems) {
            inventoryService.reserve(item.getProductId(), item.getQuantity());
        }

        // Create and save the order with items
        Order order = new Order("CONFIRMED", "Order placed successfully");
        for (OrderRequest.OrderItemRequest item : requestItems) {
            order.addItem(new OrderItem(item.getProductId(), item.getQuantity()));
        }
        orderRepository.save(order);

        // Build item outcomes as RESERVED
        List<OrderResponse.OrderItemResponse> confirmedOutcomes = requestItems.stream()
            .map(item -> new OrderResponse.OrderItemResponse(item.getProductId(), "RESERVED"))
            .collect(Collectors.toList());

        // Build inventory snapshot
        List<InventoryDto> inventorySnapshot = buildInventorySnapshot(requestItems);

        // Publish OrderPlaced event
        List<OrderPlacedEvent.Item> eventItems = requestItems.stream()
            .map(i -> new OrderPlacedEvent.Item(i.getProductId(), i.getQuantity()))
            .collect(Collectors.toList());
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId(), eventItems));

        return new OrderResponse(order.getOrderId(), "CONFIRMED", "Order placed successfully",
                confirmedOutcomes, inventorySnapshot);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return null; // Controller will return 404
        }
        if ("CANCELLED".equals(order.getStatus())) {
            return new OrderResponse(order.getOrderId(), "ALREADY_CANCELLED",
                    "Order is already cancelled", null, null);
        }

        // Only restock if order was CONFIRMED (had reserved inventory)
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

        List<OrderResponse.OrderItemResponse> itemOutcomes = order.getItems().stream()
            .map(item -> new OrderResponse.OrderItemResponse(item.getProductId(), "RESTOCKED"))
            .collect(Collectors.toList());

        return new OrderResponse(order.getOrderId(), "CANCELLED", "Order cancelled",
                itemOutcomes, inventorySnapshot);
    }

    public List<OrderHistoryDto> getAllOrders() {
        List<Order> orders = orderRepository.findAllByOrderByOrderIdDesc();
        return orders.stream().map(order -> {
            List<OrderHistoryDto.ItemDto> items = order.getItems().stream()
                .map(item -> new OrderHistoryDto.ItemDto(item.getProductId(), item.getQuantity()))
                .collect(Collectors.toList());
            return new OrderHistoryDto(order.getOrderId(), order.getStatus(),
                    order.getReason(), order.getCreatedAt(), items);
        }).collect(Collectors.toList());
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
