package edu.cit.lariosa.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.lariosa.inventory.InventoryService;
import edu.cit.lariosa.inventory.Product;
import edu.cit.lariosa.shop.OrderService;
import edu.cit.lariosa.shop.dto.OrderRequest;
import edu.cit.lariosa.shop.dto.OrderResponse;
import edu.cit.lariosa.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
class TianggeOrderProcessor {
    private static final Logger log = LoggerFactory.getLogger(TianggeOrderProcessor.class);

    private final TianggeOrderRepository orderRepo;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final TianggeClient client;
    private final ObjectMapper objectMapper;

    TianggeOrderProcessor(TianggeOrderRepository orderRepo, OrderService orderService, InventoryService inventoryService, SupplierGateway supplierGateway, TianggeClient client, ObjectMapper objectMapper) {
        this.orderRepo = orderRepo;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processEvents(List<Map<String, Object>> events) {
        if (events != null && !events.isEmpty()) {
            log.info("Processing {} Tiangge feed events", events.size());
        }
        for (Map<String, Object> event : events) {
            String eventId = (String) event.get("eventId");
            if (eventId == null) continue;

            // Dedupe by eventId
            if (orderRepo.findByEventId(eventId).isPresent()) {
                log.info("Skipping duplicate event {}", eventId);
                continue;
            }

            String type = (String) event.get("@type");
            if ("ORDER_PLACED".equals(type)) {
                handleOrderPlaced(event, eventId);
            } else if ("ORDER_CANCELLED".equals(type)) {
                handleOrderCancelled(event, eventId);
            }
        }
    }

    private void handleOrderPlaced(Map<String, Object> event, String eventId) {
        String tianggeOrderId = (String) event.get("orderId");
        if (orderRepo.existsById(tianggeOrderId)) {
            // It might be a new seq for an existing orderId but different eventId
            log.info("Order {} already processed with another eventId", tianggeOrderId);
            return;
        }

        TianggeOrder tOrder = new TianggeOrder();
        tOrder.setTianggeOrderId(tianggeOrderId);
        tOrder.setEventId(eventId);
        tOrder.setPlacedAt(Instant.parse((String) event.get("placedAt")));
        tOrder.setDecisionDeadline(Instant.parse((String) event.get("decisionDeadline")));
        tOrder.setSyncStatus("PENDING_DECISION");

        List<Map<String, Object>> lines = (List<Map<String, Object>>) event.get("lines");
        OrderRequest orderReq = new OrderRequest();
        List<OrderRequest.OrderItemRequest> items = new ArrayList<>();
        
        boolean hasUnknownProduct = false;
        boolean canFulfillAll = true;
        boolean allShortsHavePo = true;

        for (Map<String, Object> line : lines) {
            String sellerSku = (String) line.get("sellerSku");
            int qty = (Integer) line.get("qty");
            
            TianggeOrderItem tItem = new TianggeOrderItem();
            tItem.setSellerSku(sellerSku);
            tItem.setQuantity(qty);
            tOrder.addItem(tItem);

            Product p = inventoryService.getItem(sellerSku);
            if (p == null) {
                hasUnknownProduct = true;
                canFulfillAll = false;
                allShortsHavePo = false;
            } else {
                items.add(createItemRequest(sellerSku, qty));
                if (p.getStock() < qty) {
                    canFulfillAll = false;
                    if (!supplierGateway.hasOpenOrder(sellerSku)) {
                        allShortsHavePo = false;
                    }
                }
            }
        }
        orderReq.setItems(items);

        if (hasUnknownProduct) {
            tOrder.setStatus("REJECTED");
            tOrder.setReason("Unknown sellerSku");
        } else if (canFulfillAll) {
            // Attempt actual reservation
            try {
                OrderResponse res = orderService.placeOrder(orderReq);
                if ("CONFIRMED".equals(res.getStatus())) {
                    tOrder.setStatus("ACCEPTED");
                    tOrder.setShopOrderId(String.valueOf(res.getOrderId()));
                } else {
                    // Failed reservation (concurrent or invalid)
                    if (allShortsHavePo) {
                        tOrder.setStatus("BACKORDERED");
                        tOrder.setReason("Insufficient stock but PO is open");
                    } else {
                        tOrder.setStatus("REJECTED");
                        tOrder.setReason(res.getReason());
                    }
                }
            } catch (Exception e) {
                tOrder.setStatus("REJECTED");
                tOrder.setReason("Failed to process order: " + e.getMessage());
            }
        } else if (allShortsHavePo) {
            tOrder.setStatus("BACKORDERED");
            tOrder.setReason("Insufficient stock but PO is open");
        } else {
            tOrder.setStatus("REJECTED");
            tOrder.setReason("Insufficient stock and no PO");
        }

        orderRepo.save(tOrder);
    }

    private void handleOrderCancelled(Map<String, Object> event, String eventId) {
        String tianggeOrderId = (String) event.get("orderId");
        
        // Ensure we create a placeholder if it wasn't placed, so we don't process it later
        TianggeOrder tOrder = orderRepo.findById(tianggeOrderId).orElse(null);
        if (tOrder == null) {
            tOrder = new TianggeOrder();
            tOrder.setTianggeOrderId(tianggeOrderId);
            tOrder.setEventId(eventId);
            tOrder.setStatus("CANCELLED");
            tOrder.setSyncStatus("PENDING_CANCELLATION");
            orderRepo.save(tOrder);
            return;
        }

        if (tOrder.getShopOrderId() != null && ("ACCEPTED".equals(tOrder.getStatus()) || "BACKORDERED".equals(tOrder.getStatus()))) {
            try {
                orderService.cancelOrder(Long.valueOf(tOrder.getShopOrderId()));
            } catch (Exception e) {
                log.error("Failed to cancel real order {}: {}", tOrder.getShopOrderId(), e.getMessage());
            }
        }

        tOrder.setStatus("CANCELLED");
        tOrder.setSyncStatus("PENDING_CANCELLATION");
        orderRepo.save(tOrder);
    }

    @Scheduled(fixedDelay = 2000)
    public void syncDecisions() {
        List<TianggeOrder> pendingDecisions = orderRepo.findBySyncStatusInOrderByDecisionDeadlineAsc(
                Collections.singletonList("PENDING_DECISION"));
        for (TianggeOrder order : pendingDecisions) {
            try {
                Map<String, String> body = new HashMap<>();
                body.put("decision", order.getStatus());
                if (order.getShopOrderId() != null) body.put("shopOrderId", order.getShopOrderId());
                if (order.getReason() != null) body.put("reason", order.getReason());
                
                client.postDecision(order.getTianggeOrderId(), objectMapper.writeValueAsString(body));
                log.info("Synced decision {} for order {}", order.getStatus(), order.getTianggeOrderId());
                order.setSyncStatus("DECISION_SYNCED");
                orderRepo.save(order);
            } catch (Exception e) {
                log.error("Failed to sync decision for {}: {}", order.getTianggeOrderId(), e.getMessage());
                // If 409 decision_conflict, we can reconcile or mark it synced
                if (e.getMessage() != null && e.getMessage().contains("409")) {
                    order.setSyncStatus("DECISION_SYNCED");
                    orderRepo.save(order);
                }
            }
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void syncCancellations() {
        List<TianggeOrder> pendingCancels = orderRepo.findBySyncStatusInOrderByDecisionDeadlineAsc(
                Collections.singletonList("PENDING_CANCELLATION"));
        for (TianggeOrder order : pendingCancels) {
            try {
                client.postCancellation(order.getTianggeOrderId());
                log.info("Synced cancellation for order {}", order.getTianggeOrderId());
                order.setSyncStatus("CANCELLATION_SYNCED");
                orderRepo.save(order);
            } catch (Exception e) {
                log.error("Failed to sync cancellation for {}: {}", order.getTianggeOrderId(), e.getMessage());
                if (e.getMessage().contains("409")) {
                    order.setSyncStatus("CANCELLATION_SYNCED");
                    orderRepo.save(order);
                }
            }
        }
    }

    private static OrderRequest.OrderItemRequest createItemRequest(String productId, int qty) {
        OrderRequest.OrderItemRequest item = new OrderRequest.OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(qty);
        return item;
    }
}
