package edu.cit.lariosa.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
class HeartbeatResponse {
    public String serverTime;
    public int nextHeartbeatSeconds;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class FeedResponse {
    public List<Map<String, Object>> events;
    public String nextCursor;
}
