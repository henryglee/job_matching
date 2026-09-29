package com.jobmatching.demo.service;

import com.jobmatching.demo.entity.Role;
import com.jobmatching.demo.repository.RoleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RoleService {

    static final Set<String> BUILT_IN_ROLES = Set.of("Admin", "Staff", "User");

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<Role> getRoles() {
        return roleRepository.findAll(Sort.by("roleName"));
    }

    @Transactional
    public Role createRole(String roleName) {
        String name = roleName.strip();
        if (roleRepository.existsByRoleName(name)) {
            throw new IllegalArgumentException("A role named '" + name + "' already exists");
        }
        Role role = new Role();
        role.setRoleName(name);
        return roleRepository.save(role);
    }

    @Transactional
    public boolean updateRole(UUID roleId, String roleName) {
        String name = roleName.strip();
        return roleRepository.findById(roleId).map(role -> {
            if (role.getRoleName().equals(name)) {
                return true;
            }
            requireNotBuiltIn(role, "renamed");
            if (roleRepository.existsByRoleName(name)) {
                throw new IllegalArgumentException("A role named '" + name + "' already exists");
            }
            role.setRoleName(name);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean deleteRole(UUID roleId) {
        Role role = roleRepository.findById(roleId).orElse(null);
        if (role == null) {
            return false;
        }
        requireNotBuiltIn(role, "deleted");
        try {
            roleRepository.delete(role);
            roleRepository.flush(); // surface a foreign-key failure here, not at commit
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("'" + role.getRoleName() + "' is still assigned to users; remove it from them first");
        }
        return true;
    }

    // Access checks, sign-up and the JWT all look these up by name, so changing or removing one
    // would lock people out (or, for a rename, silently strip everyone who holds it).
    private static void requireNotBuiltIn(Role role, String action) {
        if (BUILT_IN_ROLES.contains(role.getRoleName())) {
            throw new IllegalArgumentException("The built-in '" + role.getRoleName() + "' role can't be " + action);
        }
    }
}
