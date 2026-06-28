package com.fooddelivery.authentication.infrastructure.persistence;

import com.fooddelivery.authentication.domain.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserSessionJPARepository extends JpaRepository<UserSession, UUID> {
    List<UserSession> findAllByUserId(UUID userId);

    @Modifying
    @Query("UPDATE UserSession s SET s.isCurrent = false WHERE s.user.id = :userId AND s.isCurrent = true AND s.deleted = false")
    void markNotCurrentByUserId(@Param("userId") UUID userId);
}
