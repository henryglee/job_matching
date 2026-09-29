package com.jobmatching.demo.controller;

import com.jobmatching.demo.config.SecurityConfig;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.security.JwtService;
import com.jobmatching.demo.service.UserAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real security chain (SecurityConfig + JwtAuthenticationFilter + real signed
 * JWTs), so the role rules on each route are exercised as deployed. No database needed.
 */
@WebMvcTest(UserNameController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "app.cors.allowed-origins=http://localhost:3000"
})
class UserNameControllerTest {

    private static final String VALID_BODY = "{\"first_name\": \"  Ada \", \"last_name\": \"Lovelace\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserAccountService userAccountService;

    @Test
    void userCanRenameThemselvesAndNamesAreTrimmed() throws Exception {
        UUID self = UUID.randomUUID();
        when(userAccountService.updateUserName(self, "Ada", "Lovelace")).thenReturn(true);

        mockMvc.perform(json(put("/api/user/name"), token(self, "User"), VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Name updated successfully."));
        verify(userAccountService).updateUserName(self, "Ada", "Lovelace");
    }

    @Test
    void selfRenameRequiresBothNames() throws Exception {
        mockMvc.perform(json(put("/api/user/name"), token(UUID.randomUUID(), "User"), "{\"first_name\": \"Ada\", \"last_name\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("First name and last name are required."));
        verify(userAccountService, never()).updateUserName(any(), any(), any());
    }

    @Test
    void selfRenameRequiresLogin() throws Exception {
        mockMvc.perform(json(put("/api/user/name"), null, VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCanRenameAnotherUser() throws Exception {
        UUID target = UUID.randomUUID();
        when(userAccountService.updateUserName(target, "Ada", "Lovelace")).thenReturn(true);

        mockMvc.perform(json(put("/api/admin/users/" + target + "/name"), token(UUID.randomUUID(), "Staff"), VALID_BODY))
                .andExpect(status().isOk());
        verify(userAccountService).updateUserName(target, "Ada", "Lovelace");
    }

    @Test
    void adminRenameOfUnknownUserIs404() throws Exception {
        mockMvc.perform(json(put("/api/admin/users/" + UUID.randomUUID() + "/name"), token(UUID.randomUUID(), "Admin"), VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @Test
    void regularUserCannotRenameOthers() throws Exception {
        mockMvc.perform(json(put("/api/admin/users/" + UUID.randomUUID() + "/name"), token(UUID.randomUUID(), "User"), VALID_BODY))
                .andExpect(status().isForbidden());
        verify(userAccountService, never()).updateUserName(any(), any(), any());
    }

    private String token(UUID userId, String activeRole) {
        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setEmail("someone@example.com");
        user.setUsername("someone");
        return jwtService.generateToken(user, activeRole);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token, String body) {
        request.contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }
}
