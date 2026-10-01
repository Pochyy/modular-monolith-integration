package edu.cit.lariosa.events;

public class StockChangedEvent {
    private final String productId;
    private final int availableQuantity;

    public StockChangedEvent(String productId, int availableQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
    }

    public String getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
