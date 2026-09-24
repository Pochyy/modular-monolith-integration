package edu.cit.lariosa.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "supplier_orders")
public class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "buyer_ref", nullable = false, unique = true)
    private String buyerRef;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "po_number")
    private String poNumber;

    private int cases;
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplierOrderStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SupplierOrder() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public SupplierOrder(String productId, int cases, int units) {
        this();
        this.productId = productId;
        this.cases = cases;
        this.units = units;
        this.status = SupplierOrderStatus.PENDING;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getBuyerRef() { return buyerRef; }
    public void setBuyerRef(String buyerRef) { this.buyerRef = buyerRef; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    public int getCases() { return cases; }
    public void setCases(int cases) { this.cases = cases; }
    public int getUnits() { return units; }
    public void setUnits(int units) { this.units = units; }
    public SupplierOrderStatus getStatus() { return status; }
    public void setStatus(SupplierOrderStatus status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
