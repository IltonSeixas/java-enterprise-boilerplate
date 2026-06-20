package com.enterprise.boilerplate.application.port.out;

import com.enterprise.boilerplate.domain.audit.AuditEvent;

public interface AuditPort {

    void record(AuditEvent event);
}
