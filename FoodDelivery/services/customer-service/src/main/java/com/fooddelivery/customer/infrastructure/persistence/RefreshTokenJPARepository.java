package com.fooddelivery.customer.infrastructure.persistence;

import com.fooddelivery.customer.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenJPARepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.status = com.fooddelivery.customer.domain.model.enums.RefreshTokenStatus.REVOKED, r.revokedAt = CURRENT_TIMESTAMP WHERE r.user.id = :userId AND r.status = com.fooddelivery.customer.domain.model.enums.RefreshTokenStatus.ACTIVE")
    void revokeAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.status = com.fooddelivery.customer.domain.model.enums.RefreshTokenStatus.REVOKED, r.revokedAt = CURRENT_TIMESTAMP WHERE r.sessionId = :sessionId AND r.status = com.fooddelivery.customer.domain.model.enums.RefreshTokenStatus.ACTIVE")
    void revokeAllBySessionId(@Param("sessionId") UUID sessionId);
}
