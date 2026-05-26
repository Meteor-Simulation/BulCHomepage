package com.bulc.homepage.controller;

import com.bulc.homepage.dto.PopupDto;
import com.bulc.homepage.entity.Popup;
import com.bulc.homepage.service.PopupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MDP-509 공개 팝업 조회 API.
 * 프론트에서 HOME_ENTRY 또는 POST_LOGIN 시점에 호출하여 활성 팝업 목록을 받는다.
 */
@RestController
@RequestMapping("/api/popups")
@RequiredArgsConstructor
public class PopupController {

    private final PopupService popupService;

    @GetMapping
    public ResponseEntity<?> findActive(@RequestParam("trigger") String triggerName) {
        Popup.Trigger trigger;
        try {
            trigger = Popup.Trigger.valueOf(triggerName);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "trigger는 HOME_ENTRY 또는 POST_LOGIN 이어야 합니다"));
        }
        List<PopupDto.Response> popups = popupService.findActiveByTrigger(trigger);
        return ResponseEntity.ok(popups);
    }
}
