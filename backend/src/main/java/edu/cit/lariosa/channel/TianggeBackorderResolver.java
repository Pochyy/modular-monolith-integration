package edu.cit.lariosa.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.lariosa.events.SupplierDeliveryEvent;
import edu.cit.lariosa.shop.OrderService;
import edu.cit.lariosa.shop.dto.OrderRequest;
import edu.cit.lariosa.shop.dto.OrderResponse;
import edu.cit.lariosa.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
class TianggeBackorderResolver {
    private static final Logger log = LoggerFactory.getLogger(TianggeBackorderResolver.class);
    
    private final TianggeOrderRepository orderRepo;
    private final OrderService orderService;
    private final SupplierGateway supplierGateway;
    private final TianggeClient client;
    private final ObjectMapper objectMapper;

    TianggeBackorderResolver(TianggeOrderRepository orderRepo, OrderService orderService, SupplierGateway supplierGateway, TianggeClient client, ObjectMapper objectMapper) {
        this.orderRepo = orderRepo;
        this.orderService = orderService;
        this.supplierGateway = supplierGateway;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSupplierDelivery(SupplierDeliveryEvent event) {
        resolveBackorders();
    }

    private synchronized void resolveBackorders() {
        List<TianggeOrder> backorders = orderRepo.findByStatusAndSyncStatus("BACKORDERED", "DECISION_SYNCED");
        // Sort oldest first based on placedAt
        backorders.sort((o1, o2) -> o1.getPlacedAt().compareTo(o2.getPlacedAt()));

        for (TianggeOrder tOrder : backorders) {
            OrderRequest req = new OrderRequest();
            List<OrderRequest.OrderItemRequest> items = new ArrayList<>();
            for (TianggeOrderItem tItem : tOrder.getItems()) {
                OrderRequest.OrderItemRequest ir = new OrderRequest.OrderItemRequest();
                ir.setProductId(tItem.getSellerSku());
                ir.setQuantity(tItem.getQuantity());
                items.add(ir);
            }
            req.setItems(items);

            try {
                OrderResponse res = orderService.placeOrder(req);
                if ("CONFIRMED".equals(res.getStatus())) {
                    tOrder.setStatus("ACCEPTED");
                    tOrder.setShopOrderId(String.valueOf(res.getOrderId()));
                    tOrder.setSyncStatus("PENDING_RESOLUTION");
                    orderRepo.save(tOrder);
                } else {
                    // Check if there is still a PO for the shorts
                    boolean poStillOpen = true;
                    for (TianggeOrderItem tItem : tOrder.getItems()) {
                        if (!supplierGateway.hasOpenOrder(tItem.getSellerSku())) {
                            poStillOpen = false;
                            break;
                        }
                    }
                    if (!poStillOpen) {
                        tOrder.setStatus("CANCELLED");
                        tOrder.setSyncStatus("PENDING_RESOLUTION");
                        orderRepo.save(tOrder);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to place backordered order {}: {}", tOrder.getTianggeOrderId(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void syncResolutions() {
        List<TianggeOrder> pendingResolutions = orderRepo.findByStatusAndSyncStatus("ACCEPTED", "PENDING_RESOLUTION");
        pendingResolutions.addAll(orderRepo.findByStatusAndSyncStatus("CANCELLED", "PENDING_RESOLUTION"));

        for (TianggeOrder order : pendingResolutions) {
            try {
                Map<String, String> body = new HashMap<>();
                body.put("status", order.getStatus());
                
                client.postResolution(order.getTianggeOrderId(), objectMapper.writeValueAsString(body));
                log.info("Synced resolution {} for order {}", order.getStatus(), order.getTianggeOrderId());
                order.setSyncStatus("RESOLUTION_SYNCED");
                orderRepo.save(order);
            } catch (Exception e) {
                log.error("Failed to sync resolution for {}: {}", order.getTianggeOrderId(), e.getMessage());
                if (e.getMessage().contains("409")) {
                    order.setSyncStatus("RESOLUTION_SYNCED");
                    orderRepo.save(order);
                }
            }
        }
    }
}
