package com.bulc.homepage.security;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrId) throws UsernameNotFoundException {
        User user;

        // Check if the input is a UUID (used by authenticated requests)
        try {
            UUID userId = UUID.fromString(usernameOrId);
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + usernameOrId));
        } catch (IllegalArgumentException e) {
            // Not a UUID, treat as email (used by OAuth and other flows)
            user = userRepository.findByEmail(usernameOrId)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + usernameOrId));
        }

        return buildUserDetails(user);
    }

    /**
     * UUID로 사용자 로드 (새 토큰용)
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(UUID userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        return buildUserDetails(user);
    }

    private UserDetails buildUserDetails(User user) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        // rolesCode 기반 권한 설정
        String rolesCode = user.getRolesCode();
        if (rolesCode != null) {
            switch (rolesCode) {
                case "000":
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    break;
                case "001":
                    authorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
                    break;
                default:
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                    break;
            }
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        // username을 userId.toString()으로 설정 (컨트롤러에서 authentication.getName()으로 userId 획득 가능)
        return new org.springframework.security.core.userdetails.User(
                user.getId().toString(),
                user.getPasswordHash() != null ? user.getPasswordHash() : "",
                authorities
        );
    }
}
