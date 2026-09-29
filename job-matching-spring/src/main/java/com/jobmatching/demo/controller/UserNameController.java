package com.jobmatching.demo.controller;

import com.jobmatching.demo.dto.UpdateNameRequest;
import com.jobmatching.demo.security.JwtUserPrincipal;
import com.jobmatching.demo.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Ports app.py's PUT /api/user/name and PUT /api/admin/users/&lt;id&gt;/name. Kept out of
 * AdminController because that class is Admin-only across the board, while app.py lets
 * Staff rename users too.
 *
 * Names aren't JWT claims (see JwtService), so a rename takes effect without re-issuing
 * the caller's token.
 */
@RestController
public class UserNameController {

    private final UserAccountService userAccountService;

    public UserNameController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    // Any logged-in user, always acting on their own account.
    @PutMapping("/api/user/name")
    public Map<String, String> updateOwnName(@Valid @RequestBody UpdateNameRequest request,
                                             @AuthenticationPrincipal JwtUserPrincipal principal) {
        updateName(principal.userId(), request);
        return Map.of("message", "Name updated successfully.");
    }

    // app.py accepts blank names here (only the self-service route checks); both routes
    // share UpdateNameRequest's validation instead, so an admin can't blank out a name.
    @PutMapping("/api/admin/users/{userId}/name")
    @PreAuthorize("hasAnyRole('Admin','Staff')")
    public Map<String, String> updateUserName(@PathVariable UUID userId,
                                              @Valid @RequestBody UpdateNameRequest request) {
        updateName(userId, request);
        return Map.of("message", "Name updated successfully.");
    }

    private void updateName(UUID userId, UpdateNameRequest request) {
        if (!userAccountService.updateUserName(userId, request.firstName().strip(), request.lastName().strip())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }
}
