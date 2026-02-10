package com.bulc.homepage.repository;

import com.bulc.homepage.entity.UserSocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, Long> {

    Optional<UserSocialAccount> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByProviderAndProviderId(String provider, String providerId);

    Optional<UserSocialAccount> findByUserIdAndProvider(UUID userId, String provider);

    void deleteByUserId(UUID userId);
}
