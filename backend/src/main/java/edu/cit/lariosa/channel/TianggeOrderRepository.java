package edu.cit.lariosa.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface TianggeOrderRepository extends JpaRepository<TianggeOrder, String> {
    Optional<TianggeOrder> findByEventId(String eventId);
    List<TianggeOrder> findBySyncStatusInOrderByDecisionDeadlineAsc(List<String> syncStatuses);
    List<TianggeOrder> findByStatusAndSyncStatus(String status, String syncStatus);
}
