package com.bulc.homepage.repository;

import com.bulc.homepage.entity.UserSocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, Long> {

    Optional<UserSocialAccount> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByProviderAndProviderId(String provider, String providerId);

    Optional<UserSocialAccount> findByUserEmailAndProvider(String userEmail, String provider);
}
