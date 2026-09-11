package com.bulc.homepage.licensing.domain;

/**
 * 활성화를 수행한 클라이언트 종류 (v1.2.0 · 계약 §3 B5 · MDP-791 컬럼 / MDP-790 배선).
 *
 * <p>GUI(BULC-AI Electron)와 CLI(fds_gpu)가 같은 좌석(activationId)을 공유할 때,
 * 어느 쪽이 실행 중인지 관측하기 위한 구분값. nullable — 구버전 클라이언트는 미전송.</p>
 *
 * <p>표기 주의: {@code @Enumerated(STRING)} 로 <b>대문자 GUI/CLI</b> 로 저장된다.
 * 요청 와이어 값은 소문자 gui|cli 이므로, DTO→도메인 배선 시 대소문자 무관 변환이 필요하다
 * (MDP-790 에서 대소문자 관대 파서로 처리).</p>
 */
public enum ClientKind {
    GUI,
    CLI
}
