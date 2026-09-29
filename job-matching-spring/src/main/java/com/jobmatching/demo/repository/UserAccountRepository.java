package com.jobmatching.demo.repository;

import com.jobmatching.demo.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    // Mirrors JobDatabase.get_user_by_login: login can be an email (case-insensitive)
    // or a username (case-sensitive), matching the Python lookup exactly. Fetches
    // candidateProfile (LAZY) eagerly since JwtService needs it right after auth, once
    // the transaction backing this query has already closed (open-in-view=false).
    //
    // A List, email matches first: one login string can match two accounts (one user's username can
    // equal another's email), and a single-result query would then fail with a 500 for both of them.
    @Query("SELECT u FROM UserAccount u LEFT JOIN FETCH u.candidateProfile "
            + "WHERE LOWER(u.email) = LOWER(:login) OR u.username = :login "
            + "ORDER BY CASE WHEN LOWER(u.email) = LOWER(:login) THEN 0 ELSE 1 END")
    List<UserAccount> findByEmailIgnoreCaseOrUsername(@Param("login") String login);

    // No JOIN FETCH for `roles` here, deliberately — it's already FetchType.EAGER on the
    // entity so it loads safely regardless (just via a separate query per row rather than
    // one SQL join; a non-issue on this low-traffic admin endpoint). Combining a `roles`
    // JOIN FETCH with SELECT DISTINCT (needed to collapse the many-to-many row fan-out)
    // pulls candidate_info's `json`-typed columns (skills, sponsorship_type) into the
    // DISTINCT comparison — Postgres has no equality operator for `json` (only `jsonb`),
    // so that combination fails outright: "could not identify an equality operator for
    // type json". candidateProfile is a @OneToOne, so it never fans out rows and needs
    // neither DISTINCT nor this workaround.
    @Query("SELECT u FROM UserAccount u LEFT JOIN FETCH u.candidateProfile")
    List<UserAccount> findAllWithRoles();

    @Query("SELECT COUNT(u) FROM UserAccount u JOIN u.roles r WHERE r.roleName = :roleName")
    long countByRoleName(@Param("roleName") String roleName);

    // With candidateProfile (LAZY) fetched, for building a token once the transaction has closed.
    @Query("SELECT u FROM UserAccount u LEFT JOIN FETCH u.candidateProfile WHERE u.id = :id")
    Optional<UserAccount> findByIdWithProfile(@Param("id") UUID id);

    Optional<UserAccount> findByCandidateProfileId(UUID candidateProfileId);

    // Batch form for the candidates list, which shows each profile's linked account.
    @Query("SELECT u FROM UserAccount u JOIN FETCH u.candidateProfile c WHERE c.id IN :candidateIds")
    List<UserAccount> findByCandidateProfileIdIn(@Param("candidateIds") Collection<UUID> candidateIds);
}
