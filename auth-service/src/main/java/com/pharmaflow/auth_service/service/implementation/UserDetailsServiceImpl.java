package com.pharmaflow.auth_service.service.implementation;

import com.pharmaflow.auth_service.config.security.CustomUserDetails;
import com.pharmaflow.auth_service.persistence.entity.AuthRoleEntity;
import com.pharmaflow.auth_service.persistence.entity.AuthUserEntity;
import com.pharmaflow.auth_service.persistence.entity.AuthUserRoleEntity;
import com.pharmaflow.auth_service.persistence.repository.AuthRolePermissionRepository;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRepository;
import com.pharmaflow.auth_service.persistence.repository.AuthUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String PERMISSION_SEPARATOR = ":";

    private final AuthUserRepository authUserRepository;
    private final AuthUserRoleRepository authUserRoleRepository;
    private final AuthRolePermissionRepository authRolePermissionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthUserEntity user = this.authUserRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        return this.buildUserDetails(user);
    }

    @Transactional(readOnly = true)
    public CustomUserDetails loadUserDetailsFor(AuthUserEntity user) {
        return this.buildUserDetails(user);
    }

    private CustomUserDetails buildUserDetails(AuthUserEntity user) {

        Set<AuthRoleEntity> activeRoles = this.authUserRoleRepository
                .findActiveRolesByUserId(user.getIdUser(), Instant.now())
                .stream()
                .map(AuthUserRoleEntity::getRole)
                .collect(Collectors.toSet());

        Set<Long> idsRoles = activeRoles.stream()
                .map(AuthRoleEntity::getIdRole)
                .collect(Collectors.toSet());

        Set<GrantedAuthority> authorities = new HashSet<>();

        activeRoles.forEach(role ->
                authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role.getRoleName())));

        if (!idsRoles.isEmpty()) {
            this.authRolePermissionRepository
                    .findActivePermissionsByRoleIds(idsRoles)
                    .forEach(rp -> authorities.add(new SimpleGrantedAuthority(
                            rp.getPermission().getResource() + PERMISSION_SEPARATOR + rp.getPermission().getAction()
                    )));
        }

        Set<String> roleNames = activeRoles.stream()
                .map(AuthRoleEntity::getRoleName)
                .collect(Collectors.toSet());

        return new CustomUserDetails(
                user.getEmail(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getActive()),
                !Boolean.TRUE.equals(user.getAccountExpired()),
                !Boolean.TRUE.equals(user.getCredentialsExpired()),
                !Boolean.TRUE.equals(user.getAccountLocked()),
                authorities,
                user.getIdUser(),
                roleNames
        );
    }
}
