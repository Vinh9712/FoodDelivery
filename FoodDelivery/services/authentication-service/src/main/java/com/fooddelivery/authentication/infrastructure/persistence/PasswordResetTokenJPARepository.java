package com.fooddelivery.authentication.infrastructure.persistence;

import com.fooddelivery.authentication.domain.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenJPARepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update PasswordResetToken t
            set t.usedAt = :now
            where t.user.id = :userId and t.usedAt is null
            """)
    int invalidateUnusedByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
