package edu.cit.lariosa.shop.dto;

import java.time.LocalDateTime;
import java.util.List;

public class OrderHistoryDto {
    private Long orderId;
    private String status;
    private String reason;
    private LocalDateTime createdAt;
    private List<ItemDto> items;

    public OrderHistoryDto() {}

    public OrderHistoryDto(Long orderId, String status, String reason,
                           LocalDateTime createdAt, List<ItemDto> items) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
        this.items = items;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<ItemDto> getItems() { return items; }
    public void setItems(List<ItemDto> items) { this.items = items; }

    public static class ItemDto {
        private String productId;
        private int quantity;

        public ItemDto() {}

        public ItemDto(String productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
