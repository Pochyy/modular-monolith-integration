package edu.cit.lariosa.shop.dto;

public class OrderResponse {
    private String status;
    private String reason;
    private InventoryDto inventory;

    public OrderResponse() {}

    public OrderResponse(String status, String reason, InventoryDto inventory) {
        this.status = status;
        this.reason = reason;
        this.inventory = inventory;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public InventoryDto getInventory() { return inventory; }
    public void setInventory(InventoryDto inventory) { this.inventory = inventory; }
}

