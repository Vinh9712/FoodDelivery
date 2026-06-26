package com.fooddelivery.customer.infrastructure.persistence;

import com.fooddelivery.customer.domain.model.RefreshToken;
import com.fooddelivery.customer.domain.model.enums.RefreshTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface RefreshTokenJPARepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.status = :revokedStatus, r.revokedAt = :revokedAt WHERE r.user.id = :userId AND r.status = :activeStatus")
    void revokeAllByUserId(@Param("userId") UUID userId,
                           @Param("activeStatus") RefreshTokenStatus activeStatus,
                           @Param("revokedStatus") RefreshTokenStatus revokedStatus,
                           @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.status = :revokedStatus, r.revokedAt = :revokedAt WHERE r.sessionId = :sessionId AND r.status = :activeStatus")
    void revokeAllBySessionId(@Param("sessionId") UUID sessionId,
                              @Param("activeStatus") RefreshTokenStatus activeStatus,
                              @Param("revokedStatus") RefreshTokenStatus revokedStatus,
                              @Param("revokedAt") Instant revokedAt);
}
