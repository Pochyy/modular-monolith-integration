package edu.cit.lariosa.inventory;

import edu.cit.lariosa.events.LowStockEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    InventoryServiceImpl(InventoryRepository inventoryRepository,
                         ApplicationEventPublisher eventPublisher,
                         @Value("${inventory.low-stock-threshold:5}") int lowStockThreshold) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    public Product getItem(String productId) {
        return inventoryRepository.findById(productId).orElse(null);
    }

    @Override
    @Transactional
    public boolean reserve(String productId, int quantity) {
        Product product = inventoryRepository.findById(productId).orElse(null);
        if (product == null) {
            return false;
        }
        if (product.getStock() >= quantity) {
            product.setStock(product.getStock() - quantity);
            inventoryRepository.save(product);
            if (product.getStock() < lowStockThreshold && product.getStock() >= 0) {
                eventPublisher.publishEvent(
                    new LowStockEvent(product.getProductId(), product.getName(), product.getStock())
                );
            }
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        Product product = inventoryRepository.findById(productId).orElse(null);
        if (product != null) {
            product.setStock(product.getStock() + quantity);
            inventoryRepository.save(product);
        }
    }

    @Override
    public List<Product> getAllItems() {
        return inventoryRepository.findAll();
    }
}
