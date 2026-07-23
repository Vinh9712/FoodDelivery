package com.fooddelivery.authentication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    public void record(String action, String outcome, UUID actorId, UUID targetUserId, String email, String ipAddress) {
        log.info("action={} outcome={} actorId={} targetUserId={} email={} ipAddress={}",
                action,
                outcome,
                actorId,
                targetUserId,
                email,
                ipAddress);
    }
}
