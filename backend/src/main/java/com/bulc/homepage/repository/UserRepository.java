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

    /** 수신거부 토큰으로 회원 조회 (광고성 메일 수신거부 링크 처리용). */
    Optional<User> findByUnsubscribeToken(String unsubscribeToken);
}
