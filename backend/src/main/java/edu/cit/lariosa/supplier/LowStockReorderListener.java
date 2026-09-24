package edu.cit.lariosa.supplier;

import edu.cit.lariosa.events.LowStockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for LowStockEvent from the Inventory module and initiates
 * a supplier reorder through the SupplierOrderService.
 * This replaces the Lab 2 "reorder needed" log with an actual supplier call.
 */
@Component
class LowStockReorderListener {

    private static final Logger log = LoggerFactory.getLogger(LowStockReorderListener.class);
    private static final int DEFAULT_REORDER_QUANTITY = 20;

    private final SupplierOrderService supplierOrderService;

    LowStockReorderListener(SupplierOrderService supplierOrderService) {
        this.supplierOrderService = supplierOrderService;
    }

    @EventListener
    public void handleLowStock(LowStockEvent event) {
        log.info("Low stock detected for {} ({}): {} units remaining. Initiating auto-reorder.",
                event.getProductId(), event.getProductName(), event.getRemainingStock());

        int unitsToOrder = DEFAULT_REORDER_QUANTITY;
        supplierOrderService.initiateReorder(event.getProductId(), unitsToOrder);
    }
}
