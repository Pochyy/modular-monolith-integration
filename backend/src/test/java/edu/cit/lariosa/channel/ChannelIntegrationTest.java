package edu.cit.lariosa.channel;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import edu.cit.lariosa.inventory.InventoryService;
import edu.cit.lariosa.shop.OrderService;
import edu.cit.lariosa.supplier.SupplierGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class ChannelIntegrationTest {

    static WireMockServer wireMockServer;

    @Autowired
    TianggeScheduler scheduler;

    @Autowired
    TianggeFeedPoller poller;

    @Autowired
    TianggeOrderRepository orderRepo;

    @BeforeAll
    static void setupClass() {
        wireMockServer = new WireMockServer(wireMockConfig().port(8089));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterAll
    static void tearDownClass() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("tiangge.base-url", () -> "http://localhost:8089/tiangge/v1");
        registry.add("legacysupply.base-url", () -> "http://localhost:8089/api/v1");
        registry.add("spring.task.scheduling.pool.size", () -> "10");
        // Use H2 in-memory for testing
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("LS_API_KEY", () -> "test-api-key");
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
        orderRepo.deleteAll();
    }

    @Autowired
    TianggeClient client;

    @Test
    void testHeartbeatAndHeaders() throws Exception {
        stubFor(post(urlEqualTo("/tiangge/v1/instances/heartbeat"))
                .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"serverTime\":\"2023-01-01T00:00:00Z\",\"nextHeartbeatSeconds\":30}")));
        
        client.heartbeat("app", "2024-01-01T00:00:00Z", 100);
        
        verify(postRequestedFor(urlEqualTo("/tiangge/v1/instances/heartbeat"))
                .withHeader("X-Client-Id", matching(".+"))
                .withHeader("X-Client-Instance", matching(".+"))
                .withHeader("Authorization", matching("Bearer .*")));
    }

    @Autowired
    TianggeOrderProcessor processor;

    @Test
    void testUnknownSellerSkuRejected() throws Exception {
        // Mock the Tiangge API for decision
        stubFor(post(urlPathMatching("/tiangge/v1/orders/.*"))
                .willReturn(aResponse().withStatus(200)));

        String eventId = java.util.UUID.randomUUID().toString();
        List<Map<String, Object>> events = List.of(
            Map.of("@type", "ORDER_PLACED",
                   "eventId", eventId,
                   "orderId", "T-123",
                   "placedAt", "2024-01-01T10:00:00Z",
                   "decisionDeadline", "2024-01-01T10:01:00Z",
                   "lines", List.of(Map.of("sellerSku", "UNKNOWN-999", "qty", 1)))
        );

        processor.processEvents(events);

        TianggeOrder order = orderRepo.findById("T-123").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("REJECTED", order.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("Unknown sellerSku", order.getReason());
    }

    @Test
    void testOrderDeduplication() throws Exception {
        String eventId = java.util.UUID.randomUUID().toString();
        List<Map<String, Object>> events = List.of(
            Map.of("@type", "ORDER_PLACED",
                   "eventId", eventId,
                   "orderId", "T-456",
                   "placedAt", "2024-01-01T10:00:00Z",
                   "decisionDeadline", "2024-01-01T10:01:00Z",
                   "lines", List.of(Map.of("sellerSku", "UNKNOWN-999", "qty", 1)))
        );

        processor.processEvents(events);
        
        // Second time with same eventId
        processor.processEvents(events);

        long count = orderRepo.count();
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }
    
    @Test
    void testDecisionConflictReconciliation() throws Exception {
        // Prepare an order in PENDING_DECISION state
        TianggeOrder order = new TianggeOrder();
        order.setTianggeOrderId("T-CONFLICT");
        order.setEventId("event-conflict");
        order.setPlacedAt(java.time.Instant.now());
        order.setDecisionDeadline(java.time.Instant.now().plusSeconds(60));
        order.setStatus("ACCEPTED");
        order.setSyncStatus("PENDING_DECISION");
        orderRepo.save(order);

        // Stub the decision API to return 409
        stubFor(post(urlEqualTo("/tiangge/v1/orders/T-CONFLICT/decision"))
                .willReturn(aResponse().withStatus(409)));

        // Run sync
        processor.syncDecisions();

        // Verify it was marked synced despite the 409
        TianggeOrder saved = orderRepo.findById("T-CONFLICT").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("DECISION_SYNCED", saved.getSyncStatus());
    }
}
