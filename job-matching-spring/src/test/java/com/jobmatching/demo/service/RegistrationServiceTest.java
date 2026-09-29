package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.PendingRegistration;
import com.jobmatching.demo.entity.Role;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.repository.PendingRegistrationRepository;
import com.jobmatching.demo.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationServiceTest {

    private PendingRegistrationRepository pendingRepository;
    private UserAccountService userAccountService;
    private RoleRepository roleRepository;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        pendingRepository = mock(PendingRegistrationRepository.class);
        userAccountService = mock(UserAccountService.class);
        roleRepository = mock(RoleRepository.class);
        service = new RegistrationService(pendingRepository, userAccountService, roleRepository);
    }

    @Test
    void startRegistrationStoresHashedPasswordAndReturnsSixDigitCode() {
        when(userAccountService.hashPassword("secret123")).thenReturn("HASH");

        String code = service.startRegistration("New@Example.com", "newuser", "secret123", "New", "User");

        assertTrue(code.matches("\\d{6}"), code);
        ArgumentCaptor<PendingRegistration> saved = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(pendingRepository).deleteByEmailIgnoreCase("New@Example.com");
        verify(pendingRepository).save(saved.capture());
        assertEquals("new@example.com", saved.getValue().getEmail());
        assertEquals("HASH", saved.getValue().getPasswordHash());
        assertEquals(code, saved.getValue().getCode());
        assertEquals(0, saved.getValue().getAttempts());
    }

    @Test
    void startRegistrationRejectsTakenEmailAndSpacesInPassword() {
        when(userAccountService.emailExists("taken@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.startRegistration("taken@example.com", "u", "password1", "A", "B"));
        assertThrows(IllegalArgumentException.class,
                () -> service.startRegistration("free@example.com", "u", "pass word1", "A", "B"));
        verify(pendingRepository, never()).save(any());
    }

    @Test
    void correctCodeCreatesUserWithUserRoleAndDeletesPending() {
        PendingRegistration pending = pending("123456", 0, 5);
        when(pendingRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(pending));
        UserAccount user = new UserAccount();
        when(userAccountService.createUserWithPasswordHash("a@example.com", "alice", "HASH", "Alice", "Smith"))
                .thenReturn(user);
        Role userRole = new Role();
        userRole.setRoleName("User");
        when(roleRepository.findByRoleName("User")).thenReturn(Optional.of(userRole));

        RegistrationService.VerificationResult result = service.verify("a@example.com", " 123456 ");

        assertEquals(RegistrationService.Status.VERIFIED, result.status());
        assertSame(user, result.user());
        assertEquals(Set.of(userRole), user.getRoles());
        verify(pendingRepository).delete(pending);
    }

    @Test
    void missingUserRoleStillCreatesAccountWithoutRole() {
        PendingRegistration pending = pending("123456", 0, 5);
        when(pendingRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(pending));
        UserAccount user = new UserAccount();
        when(userAccountService.createUserWithPasswordHash(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(user);
        when(roleRepository.findByRoleName("User")).thenReturn(Optional.empty());

        RegistrationService.VerificationResult result = service.verify("a@example.com", "123456");

        assertEquals(RegistrationService.Status.VERIFIED, result.status());
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    void wrongCodeIncrementsAttemptsUntilLimitThenDiscardsPending() {
        PendingRegistration pending = pending("123456", 0, 5);
        when(pendingRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(pending));

        for (int i = 1; i < RegistrationService.MAX_ATTEMPTS; i++) {
            assertEquals(RegistrationService.Status.INCORRECT_CODE, service.verify("a@example.com", "000000").status());
            assertEquals(i, pending.getAttempts());
        }
        assertEquals(RegistrationService.Status.TOO_MANY_ATTEMPTS, service.verify("a@example.com", "000000").status());
        verify(pendingRepository).delete(pending);
        verify(userAccountService, never()).createUserWithPasswordHash(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void expiredCodeIsRejectedEvenIfCorrect() {
        PendingRegistration pending = pending("123456", 0, -1);
        when(pendingRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(pending));

        assertEquals(RegistrationService.Status.EXPIRED, service.verify("a@example.com", "123456").status());
        verify(pendingRepository).delete(pending);
    }

    @Test
    void noPendingRegistration() {
        when(pendingRepository.findByEmailIgnoreCase("x@example.com")).thenReturn(Optional.empty());

        assertEquals(RegistrationService.Status.NO_PENDING, service.verify("x@example.com", "123456").status());
    }

    @Test
    void usernameClaimedWhilePendingIsRejected() {
        PendingRegistration pending = pending("123456", 0, 5);
        when(pendingRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(pending));
        when(userAccountService.usernameExists("alice")).thenReturn(true);

        assertEquals(RegistrationService.Status.USERNAME_TAKEN, service.verify("a@example.com", "123456").status());
        verify(pendingRepository).delete(pending);
    }

    private static PendingRegistration pending(String code, int attempts, int minutesUntilExpiry) {
        PendingRegistration p = new PendingRegistration();
        p.setEmail("a@example.com");
        p.setUsername("alice");
        p.setFirstName("Alice");
        p.setLastName("Smith");
        p.setPasswordHash("HASH");
        p.setCode(code);
        p.setAttempts(attempts);
        p.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(minutesUntilExpiry));
        return p;
    }
}
