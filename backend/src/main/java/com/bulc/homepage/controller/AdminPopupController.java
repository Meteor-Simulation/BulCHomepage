package com.bulc.homepage.controller;

import com.bulc.homepage.dto.PopupDto;
import com.bulc.homepage.repository.UserRepository;
import com.bulc.homepage.service.PopupService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MDP-509 관리자 팝업 CRUD + 이미지 업로드 API.
 * 권한: roles_code 000(관리자) / 001(매니저) 만 호출 가능.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/popups")
@RequiredArgsConstructor
public class AdminPopupController {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp");
    private static final String POPUP_UPLOAD_DIR = "uploads/popups";

    private final PopupService popupService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<?> list() {
        if (!isAdmin()) return forbidden();
        return ResponseEntity.ok(popupService.findAllForAdmin());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        if (!isAdmin()) return forbidden();
        try {
            return ResponseEntity.ok(popupService.findById(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody PopupDto.Request req) {
        if (!isAdmin()) return forbidden();
        try {
            return ResponseEntity.ok(popupService.create(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PopupDto.Request req) {
        if (!isAdmin()) return forbidden();
        try {
            return ResponseEntity.ok(popupService.update(id, req));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!isAdmin()) return forbidden();
        try {
            popupService.delete(id);
            return ResponseEntity.ok(Map.of("deleted", id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/images")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        if (!isAdmin()) return forbidden();
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일이 비어있습니다"));
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일 크기는 5MB 이하여야 합니다"));
        }
        String originalName = file.getOriginalFilename();
        String extension = getFileExtension(originalName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "허용되지 않는 파일 형식입니다 (jpg, jpeg, png, gif, webp 만 가능)"));
        }
        try {
            Path uploadDir = Paths.get(POPUP_UPLOAD_DIR).toAbsolutePath();
            if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);
            String savedFileName = UUID.randomUUID() + "." + extension;
            Path filePath = uploadDir.resolve(savedFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            String url = "/" + POPUP_UPLOAD_DIR + "/" + savedFileName;
            log.info("팝업 이미지 업로드 - name: {}, savedAs: {}, size: {}KB",
                    originalName, savedFileName, file.getSize() / 1024);
            return ResponseEntity.ok(Map.of("imageUrl", url));
        } catch (IOException e) {
            log.error("팝업 이미지 업로드 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "이미지 업로드에 실패했습니다"));
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        try {
            UUID userId = UUID.fromString(auth.getName());
            return userRepository.findById(userId)
                    .map(u -> "000".equals(u.getRolesCode()) || "001".equals(u.getRolesCode()))
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of("error", "관리자 권한이 필요합니다"));
    }
}
