package edu.cit.lariosa.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface PendingStockRepository extends JpaRepository<PendingStock, String> {
}
