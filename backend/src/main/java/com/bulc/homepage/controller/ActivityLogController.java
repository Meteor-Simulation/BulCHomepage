package com.bulc.homepage.controller;

import com.bulc.homepage.dto.request.ActivityLogRequest;
import com.bulc.homepage.dto.response.ApiResponse;
import com.bulc.homepage.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    @PostMapping("/activity")
    public ResponseEntity<ApiResponse<Void>> logActivity(
            @Valid @RequestBody ActivityLogRequest request,
            HttpServletRequest httpRequest) {
        activityLogService.logActivity(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("활동이 기록되었습니다", null));
    }
}
