package com.enterprise.boilerplate.infrastructure.audit;

import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Profile("inmemory")
public class InMemoryAuditLog implements AuditPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAuditLog.class);

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(AuditEvent event) {
        events.add(event);
        log.info("audit type={} actor={} target={} detail={}",
                event.type(), event.actorUserId(), event.targetUserId(), event.detail());
    }

    public List<AuditEvent> findAll() {
        return List.copyOf(events);
    }
}
