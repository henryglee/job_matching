package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.RegisterRequest;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.repository.RoleRepository;
import com.jobmatching.demo.repository.UserAccountRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** One login string can match two accounts; the owner of that email wins, and it no longer errors. */
class LoginLookupTest {

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final UserAccountService service = new UserAccountService(
            repository, mock(RoleRepository.class), mock(CandidateInfoRepository.class), mock(PasswordEncoder.class));

    private static UserAccount user(String username) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        return u;
    }

    @Test
    void takesTheFirstMatchWhichTheQueryOrdersEmailFirst() {
        UserAccount owner = user("victim");
        UserAccount impostor = user("victim@example.com");
        when(repository.findByEmailIgnoreCaseOrUsername("victim@example.com")).thenReturn(List.of(owner, impostor));

        assertEquals(owner, service.getUserByLogin("victim@example.com").orElseThrow());
    }

    @Test
    void noMatchIsEmpty() {
        when(repository.findByEmailIgnoreCaseOrUsername("nobody")).thenReturn(List.of());
        assertTrue(service.getUserByLogin("nobody").isEmpty());
    }

    @Test
    void newUsernamesCantContainAnAtSign() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var bad = validator.validate(new RegisterRequest("a@example.com", "b@example.com", "A", "L", "password1", "password1"));
        assertEquals(1, bad.size());
        assertEquals("Username cannot contain @", bad.iterator().next().getMessage());
        assertTrue(validator.validate(new RegisterRequest("a@example.com", "ada_l", "A", "L", "password1", "password1")).isEmpty());
    }
}
