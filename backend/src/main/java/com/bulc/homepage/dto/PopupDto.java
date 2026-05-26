package com.bulc.homepage.dto;

import com.bulc.homepage.entity.Popup;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class PopupDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private Popup.Type type;
        private String title;
        private String content;
        private String imageUrl;
        private List<Popup.Trigger> triggers;
        private List<Popup.CloseOption> closeOptions;
        private Integer priority;
        private Boolean isActive;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Popup.Type type;
        private String title;
        private String content;
        private String imageUrl;
        private List<Popup.Trigger> triggers;
        private List<Popup.CloseOption> closeOptions;
        private Integer priority;
        private Boolean isActive;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(Popup p) {
            return Response.builder()
                    .id(p.getId())
                    .type(p.getType())
                    .title(p.getTitle())
                    .content(p.getContent())
                    .imageUrl(p.getImageUrl())
                    .triggers(p.getTriggerList())
                    .closeOptions(p.getCloseOptionList())
                    .priority(p.getPriority())
                    .isActive(p.getIsActive())
                    .startAt(p.getStartAt())
                    .endAt(p.getEndAt())
                    .createdAt(p.getCreatedAt())
                    .updatedAt(p.getUpdatedAt())
                    .build();
        }
    }
}
