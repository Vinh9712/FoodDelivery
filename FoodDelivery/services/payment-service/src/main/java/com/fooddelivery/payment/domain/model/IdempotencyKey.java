package com.fooddelivery.payment.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.payment.domain.exception.IdempotencyKeyAlreadyUsedException;
import com.fooddelivery.payment.domain.model.valueobject.CachedResponse;
import com.fooddelivery.payment.domain.model.valueobject.RequestHash;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.payment.domain.util.UuidCreator;

@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Type(JsonType.class)
    @Column(name = "response", columnDefinition = "jsonb")
    private CachedResponse response;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IdempotencyKey(UUID id, String idempotencyKey, RequestHash requestHash, Instant expiresAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash.value();
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public static IdempotencyKey create(String idempotencyKey, RequestHash requestHash, Instant expiresAt) {
        return new IdempotencyKey(UuidCreator.nextUuidV7(), idempotencyKey, requestHash, expiresAt);
    }

    public RequestHash getRequestHash() {
        return new RequestHash(this.requestHash);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean hasResponse() {
        return response != null;
    }

    public void attachPayment(UUID paymentId) {
        if (this.paymentId != null) {
            throw new IdempotencyKeyAlreadyUsedException(idempotencyKey);
        }
        this.paymentId = paymentId;
    }

    public void cacheResponse(CachedResponse response) {
        this.response = response;
    }
}
