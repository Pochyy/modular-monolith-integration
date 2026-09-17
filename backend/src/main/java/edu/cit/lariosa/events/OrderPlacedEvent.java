package edu.cit.lariosa.events;

import java.util.List;

public class OrderPlacedEvent {
    private final Long orderId;
    private final List<Item> items;

    public OrderPlacedEvent(Long orderId, List<Item> items) {
        this.orderId = orderId;
        this.items = items;
    }

    public Long getOrderId() { return orderId; }
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
