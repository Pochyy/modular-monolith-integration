package edu.cit.lariosa.inventory;

public interface InventoryService {
    Product getItem(String productId);
    boolean reserve(String productId, int quantity);
}

