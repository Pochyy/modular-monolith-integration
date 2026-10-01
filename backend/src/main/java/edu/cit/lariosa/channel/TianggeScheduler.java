package edu.cit.lariosa.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.lariosa.inventory.InventoryService;
import edu.cit.lariosa.inventory.Product;
import edu.cit.lariosa.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
class TianggeScheduler {

    private static final Logger log = LoggerFactory.getLogger(TianggeScheduler.class);
    
    private final TianggeClient client;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final ObjectMapper objectMapper;
    private final TianggeStockPublisher stockPublisher;

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final String startedAt = Instant.now().toString();
    private final long startupTimeMillis = System.currentTimeMillis();

    TianggeScheduler(TianggeClient client, InventoryService inventoryService, SupplierGateway supplierGateway, ObjectMapper objectMapper, TianggeStockPublisher stockPublisher) {
        this.client = client;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.objectMapper = objectMapper;
        this.stockPublisher = stockPublisher;
    }

    private long nextHeartbeatTimeMillis = 0;

    @Scheduled(fixedDelay = 1000)
    public void heartbeat() {
        if (System.currentTimeMillis() < nextHeartbeatTimeMillis) return;

        try {
            long uptime = (System.currentTimeMillis() - startupTimeMillis) / 1000;
            HeartbeatResponse resp = client.heartbeat("edu.cit.lariosa.shop-app", startedAt, uptime);
            int delaySeconds = (resp != null && resp.nextHeartbeatSeconds > 0) ? resp.nextHeartbeatSeconds : 30;
            this.nextHeartbeatTimeMillis = System.currentTimeMillis() + (delaySeconds * 1000L);
            log.info("Heartbeat successful. Next in {}s", delaySeconds);

            if (registered.compareAndSet(false, true)) {
                publishInitialListingsAndStock();
            }
        } catch (Exception e) {
            log.error("Heartbeat failed: {}", e.getMessage());
            this.nextHeartbeatTimeMillis = System.currentTimeMillis() + 10000; // Retry in 10s on error
        }
    }

    private void publishInitialListingsAndStock() {
        new Thread(() -> {
            boolean listingsPublished = false;
            while (!listingsPublished) {
                try {
                    List<Product> products = inventoryService.getAllItems();
                    List<Map<String, String>> listings = new ArrayList<>();
                    for (Product p : products) {
                        String supplierSku = supplierGateway.getSupplierReference(p.getProductId());
                        if (supplierSku != null) {
                            Map<String, String> item = new HashMap<>();
                            item.put("sellerSku", p.getProductId());
                            item.put("title", p.getName());
                            item.put("supplierSku", supplierSku);
                            listings.add(item);
                        }
                    }
                    if (!listings.isEmpty()) {
                        client.putListings(objectMapper.writeValueAsString(listings));
                        log.info("Published {} listings to Tiangge", listings.size());
                        stockPublisher.publishAllStock();
                        listingsPublished = true;
                    } else {
                        log.warn("No products with supplier mappings found to list.");
                        break;
                    }
                } catch (Exception e) {
                    log.error("Failed to publish initial listings, retrying in 10s: {}", e.getMessage());
                    try { Thread.sleep(10000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }).start();
    }
}
