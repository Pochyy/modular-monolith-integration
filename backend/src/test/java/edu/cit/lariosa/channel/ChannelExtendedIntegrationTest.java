package edu.cit.lariosa.channel;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import edu.cit.lariosa.inventory.InventoryService;
import edu.cit.lariosa.inventory.Product;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class ChannelExtendedIntegrationTest {

    static WireMockServer wireMockServer;

    @Autowired
    TianggeFeedPoller poller;

    @Autowired
    TianggeFeedCursorRepository cursorRepo;

    @Autowired
    TianggeOrderRepository orderRepo;

    @Autowired
    TianggeOrderProcessor processor;

    @BeforeAll
    static void setupClass() {
        wireMockServer = new WireMockServer(wireMockConfig().port(8090));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8090);
    }

    @AfterAll
    static void tearDownClass() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("tiangge.base-url", () -> "http://localhost:8090/tiangge/v1");
        registry.add("legacysupply.base-url", () -> "http://localhost:8090/api/v1");
        registry.add("spring.task.scheduling.pool.size", () -> "10");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:exttestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("LS_API_KEY", () -> "test-api-key");
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
        orderRepo.deleteAll();
        cursorRepo.deleteAll();
    }

    @Test
    void testCursorPersistedAndResumed() throws Exception {
        stubFor(get(urlEqualTo("/tiangge/v1/feed?limit=50"))
                .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"events\":[],\"nextCursor\":\"C123\"}")));
                
        poller.pollFeed();
        
        TianggeFeedCursor cursor = cursorRepo.findById(1).orElseThrow();
        assertEquals("C123", cursor.getCursorValue());
    }

    @Test
    void test503TimeoutHandling() throws Exception {
        // Tiangge order
        TianggeOrder order = new TianggeOrder();
        order.setTianggeOrderId("T-503");
        order.setEventId("e-503");
        order.setPlacedAt(Instant.now());
        order.setDecisionDeadline(Instant.now().plusSeconds(60));
        order.setStatus("ACCEPTED");
        order.setSyncStatus("PENDING_DECISION");
        orderRepo.save(order);

        stubFor(post(urlEqualTo("/tiangge/v1/orders/T-503/decision"))
                .willReturn(aResponse().withStatus(503)));

        processor.syncDecisions();

        // Should still be PENDING_DECISION
        TianggeOrder saved = orderRepo.findById("T-503").orElseThrow();
        assertEquals("PENDING_DECISION", saved.getSyncStatus());
    }
}
