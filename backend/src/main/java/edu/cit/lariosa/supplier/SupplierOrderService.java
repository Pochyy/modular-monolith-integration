package edu.cit.lariosa.supplier;

import edu.cit.lariosa.events.SupplierDeliveryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
class SupplierOrderService {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderService.class);

    private final SupplierOrderRepository supplierOrderRepository;
    private final SupplierGateway supplierGateway;
    private final ApplicationEventPublisher eventPublisher;

    SupplierOrderService(SupplierOrderRepository supplierOrderRepository,
                         SupplierGateway supplierGateway,
                         ApplicationEventPublisher eventPublisher) {
        this.supplierOrderRepository = supplierOrderRepository;
        this.supplierGateway = supplierGateway;
        this.eventPublisher = eventPublisher;
    }

    // Called when low stock triggers a reorder.
    // Persists a PENDING supplier order BEFORE calling the supplier,
    // with a stable BuyerRef and RequestId to prevent duplicates.
    @Transactional
    public void initiateReorder(String productId, int unitsNeeded) {
        // Check if there's already a pending/accepted/processing order for this product
        List<SupplierOrderStatus> activeStatuses = Arrays.asList(
                SupplierOrderStatus.PENDING,
                SupplierOrderStatus.ACCEPTED,
                SupplierOrderStatus.PROCESSING,
                SupplierOrderStatus.SHIPPED
        );
        if (supplierOrderRepository.findByProductIdAndStatusIn(productId, activeStatuses).isPresent()) {
            log.info("Active supplier order already exists for {}, skipping reorder", productId);
            return;
        }

        // Calculate cases needed
        ProductMapping mapping = ProductMapping.forProductId(productId);
        if (mapping == null) {
            log.warn("No supplier mapping for product {}", productId);
            return;
        }
        int cases = (int) Math.ceil((double) unitsNeeded / mapping.getPackSize());
        if (cases < 1) cases = 1;
        int actualUnits = cases * mapping.getPackSize();

        // Persist PENDING order first (with deterministic BuyerRef/RequestId)
        SupplierOrder order = new SupplierOrder(productId, cases, actualUnits);
        supplierOrderRepository.save(order);
        // Now set BuyerRef and RequestId based on the generated ID
        order.setBuyerRef("RO-" + order.getId());
        order.setRequestId("RO-REQ-" + order.getId());
        supplierOrderRepository.save(order);

        log.info("Created PENDING supplier order {} for {} ({} cases = {} units)",
                order.getId(), productId, cases, actualUnits);

        // Try to submit immediately
        submitOrder(order);
    }

    // Submits a PENDING order to LegacySupply
    private void submitOrder(SupplierOrder order) {
        SupplierOrderResult result = supplierGateway.placeReorder(
                order.getProductId(), order.getUnits(), order.getId());

        if (result.isSuccess()) {
            order.setPoNumber(result.getPoNumber());
            order.setStatus(SupplierOrderStatus.ACCEPTED);
            supplierOrderRepository.save(order);
            log.info("Supplier order {} submitted successfully: PO={}", order.getId(), result.getPoNumber());
        } else {
            // Keep as PENDING for scheduled retry
            log.warn("Supplier order {} submission failed: {}. Will retry.", order.getId(), result.getErrorMessage());
        }
    }

    // Scheduled job: retries PENDING orders every 60 seconds
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    @Transactional
    public void retryPendingOrders() {
        List<SupplierOrder> pending = supplierOrderRepository.findByStatus(SupplierOrderStatus.PENDING);
        for (SupplierOrder order : pending) {
            log.info("Retrying PENDING supplier order {}", order.getId());
            submitOrder(order);
        }
    }

    // Scheduled job: polls LegacySupply for open order status updates every 90 seconds
    @Scheduled(fixedDelay = 90000, initialDelay = 45000)
    @Transactional
    public void pollDeliveryStatus() {
        List<SupplierOrderStatus> openStatuses = Arrays.asList(
                SupplierOrderStatus.ACCEPTED,
                SupplierOrderStatus.PROCESSING,
                SupplierOrderStatus.SHIPPED
        );
        List<SupplierOrder> openOrders = supplierOrderRepository.findByStatusIn(openStatuses);

        for (SupplierOrder order : openOrders) {
            if (order.getPoNumber() == null) continue;

            SupplierOrderStatus newStatus = supplierGateway.checkOrderStatus(order.getPoNumber());

            if (newStatus == SupplierOrderStatus.UNKNOWN) {
                log.warn("Unknown status for PO {}, keeping current status {}", order.getPoNumber(), order.getStatus());
                continue;
            }

            if (newStatus != order.getStatus()) {
                log.info("Supplier order {} status: {} -> {}", order.getId(), order.getStatus(), newStatus);
                order.setStatus(newStatus);
                supplierOrderRepository.save(order);

                if (newStatus == SupplierOrderStatus.DELIVERED) {
                    log.info("Supplier order {} DELIVERED: {} units for product {}",
                            order.getId(), order.getUnits(), order.getProductId());
                    eventPublisher.publishEvent(
                            new SupplierDeliveryEvent(order.getProductId(), order.getUnits()));
                }
            }
        }
    }
}
