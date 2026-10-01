package edu.cit.lariosa.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.lariosa.events.StockChangedEvent;
import edu.cit.lariosa.inventory.InventoryService;
import edu.cit.lariosa.inventory.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
class TianggeStockPublisher {
    private static final Logger log = LoggerFactory.getLogger(TianggeStockPublisher.class);
    
    private final PendingStockRepository pendingStockRepository;
    private final TianggeClient client;
    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

    TianggeStockPublisher(PendingStockRepository pendingStockRepository, TianggeClient client, ObjectMapper objectMapper, InventoryService inventoryService) {
        this.pendingStockRepository = pendingStockRepository;
        this.client = client;
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStockChanged(StockChangedEvent event) {
        // Save the latest stock to pending, overwriting any previous pending value
        PendingStock pending = new PendingStock(event.getProductId(), event.getAvailableQuantity());
        pendingStockRepository.save(pending);
    }

    public void publishAllStock() {
        List<Product> products = inventoryService.getAllItems();
        for (Product p : products) {
            PendingStock pending = new PendingStock(p.getProductId(), p.getStock());
            pendingStockRepository.save(pending);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void pushPendingStock() {
        List<PendingStock> pendingList = pendingStockRepository.findAll();
        if (pendingList.isEmpty()) return;

        List<Map<String, Object>> payloads = new ArrayList<>();
        for (PendingStock ps : pendingList) {
            Map<String, Object> item = new HashMap<>();
            item.put("sellerSku", ps.getSellerSku());
            item.put("available", ps.getAvailableQuantity());
            payloads.add(item);
        }

        try {
            client.putStock(objectMapper.writeValueAsString(payloads));
            log.info("Successfully pushed stock to Tiangge for {} items", payloads.size());
            pendingStockRepository.deleteAll(pendingList);
        } catch (Exception e) {
            log.error("Failed to push stock to Tiangge: {}. Will retry.", e.getMessage());
        }
    }
}
