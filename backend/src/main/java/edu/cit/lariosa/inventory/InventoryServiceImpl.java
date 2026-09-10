package edu.cit.lariosa.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
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
            return true;
        }
        return false;
    }
}

