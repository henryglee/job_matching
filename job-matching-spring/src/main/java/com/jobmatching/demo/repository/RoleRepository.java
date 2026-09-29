package com.jobmatching.demo.repository;

import com.jobmatching.demo.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByRoleName(String roleName);

    boolean existsByRoleName(String roleName);
}
