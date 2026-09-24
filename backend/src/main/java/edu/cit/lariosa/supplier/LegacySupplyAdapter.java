package edu.cit.lariosa.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
class LegacySupplyAdapter implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyAdapter.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final LegacySupplyClient client;

    LegacySupplyAdapter(LegacySupplyClient client) {
        this.client = client;
    }

    @Override
    public SupplierOrderResult placeReorder(String productId, int unitsNeeded, Long supplierOrderId) {
        ProductMapping mapping = ProductMapping.forProductId(productId);
        if (mapping == null) {
            return SupplierOrderResult.failure("No supplier mapping for product: " + productId);
        }

        // Convert units to cases (round up)
        int cases = (int) Math.ceil((double) unitsNeeded / mapping.getPackSize());
        if (cases < 1) cases = 1;
        if (cases > 99) cases = 99;

        int actualUnits = cases * mapping.getPackSize();
        String buyerRef = "RO-" + supplierOrderId;
        String requestId = "RO-REQ-" + supplierOrderId;

        log.info("Placing reorder for {} -> {} x{} cases (={} units), BuyerRef={}, RequestId={}",
                productId, mapping.getSupplierSku(), cases, actualUnits, buyerRef, requestId);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String responseXml = client.createPurchaseOrder(
                        mapping.getSupplierSku(), cases, buyerRef, requestId);

                String poNumber = LegacySupplyClient.extractXmlValue(responseXml, "PoNumber");
                if (poNumber != null) {
                    log.info("PO created: {} for {} ({} cases = {} units)", poNumber, productId, cases, actualUnits);
                    return SupplierOrderResult.success(poNumber, cases, actualUnits);
                } else {
                    return SupplierOrderResult.failure("No PoNumber in response: " + responseXml);
                }
            } catch (IOException e) {
                log.warn("Attempt {}/{} failed for {}: {}", attempt, MAX_RETRIES, productId, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return SupplierOrderResult.failure("Interrupted during retry");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return SupplierOrderResult.failure("Interrupted");
            }
        }

        return SupplierOrderResult.failure("All " + MAX_RETRIES + " attempts failed for " + productId);
    }

    @Override
    public SupplierOrderStatus checkOrderStatus(String poNumber) {
        try {
            String responseXml = client.getPurchaseOrder(poNumber);
            String statusCode = LegacySupplyClient.extractXmlValue(responseXml, "StatusCode");
            return translateStatus(statusCode);
        } catch (Exception e) {
            log.warn("Failed to check status for {}: {}", poNumber, e.getMessage());
            return SupplierOrderStatus.UNKNOWN;
        }
    }

    // Translates LegacySupply status codes to application-level enum.
    // LegacySupply codes are isolated here and never leak outside this module.
    private SupplierOrderStatus translateStatus(String statusCode) {
        if (statusCode == null) return SupplierOrderStatus.UNKNOWN;
        switch (statusCode) {
            case "10": return SupplierOrderStatus.ACCEPTED;
            case "20": return SupplierOrderStatus.PROCESSING;
            case "30": return SupplierOrderStatus.SHIPPED;
            case "40": return SupplierOrderStatus.DELIVERED;
            default:
                log.warn("Unknown LegacySupply status code: {}", statusCode);
                return SupplierOrderStatus.UNKNOWN;
        }
    }
}
