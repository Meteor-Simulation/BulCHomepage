package com.bulc.homepage.controller;

import com.bulc.homepage.dto.response.ApiResponse;
import com.bulc.homepage.entity.PostImage;
import com.bulc.homepage.repository.PostImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/posts/images")
@RequiredArgsConstructor
public class PostImageController {

    private final PostImageRepository postImageRepository;

    @Value("${bulc.upload.path:uploads/posts}")
    private String uploadPath;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * 이미지 업로드
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadImage(
            @RequestParam("file") MultipartFile file) {

        // 파일 크기 검증
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("파일이 비어있습니다."));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(ApiResponse.error("파일 크기는 5MB 이하여야 합니다."));
        }

        // 확장자 검증
        String originalName = file.getOriginalFilename();
        String extension = getFileExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("허용되지 않는 파일 형식입니다. (jpg, jpeg, png, gif, webp만 가능)"));
        }

        try {
            // 저장 디렉토리 생성
            Path uploadDir = Paths.get(uploadPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // UUID 파일명으로 저장
            String savedFileName = UUID.randomUUID() + "." + extension;
            Path filePath = uploadDir.resolve(savedFileName);
            file.transferTo(filePath.toFile());

            // DB 저장 (post_id는 null — 글 저장 시 연결)
            PostImage postImage = PostImage.builder()
                    .imageUrl("/" + uploadPath + "/" + savedFileName)
                    .originalName(originalName)
                    .fileSize(file.getSize())
                    .build();
            postImage = postImageRepository.save(postImage);

            log.info("이미지 업로드 - id: {}, name: {}, size: {}KB", postImage.getId(), originalName, file.getSize() / 1024);

            return ResponseEntity.ok(ApiResponse.success("이미지가 업로드되었습니다.", Map.of(
                    "imageId", postImage.getId(),
                    "imageUrl", postImage.getImageUrl()
            )));
        } catch (IOException e) {
            log.error("이미지 업로드 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse.error("이미지 업로드에 실패했습니다."));
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
