package com.jobmatching.demo.controller;

import com.jobmatching.demo.dto.AssignRoleRequest;
import com.jobmatching.demo.dto.CandidateIdRequest;
import com.jobmatching.demo.dto.RoleNameRequest;
import com.jobmatching.demo.entity.Role;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.security.JwtUserPrincipal;
import com.jobmatching.demo.service.RoleService;
import com.jobmatching.demo.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ports app.py's Admin-only user/role management routes. Every endpoint here requires
 * the Admin role (class-level @PreAuthorize), matching API_DOCUMENTATION.md's note that
 * the whole section is Admin-only.
 */
@RestController
@PreAuthorize("hasRole('Admin')")
public class AdminController {

    private final UserAccountService userAccountService;
    private final RoleService roleService;

    public AdminController(UserAccountService userAccountService, RoleService roleService) {
        this.userAccountService = userAccountService;
        this.roleService = roleService;
    }

    @GetMapping("/api/admin/users")
    public Map<String, Object> getUsers() {
        var users = userAccountService.getAllUsersWithRoles().stream()
                .map(AdminController::userSummary)
                .toList();
        return Map.of("users", users);
    }

    @PostMapping("/api/admin/users/{userId}/roles")
    public Map<String, String> assignRole(@PathVariable UUID userId, @Valid @RequestBody AssignRoleRequest request) {
        boolean assigned = userAccountService.assignRoleToUser(userId, request.roleId());
        if (!assigned) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User or role not found");
        }
        return Map.of("message", "Role assigned");
    }

    @DeleteMapping("/api/admin/users/{userId}/roles/{roleId}")
    public Map<String, String> removeRole(@PathVariable UUID userId, @PathVariable UUID roleId) {
        boolean removed = userAccountService.removeRoleFromUser(userId, roleId);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role mapping not found");
        }
        return Map.of("message", "Role removed");
    }

    @PutMapping("/api/admin/users/{userId}/candidate-profile")
    public Map<String, String> linkCandidateProfile(@PathVariable UUID userId, @Valid @RequestBody CandidateIdRequest request) {
        boolean linked = userAccountService.associateCandidateProfile(userId, request.candidateId());
        if (!linked) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User or candidate not found");
        }
        return Map.of("message", "Profile associated");
    }

    @DeleteMapping("/api/admin/users/{userId}/candidate-profile")
    public Map<String, String> unlinkCandidateProfile(@PathVariable UUID userId) {
        boolean unlinked = userAccountService.disassociateCandidateProfile(userId);
        if (!unlinked) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return Map.of("message", "Profile disassociated");
    }

    @DeleteMapping("/api/admin/users/{userId}")
    public Map<String, String> deleteUser(@PathVariable UUID userId, @AuthenticationPrincipal JwtUserPrincipal principal) {
        if (userId.equals(principal.userId())) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }
        boolean deleted = userAccountService.deleteUser(userId);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return Map.of("message", "User deleted");
    }

    @GetMapping("/api/roles")
    public Map<String, Object> getRoles() {
        var roles = roleService.getRoles().stream().map(AdminController::roleSummary).toList();
        return Map.of("roles", roles);
    }

    @PostMapping("/api/roles")
    public ResponseEntity<Map<String, Object>> createRole(@Valid @RequestBody RoleNameRequest request) {
        Role role = roleService.createRole(request.roleName());
        return ResponseEntity.status(HttpStatus.CREATED).body(roleSummary(role));
    }

    @PutMapping("/api/roles/{roleId}")
    public Map<String, String> updateRole(@PathVariable UUID roleId, @Valid @RequestBody RoleNameRequest request) {
        boolean updated = roleService.updateRole(roleId, request.roleName());
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }
        return Map.of("message", "Role updated");
    }

    @DeleteMapping("/api/roles/{roleId}")
    public Map<String, String> deleteRole(@PathVariable UUID roleId) {
        boolean deleted = roleService.deleteRole(roleId);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }
        return Map.of("message", "Role deleted");
    }

    private static Map<String, Object> userSummary(UserAccount user) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", user.getId());
        summary.put("username", user.getUsername());
        summary.put("email", user.getEmail());
        summary.put("candidate_profile_id", user.getCandidateProfile() != null ? user.getCandidateProfile().getId() : null);
        summary.put("roles", user.getRoles().stream().map(Role::getRoleName).sorted().toList());
        return summary;
    }

    private static Map<String, Object> roleSummary(Role role) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("role_id", role.getRoleId());
        summary.put("role_name", role.getRoleName());
        return summary;
    }
}
