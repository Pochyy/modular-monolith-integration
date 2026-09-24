package edu.cit.lariosa.events;

public class SupplierDeliveryEvent {
    private final String productId;
    private final int unitsDelivered;

    public SupplierDeliveryEvent(String productId, int unitsDelivered) {
        this.productId = productId;
        this.unitsDelivered = unitsDelivered;
    }

    public String getProductId() { return productId; }
    public int getUnitsDelivered() { return unitsDelivered; }
}
