package edu.cit.lariosa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InstanceIdentity {
    private static final Logger log = LoggerFactory.getLogger(InstanceIdentity.class);
    private final String id;

    public InstanceIdentity() {
        this.id = UUID.randomUUID().toString();
        log.info("Application Instance ID generated: {}", this.id);
    }

    public String getId() {
        return id;
    }
}
