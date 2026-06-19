package com.bulc.homepage.service;

import com.bulc.homepage.entity.User;
import com.bulc.homepage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 광고성 메일 수신거부 처리 (MDP-607).
 *
 * 광고성 메일 footer 의 /unsubscribe?token=... 링크에서 호출된다.
 * - 회원(User): unsubscribe_token 으로 조회 → marketing_agreed=false 로 변경
 *   (운영성 메일은 수신거부와 무관하게 계속 발송되므로 비활성화하지 않는다)
 * - 직접등록 컨택(LeadContact): UUID 토큰으로 조회 → 수신 비활성화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnsubscribeService {

    private final UserRepository userRepository;
    private final LeadContactService leadContactService;

    public enum Result { MEMBER, CONTACT, NOT_FOUND }

    @Transactional
    public Result unsubscribe(String token) {
        if (token == null || token.isBlank()) {
            return Result.NOT_FOUND;
        }
        String trimmed = token.trim();

        // 1) 회원(User) 토큰
        var userOpt = userRepository.findByUnsubscribeToken(trimmed);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (Boolean.TRUE.equals(user.getMarketingAgreed())) {
                user.setMarketingAgreed(false);
                user.setMarketingAgreedAt(null);
                userRepository.save(user);
                log.info("[수신거부] 회원 광고성 수신동의 해제: {}", user.getEmail());
            }
            return Result.MEMBER;
        }

        // 2) 직접등록 컨택(LeadContact) 토큰 (UUID)
        try {
            UUID uuid = UUID.fromString(trimmed);
            if (leadContactService.unsubscribeByToken(uuid, "이메일 수신거부 링크").isPresent()) {
                log.info("[수신거부] 컨택 수신 비활성화 (token={})", uuid);
                return Result.CONTACT;
            }
        } catch (IllegalArgumentException ignored) {
            // UUID 형식 아님 → 매칭 없음
        }

        return Result.NOT_FOUND;
    }
}
