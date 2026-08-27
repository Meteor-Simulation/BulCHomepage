package com.bulc.homepage.repository;

import com.bulc.homepage.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 활성 사용자 전체 조회 (운영성 메일 일괄 발송용). */
    List<User> findAllByIsActiveTrue();

    /**
     * 광고성 메일 발송 대상 — 활성 + 수신 동의(Y) 회원 (MDP-772).
     * 거절(N)·미선택(P)은 제외된다. 호출부에서 {@code MarketingConsent.AGREED} 를 넘긴다.
     */
    List<User> findAllByIsActiveTrueAndMarketingConsent(String marketingConsent);

    /** 수신거부 토큰으로 회원 조회 (광고성 메일 수신거부 링크 처리용). */
    Optional<User> findByUnsubscribeToken(String unsubscribeToken);
}
