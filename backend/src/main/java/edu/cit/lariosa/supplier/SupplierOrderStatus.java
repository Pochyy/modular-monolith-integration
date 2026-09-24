package edu.cit.lariosa.supplier;

// Application-level supplier order status enum.
// Does NOT expose LegacySupply status codes.
public enum SupplierOrderStatus {
    PENDING,      // Not yet submitted or submission failed
    ACCEPTED,     // Supplier accepted (LS code 10)
    PROCESSING,   // Picking/in-progress (LS code 20)
    SHIPPED,      // Shipped (LS code 30)
    DELIVERED,    // Delivered (LS code 40)
    UNKNOWN       // Unexpected/unrecognized status
}
