package com.jobmatching.demo.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Ports app.py's repeated inline ownership check:
 *   if current_user.active_role not in ('Admin', 'Staff') and current_user.candidate_profile_id != candidate_id:
 *       abort(403)
 * A User may only act on their own linked candidate profile; Admin/Staff may act on any.
 */
public final class AccessControl {

    private AccessControl() {
    }

    public static boolean isStaffOrAdmin(JwtUserPrincipal principal) {
        return "Admin".equals(principal.activeRole()) || "Staff".equals(principal.activeRole());
    }

    public static void requireOwnerOrStaff(JwtUserPrincipal principal, UUID candidateId) {
        if (!isStaffOrAdmin(principal) && !candidateId.equals(principal.candidateProfileId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    // For changes only the candidate should make, where Admin/Staff get a read-only view
    // (e.g. an applied job's status, which the candidate reports themselves).
    public static void requireOwner(JwtUserPrincipal principal, UUID candidateId) {
        if (!candidateId.equals(principal.candidateProfileId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the candidate can change this");
        }
    }
}
