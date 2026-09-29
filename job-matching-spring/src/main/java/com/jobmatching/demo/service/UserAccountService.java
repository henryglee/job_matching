package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.Role;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.repository.RoleRepository;
import com.jobmatching.demo.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ports the user/role/account-linking side of jobspy/database.py. Password hashing uses
 * Spring Security's BCryptPasswordEncoder in place of Flask's bcrypt calls — same algorithm,
 * so existing password hashes in the database remain verifiable unmodified.
 */
@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final CandidateInfoRepository candidateInfoRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserAccountRepository userAccountRepository,
                               RoleRepository roleRepository,
                               CandidateInfoRepository candidateInfoRepository,
                               PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.candidateInfoRepository = candidateInfoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount createUser(String email, String username, String rawPassword, String firstName, String lastName) {
        return createUserWithPasswordHash(email, username, hashPassword(rawPassword), firstName, lastName);
    }

    // For the email-verification flow: the password is hashed at /register time and only
    // the hash is held in pending_registrations until the code is confirmed.
    @Transactional
    public UserAccount createUserWithPasswordHash(String email, String username, String passwordHash, String firstName, String lastName) {
        UserAccount user = new UserAccount();
        user.setEmail(email.toLowerCase());
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        return userAccountRepository.save(user);
    }

    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public Optional<UserAccount> getUserById(UUID userId) {
        return userAccountRepository.findById(userId);
    }

    public Optional<UserAccount> getUserWithProfile(UUID userId) {
        return userAccountRepository.findByIdWithProfile(userId);
    }

    // Mirrors JobDatabase.get_user_by_login: login can be an email or a username.
    public Optional<UserAccount> getUserByLogin(String login) {
        return userAccountRepository.findByEmailIgnoreCaseOrUsername(login).stream().findFirst();
    }

    public boolean verifyPassword(UserAccount user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    public boolean emailExists(String email) {
        return userAccountRepository.existsByEmailIgnoreCase(email);
    }

    public boolean usernameExists(String username) {
        return userAccountRepository.existsByUsername(username);
    }

    @Transactional
    public boolean updateUserName(UUID userId, String firstName, String lastName) {
        return userAccountRepository.findById(userId).map(user -> {
            user.setFirstName(firstName);
            user.setLastName(lastName);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean updateUserPassword(String email, String rawPassword) {
        return userAccountRepository.findByEmailIgnoreCase(email).map(user -> {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            return true;
        }).orElse(false);
    }

    // Maps candidate profile id -> the account linked to it; unlinked profiles are absent.
    public Map<UUID, UserAccount> getLinkedAccounts(Collection<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        return userAccountRepository.findByCandidateProfileIdIn(candidateIds).stream()
                .collect(Collectors.toMap(u -> u.getCandidateProfile().getId(), Function.identity()));
    }

    public List<UserAccount> getAllUsersWithRoles() {
        return userAccountRepository.findAllWithRoles();
    }

    public Set<Role> getUserRoles(UUID userId) {
        return userAccountRepository.findById(userId).map(UserAccount::getRoles).orElse(Set.of());
    }

    @Transactional
    public boolean assignRoleToUser(UUID userId, UUID roleId) {
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        Role role = roleRepository.findById(roleId).orElse(null);
        if (user == null || role == null) {
            return false;
        }
        user.getRoles().add(role);
        return true;
    }

    @Transactional
    public boolean removeRoleFromUser(UUID userId, UUID roleId) {
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        boolean holdsAdmin = user.getRoles().stream()
                .anyMatch(r -> r.getRoleId().equals(roleId) && "Admin".equals(r.getRoleName()));
        if (holdsAdmin && userAccountRepository.countByRoleName("Admin") <= 1) {
            throw new IllegalArgumentException("Can't remove the last Admin; make someone else an Admin first");
        }
        return user.getRoles().removeIf(role -> role.getRoleId().equals(roleId));
    }

    @Transactional
    public boolean associateCandidateProfile(UUID userId, UUID candidateId) {
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        CandidateInfo candidate = candidateInfoRepository.findById(candidateId).orElse(null);
        if (user == null || candidate == null) {
            return false;
        }
        // candidate_profile_id is unique: without this, linking a profile that already belongs
        // to someone else fails at commit as an unhandled constraint violation (a 500).
        if (userAccountRepository.findByCandidateProfileId(candidateId)
                .filter(owner -> !owner.getId().equals(userId)).isPresent()) {
            throw new IllegalArgumentException("That candidate profile is already linked to another account");
        }
        user.setCandidateProfile(candidate);
        return true;
    }

    @Transactional
    public boolean disassociateCandidateProfile(UUID userId) {
        return userAccountRepository.findById(userId).map(user -> {
            user.setCandidateProfile(null);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean deleteUser(UUID userId) {
        if (!userAccountRepository.existsById(userId)) {
            return false;
        }
        userAccountRepository.deleteById(userId);
        return true;
    }
}
