package edu.cit.lariosa.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.lariosa.config.InstanceIdentity;
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
class TianggeClient {

    private static final Logger log = LoggerFactory.getLogger(TianggeClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final String instanceId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    TianggeClient(@Value("${tiangge.base-url:https://legacysupply.onrender.com/tiangge/v1}") String baseUrl,
                  @Value("${supplier.client-id:21-0587-173}") String clientId,
                  @Value("${LS_API_KEY:}") String apiKey,
                  InstanceIdentity instanceIdentity,
                  ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instanceId = instanceIdentity.getId();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("X-Client-Id", clientId)
                .header("X-Client-Instance", instanceId)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .timeout(TIMEOUT);
    }

    public HeartbeatResponse heartbeat(String appName, String startedAt, long uptimeSeconds) throws IOException, InterruptedException {
        String body = String.format("{\"appName\":\"%s\",\"startedAt\":\"%s\",\"uptimeSeconds\":%d}", appName, startedAt, uptimeSeconds);
        HttpRequest req = requestBuilder("/instances/heartbeat")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) throw new IOException("Heartbeat failed: " + res.statusCode() + " " + res.body());
        return objectMapper.readValue(res.body(), HeartbeatResponse.class);
    }

    public void putListings(String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = requestBuilder("/listings")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) throw new IOException("Listings failed: " + res.statusCode() + " " + res.body());
    }

    public void putStock(String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = requestBuilder("/stock")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) throw new IOException("Stock failed: " + res.statusCode() + " " + res.body());
    }

    public FeedResponse getFeed(String cursor, int limit) throws IOException, InterruptedException {
        String url = "/feed?limit=" + limit;
        if (cursor != null) url += "&after=" + cursor;
        HttpRequest req = requestBuilder(url).GET().build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) throw new IOException("Feed failed: " + res.statusCode() + " " + res.body());
        return objectMapper.readValue(res.body(), FeedResponse.class);
    }

    public void postDecision(String orderId, String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = requestBuilder("/orders/" + orderId + "/decision")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300 && res.statusCode() != 409) {
            throw new IOException("Decision failed: " + res.statusCode() + " " + res.body());
        }
    }

    public void postResolution(String orderId, String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = requestBuilder("/orders/" + orderId + "/resolution")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300 && res.statusCode() != 409) {
            throw new IOException("Resolution failed: " + res.statusCode() + " " + res.body());
        }
    }

    public void postCancellation(String orderId) throws IOException, InterruptedException {
        HttpRequest req = requestBuilder("/orders/" + orderId + "/cancellation")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"restocked\":true}"))
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300 && res.statusCode() != 409) {
            throw new IOException("Cancellation failed: " + res.statusCode() + " " + res.body());
        }
    }
}
