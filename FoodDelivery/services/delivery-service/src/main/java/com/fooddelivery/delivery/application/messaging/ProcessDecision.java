package com.fooddelivery.delivery.application.messaging;

/**
 * Outcome of sequenced inbox processing for a single integration event.
 */
public enum ProcessDecision {
    APPLIED,
    DUPLICATE,
    STALE,
    DEFERRED
}
