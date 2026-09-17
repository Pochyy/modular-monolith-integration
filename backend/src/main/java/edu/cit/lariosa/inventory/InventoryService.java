package edu.cit.lariosa.inventory;

import java.util.List;

public interface InventoryService {
    Product getItem(String productId);
    boolean reserve(String productId, int quantity);
    void restock(String productId, int quantity);
    List<Product> getAllItems();
}
