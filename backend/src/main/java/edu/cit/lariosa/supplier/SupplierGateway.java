package edu.cit.lariosa.supplier;

// This is the ONLY public interface from the supplier module.
// It accepts application-level concepts (productId, unitsNeeded)
// and returns a SupplierOrderResult.
public interface SupplierGateway {
    SupplierOrderResult placeReorder(String productId, int unitsNeeded, Long supplierOrderId);
    SupplierOrderStatus checkOrderStatus(String poNumber);
}
