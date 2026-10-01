package edu.cit.lariosa.channel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tiangge_pending_stock")
class PendingStock {
    @Id
    private String sellerSku;
    private int availableQuantity;
    private Instant lastUpdated;

    public PendingStock() {}
    public PendingStock(String sellerSku, int availableQuantity) {
        this.sellerSku = sellerSku;
        this.availableQuantity = availableQuantity;
        this.lastUpdated = Instant.now();
    }

    public String getSellerSku() { return sellerSku; }
    public void setSellerSku(String sellerSku) { this.sellerSku = sellerSku; }
    public int getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}
