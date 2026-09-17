package edu.cit.lariosa.events;

import java.util.List;

public class OrderRejectedEvent {
    private final String reason;
    private final List<Item> items;

    public OrderRejectedEvent(String reason, List<Item> items) {
        this.reason = reason;
        this.items = items;
    }

    public String getReason() { return reason; }
    public List<Item> getItems() { return items; }

    public static class Item {
        private final String productId;
        private final int quantity;

        public Item(String productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public String getProductId() { return productId; }
        public int getQuantity() { return quantity; }
    }
}
