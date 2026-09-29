package com.jobmatching.demo.security;

import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.entity.UserAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A valid token is only honoured while the account still matches what it was issued for. */
class JwtAuthenticationFilterAccountStatusTest {

    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final String PWV = JwtService.passwordVersion("hash-at-login");

    private final JwtService jwtService = new JwtService("test-secret-that-is-at-least-thirty-two-bytes-long", 60_000);
    private final AccountStatusChecker checker = mock(AccountStatusChecker.class);
    private final UserAccount user = user();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deletedAccountIsNotAuthenticated() throws Exception {
        when(checker.lookup(user.getId())).thenReturn(Optional.empty());
        assertNull(run("User"));
    }

    @Test
    void removedRoleIsNotAuthenticated() throws Exception {
        when(checker.lookup(user.getId())).thenReturn(Optional.of(new AccountStatusChecker.AccountStatus(Set.of("User"), PROFILE_ID, PWV)));
        assertNull(run("Admin"));
    }

    @Test
    void changedPasswordIsNotAuthenticated() throws Exception {
        String afterReset = JwtService.passwordVersion("hash-after-reset");
        when(checker.lookup(user.getId())).thenReturn(Optional.of(new AccountStatusChecker.AccountStatus(Set.of("User"), PROFILE_ID, afterReset)));
        assertNull(run("User"));
    }

    @Test
    void unlinkedProfileIsDroppedFromThePrincipal() throws Exception {
        when(checker.lookup(user.getId())).thenReturn(Optional.of(new AccountStatusChecker.AccountStatus(Set.of("User"), null, PWV)));
        JwtUserPrincipal principal = run("User");
        assertEquals(user.getId(), principal.userId());
        assertNull(principal.candidateProfileId());
    }

    @Test
    void currentProfileIsUsedRatherThanTheTokensSnapshot() throws Exception {
        UUID relinked = UUID.randomUUID();
        when(checker.lookup(user.getId())).thenReturn(Optional.of(new AccountStatusChecker.AccountStatus(Set.of("User"), relinked, PWV)));
        assertEquals(relinked, run("User").candidateProfileId());
    }

    private JwtUserPrincipal run(String activeRole) throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<AccountStatusChecker> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(checker);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user, activeRole));
        new JwtAuthenticationFilter(jwtService, provider)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : (JwtUserPrincipal) auth.getPrincipal();
    }

    private static UserAccount user() {
        CandidateInfo profile = new CandidateInfo();
        profile.setId(PROFILE_ID);
        UserAccount u = new UserAccount();
        u.setId(UUID.randomUUID());
        u.setEmail("me@example.com");
        u.setUsername("me");
        u.setPasswordHash("hash-at-login");
        u.setCandidateProfile(profile);
        return u;
    }
}
