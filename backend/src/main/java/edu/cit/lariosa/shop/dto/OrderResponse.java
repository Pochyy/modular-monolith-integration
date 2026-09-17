package edu.cit.lariosa.shop.dto;

import java.util.List;

public class OrderResponse {
    private Long orderId;
    private String status;
    private String reason;
    private List<OrderItemResponse> items;
    private List<InventoryDto> inventory;

    public OrderResponse() {}

    public OrderResponse(Long orderId, String status, String reason,
                         List<OrderItemResponse> items, List<InventoryDto> inventory) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.items = items;
        this.inventory = inventory;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public List<OrderItemResponse> getItems() { return items; }
    public void setItems(List<OrderItemResponse> items) { this.items = items; }

    public List<InventoryDto> getInventory() { return inventory; }
    public void setInventory(List<InventoryDto> inventory) { this.inventory = inventory; }

    public static class OrderItemResponse {
        private String productId;
        private String outcome;

        public OrderItemResponse() {}

        public OrderItemResponse(String productId, String outcome) {
            this.productId = productId;
            this.outcome = outcome;
        }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }
    }
}