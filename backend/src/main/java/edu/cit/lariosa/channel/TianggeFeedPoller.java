package edu.cit.lariosa.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class TianggeFeedPoller {
    private static final Logger log = LoggerFactory.getLogger(TianggeFeedPoller.class);
    
    private final TianggeClient client;
    private final TianggeFeedCursorRepository cursorRepo;
    private final TianggeOrderProcessor processor;

    TianggeFeedPoller(TianggeClient client, TianggeFeedCursorRepository cursorRepo, TianggeOrderProcessor processor) {
        this.client = client;
        this.cursorRepo = cursorRepo;
        this.processor = processor;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollFeed() {
        try {
            boolean hasMore = true;
            while (hasMore) {
                String currentCursor = cursorRepo.findById(1)
                        .map(TianggeFeedCursor::getCursorValue)
                        .orElse(null);

                FeedResponse resp = client.getFeed(currentCursor, 50);
                if (resp == null) {
                    hasMore = false;
                } else {
                    if (resp.events != null && !resp.events.isEmpty()) {
                        processor.processEvents(resp.events);
                    }
                    if (resp.nextCursor != null && !resp.nextCursor.equals(currentCursor)) {
                        cursorRepo.save(new TianggeFeedCursor(1, resp.nextCursor));
                    }
                    hasMore = resp.events != null && resp.events.size() >= 50;
                }
            }
        } catch (Exception e) {
            log.error("Failed to poll Tiangge feed: {}", e.getMessage());
        }
    }
}
