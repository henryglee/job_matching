package com.jobmatching.demo.security;

import com.jobmatching.demo.entity.Role;
import com.jobmatching.demo.repository.UserAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Looks up an account's current roles and linked profile on every authenticated request, so
 * a still-unexpired JWT can't outlive a deleted account, a removed role, an unlinked
 * profile or a changed password (its claims are only a snapshot from login time). One primary-key lookup per request.
 */
@Component
public class AccountStatusChecker {

    public record AccountStatus(Set<String> roles, UUID candidateProfileId, String passwordVersion) {
    }

    private final UserAccountRepository userAccountRepository;

    public AccountStatusChecker(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    // Empty if the account no longer exists.
    @Transactional(readOnly = true)
    public Optional<AccountStatus> lookup(UUID userId) {
        return userAccountRepository.findById(userId).map(user -> new AccountStatus(
                user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toSet()),
                user.getCandidateProfile() != null ? user.getCandidateProfile().getId() : null,
                JwtService.passwordVersion(user.getPasswordHash())));
    }
}
