package edu.cit.lariosa.supplier;

// Package-private. Maps application product IDs to LegacySupply SKUs.
class ProductMapping {
    private final String supplierSku;
    private final int packSize;

    ProductMapping(String supplierSku, int packSize) {
        this.supplierSku = supplierSku;
        this.packSize = packSize;
    }

    String getSupplierSku() { return supplierSku; }
    int getPackSize() { return packSize; }

    static ProductMapping forProductId(String productId) {
        switch (productId) {
            case "P100": return new ProductMapping("NJL-7284", 24); // Wireless Mouse
            case "P200": return new ProductMapping("NJL-2407", 6);  // Mechanical Keyboard
            case "P300": return new ProductMapping("NJL-4240", 24); // USB-C Hub
            default: return null;
        }
    }
}
