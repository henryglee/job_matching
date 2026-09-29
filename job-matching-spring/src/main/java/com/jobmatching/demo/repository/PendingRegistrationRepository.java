package com.jobmatching.demo.repository;

import com.jobmatching.demo.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, UUID> {

    Optional<PendingRegistration> findByEmailIgnoreCase(String email);

    // Bulk JPQL delete (flushed immediately) rather than a derived deleteBy..., so a
    // re-registration can insert a fresh row for the same unique email in the same
    // transaction without hitting the unique constraint.
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PendingRegistration p WHERE LOWER(p.email) = LOWER(:email)")
    int deleteByEmailIgnoreCase(@Param("email") String email);

    @Modifying
    @Query("DELETE FROM PendingRegistration p WHERE p.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
