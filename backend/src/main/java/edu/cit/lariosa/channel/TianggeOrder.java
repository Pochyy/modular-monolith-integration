package edu.cit.lariosa.channel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tiangge_orders")
class TianggeOrder {
    @Id
    @Column(name = "tiangge_order_id")
    private String tianggeOrderId;

    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;

    @Column(name = "shop_order_id")
    private String shopOrderId;

    private String status; // ACCEPTED, REJECTED, BACKORDERED, CANCELLED
    private String reason;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "decision_deadline")
    private Instant decisionDeadline;

    @Column(name = "sync_status")
    private String syncStatus; // PENDING_DECISION, DECISION_SYNCED, PENDING_RESOLUTION, RESOLUTION_SYNCED, PENDING_CANCELLATION, CANCELLATION_SYNCED

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "order")
    private List<TianggeOrderItem> items = new ArrayList<>();

    public TianggeOrder() {}

    public String getTianggeOrderId() { return tianggeOrderId; }
    public void setTianggeOrderId(String tianggeOrderId) { this.tianggeOrderId = tianggeOrderId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getShopOrderId() { return shopOrderId; }
    public void setShopOrderId(String shopOrderId) { this.shopOrderId = shopOrderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getPlacedAt() { return placedAt; }
    public void setPlacedAt(Instant placedAt) { this.placedAt = placedAt; }
    public Instant getDecisionDeadline() { return decisionDeadline; }
    public void setDecisionDeadline(Instant decisionDeadline) { this.decisionDeadline = decisionDeadline; }
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
    public List<TianggeOrderItem> getItems() { return items; }
    public void addItem(TianggeOrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }
}
