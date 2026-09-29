package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.Role;
import com.jobmatching.demo.entity.UserAccount;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.repository.RoleRepository;
import com.jobmatching.demo.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/** Role management can't break access control, and the last Admin can't be removed. */
class RoleAndAdminGuardsTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final RoleService roleService = new RoleService(roleRepository);
    private final UserAccountRepository userRepository = mock(UserAccountRepository.class);
    private final UserAccountService userService = new UserAccountService(
            userRepository, roleRepository, mock(CandidateInfoRepository.class), mock(PasswordEncoder.class));

    @Test
    void duplicateRoleNameIsRejectedRatherThanFailingAtTheDatabase() {
        when(roleRepository.existsByRoleName("Recruiter")).thenReturn(true);
        assertEquals("A role named 'Recruiter' already exists",
                assertThrows(IllegalArgumentException.class, () -> roleService.createRole(" Recruiter ")).getMessage());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void builtInRolesCantBeRenamedOrDeleted() {
        for (String name : new String[]{"Admin", "Staff", "User"}) {
            Role role = role(name);
            when(roleRepository.findById(role.getRoleId())).thenReturn(Optional.of(role));
            assertThrows(IllegalArgumentException.class, () -> roleService.updateRole(role.getRoleId(), "Renamed"));
            assertThrows(IllegalArgumentException.class, () -> roleService.deleteRole(role.getRoleId()));
            assertEquals(name, role.getRoleName());
        }
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void customRolesCanBeRenamedAndDeleted() {
        Role role = role("Recruiter");
        when(roleRepository.findById(role.getRoleId())).thenReturn(Optional.of(role));
        assertTrue(roleService.updateRole(role.getRoleId(), "Sourcer"));
        assertEquals("Sourcer", role.getRoleName());
        assertTrue(roleService.deleteRole(role.getRoleId()));
        verify(roleRepository).delete(role);
    }

    @Test
    void renameToAnExistingNameIsRejected() {
        Role role = role("Recruiter");
        when(roleRepository.findById(role.getRoleId())).thenReturn(Optional.of(role));
        when(roleRepository.existsByRoleName("Staff")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> roleService.updateRole(role.getRoleId(), "Staff"));
        assertEquals("Recruiter", role.getRoleName());
    }

    @Test
    void deletingARoleStillAssignedToUsersGivesAClearError() {
        Role role = role("Recruiter");
        when(roleRepository.findById(role.getRoleId())).thenReturn(Optional.of(role));
        doThrow(new DataIntegrityViolationException("fk")).when(roleRepository).flush();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> roleService.deleteRole(role.getRoleId()))
                .getMessage().contains("still assigned"));
    }

    @Test
    void lastAdminCantLoseTheAdminRole() {
        Role admin = role("Admin");
        UserAccount user = userWith(admin);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.countByRoleName("Admin")).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> userService.removeRoleFromUser(user.getId(), admin.getRoleId()));
        assertTrue(user.getRoles().contains(admin));
    }

    @Test
    void anAdminCanBeDemotedWhenAnotherRemains() {
        Role admin = role("Admin");
        UserAccount user = userWith(admin);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.countByRoleName("Admin")).thenReturn(2L);

        assertTrue(userService.removeRoleFromUser(user.getId(), admin.getRoleId()));
        assertFalse(user.getRoles().contains(admin));
    }

    @Test
    void otherRolesAreNotSubjectToTheLastAdminRule() {
        Role staff = role("Staff");
        UserAccount user = userWith(staff);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertTrue(userService.removeRoleFromUser(user.getId(), staff.getRoleId()));
    }

    private static Role role(String name) {
        Role role = new Role();
        role.setRoleId(UUID.randomUUID());
        role.setRoleName(name);
        return role;
    }

    private static UserAccount userWith(Role role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.getRoles().add(role);
        return user;
    }
}
