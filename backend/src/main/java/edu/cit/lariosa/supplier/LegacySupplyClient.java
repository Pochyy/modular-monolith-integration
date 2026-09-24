package edu.cit.lariosa.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);
    private static final String BASE_URL = "https://legacysupply.onrender.com/api/v1";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final String clientId;
    private final String apiKey;
    private final HttpClient httpClient;

    private volatile String sessionToken;

    LegacySupplyClient(@Value("${supplier.client-id:21-0587-173}") String clientId,
                       @Value("${LS_API_KEY:}") String apiKey) {
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    // Authenticates and obtains a session token
    synchronized String authenticate() throws IOException, InterruptedException {
        String body = "<AuthRequest><ClientId>" + clientId + "</ClientId><ApiKey>" + apiKey + "</ApiKey></AuthRequest>";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/auth/token"))
                .header("Content-Type", "application/xml")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Auth failed: HTTP " + response.statusCode() + " " + response.body());
        }
        String xml = response.body();
        String token = extractXmlValue(xml, "SessionToken");
        if (token == null || token.isEmpty()) {
            throw new IOException("No session token in auth response");
        }
        this.sessionToken = token;
        log.info("LegacySupply session obtained");
        return token;
    }

    // Ensures we have a valid session, re-authenticating if needed
    private String ensureSession() throws IOException, InterruptedException {
        if (sessionToken == null) {
            return authenticate();
        }
        return sessionToken;
    }

    // Invalidates the current session (called on 401)
    void invalidateSession() {
        this.sessionToken = null;
    }

    // Creates a purchase order. Returns the raw XML response body.
    String createPurchaseOrder(String supplierSku, int qty, String buyerRef, String requestId)
            throws IOException, InterruptedException {
        String body = "<PurchaseOrder>" +
                "<SupplierSku>" + supplierSku + "</SupplierSku>" +
                "<Qty>" + qty + "</Qty>" +
                "<BuyerRef>" + buyerRef + "</BuyerRef>" +
                "</PurchaseOrder>";

        return executeWithSessionRetry("POST", "/purchase-orders", body, requestId);
    }

    // Gets a purchase order status by PO number. Returns the raw XML response body.
    String getPurchaseOrder(String poNumber) throws IOException, InterruptedException {
        return executeWithSessionRetry("GET", "/purchase-orders/" + poNumber, null, null);
    }

    // Queries purchase orders by BuyerRef
    String queryByBuyerRef(String buyerRef) throws IOException, InterruptedException {
        return executeWithSessionRetry("GET", "/purchase-orders?buyerRef=" + buyerRef, null, null);
    }

    // Executes an API call with automatic session retry on 401
    private String executeWithSessionRetry(String method, String path, String body, String requestId)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = ensureSession();
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("X-LS-Session", token)
                    .timeout(TIMEOUT);

            if (requestId != null) {
                reqBuilder.header("X-Request-Id", requestId);
            }

            if ("POST".equals(method) && body != null) {
                reqBuilder.header("Content-Type", "application/xml")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
            } else {
                reqBuilder.GET();
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 && attempt == 0) {
                log.warn("LegacySupply session expired, re-authenticating");
                invalidateSession();
                authenticate();
                continue;
            }

            if (response.statusCode() == 503) {
                throw new IOException("LegacySupply unavailable (503): " + response.body());
            }

            if (response.statusCode() == 429) {
                throw new IOException("LegacySupply rate limit (429): " + response.body());
            }

            // For POST /purchase-orders: 201 = created, 200 = idempotent replay
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }

            // 409 means idempotent replay with different content (E-IDEM-04)
            if (response.statusCode() == 409) {
                throw new IOException("Idempotency conflict (409): " + response.body());
            }

            throw new IOException("LegacySupply error HTTP " + response.statusCode() + ": " + response.body());
        }
        throw new IOException("Failed after session retry");
    }

    // Simple XML value extractor
    static String extractXmlValue(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        if (start < 0) return null;
        start += open.length();
        int end = xml.indexOf(close, start);
        if (end < 0) return null;
        return xml.substring(start, end);
    }
}
