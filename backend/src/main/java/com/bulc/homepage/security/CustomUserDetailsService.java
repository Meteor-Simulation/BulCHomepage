package com.bulc.homepage.security;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.entity.UserRoleMapping;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.repository.UserRoleMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        List<UserRoleMapping> roleMappings = userRoleMappingRepository.findByUserIdWithRole(user.getId());

        List<SimpleGrantedAuthority> authorities = roleMappings.stream()
                .map(mapping -> new SimpleGrantedAuthority("ROLE_" + mapping.getRole().getCode().toUpperCase()))
                .collect(Collectors.toList());

        // 기본 역할이 없으면 USER 역할 추가
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash() != null ? user.getPasswordHash() : "",
                authorities
        );
    }
}
