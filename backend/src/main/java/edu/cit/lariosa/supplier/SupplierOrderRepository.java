package edu.cit.lariosa.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {
    List<SupplierOrder> findByStatus(SupplierOrderStatus status);
    List<SupplierOrder> findByStatusIn(List<SupplierOrderStatus> statuses);
    Optional<SupplierOrder> findByProductIdAndStatusIn(String productId, List<SupplierOrderStatus> statuses);
    Optional<SupplierOrder> findByBuyerRef(String buyerRef);
}
