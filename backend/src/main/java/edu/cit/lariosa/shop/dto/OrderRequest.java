package edu.cit.lariosa.shop.dto;

import java.util.List;

public class OrderRequest {
    private List<OrderItemRequest> items;

    public OrderRequest() {}

    public List<OrderItemRequest> getItems() { return items; }
    public void setItems(List<OrderItemRequest> items) { this.items = items; }

    public static class OrderItemRequest {
        private String productId;
        private int quantity;

        public OrderItemRequest() {}

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}