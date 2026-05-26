package com.bulc.homepage.service;

import com.bulc.homepage.dto.PopupDto;
import com.bulc.homepage.entity.Popup;
import com.bulc.homepage.repository.PopupRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PopupService {

    private final PopupRepository popupRepository;

    @Transactional(readOnly = true)
    public List<PopupDto.Response> findAllForAdmin() {
        return popupRepository.findAllByOrderByPriorityAscIdAsc().stream()
                .map(PopupDto.Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PopupDto.Response> findActiveByTrigger(Popup.Trigger trigger) {
        LocalDateTime now = LocalDateTime.now();
        return popupRepository.findAllByIsActiveTrueOrderByPriorityAscIdAsc().stream()
                .filter(p -> p.isVisibleAt(now))
                .filter(p -> p.getTriggerList().contains(trigger))
                .map(PopupDto.Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PopupDto.Response findById(Long id) {
        Popup p = popupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("팝업을 찾을 수 없습니다: id=" + id));
        return PopupDto.Response.from(p);
    }

    @Transactional
    public PopupDto.Response create(PopupDto.Request req) {
        validateRequest(req);
        Popup p = Popup.builder()
                .type(req.getType())
                .title(req.getTitle())
                .content(req.getContent())
                .imageUrl(req.getImageUrl())
                .triggers(joinEnums(req.getTriggers()))
                .closeOptions(joinEnums(req.getCloseOptions()))
                .priority(req.getPriority() != null ? req.getPriority() : 0)
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .startAt(req.getStartAt())
                .endAt(req.getEndAt())
                .build();
        return PopupDto.Response.from(popupRepository.save(p));
    }

    @Transactional
    public PopupDto.Response update(Long id, PopupDto.Request req) {
        validateRequest(req);
        Popup p = popupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("팝업을 찾을 수 없습니다: id=" + id));
        p.setType(req.getType());
        p.setTitle(req.getTitle());
        p.setContent(req.getContent());
        p.setImageUrl(req.getImageUrl());
        p.setTriggers(joinEnums(req.getTriggers()));
        p.setCloseOptions(joinEnums(req.getCloseOptions()));
        if (req.getPriority() != null) p.setPriority(req.getPriority());
        if (req.getIsActive() != null) p.setIsActive(req.getIsActive());
        p.setStartAt(req.getStartAt());
        p.setEndAt(req.getEndAt());
        return PopupDto.Response.from(popupRepository.save(p));
    }

    @Transactional
    public void delete(Long id) {
        if (!popupRepository.existsById(id)) {
            throw new EntityNotFoundException("팝업을 찾을 수 없습니다: id=" + id);
        }
        popupRepository.deleteById(id);
    }

    private void validateRequest(PopupDto.Request req) {
        if (req.getType() == null) throw new IllegalArgumentException("type은 필수입니다");
        if (req.getTitle() == null || req.getTitle().isBlank())
            throw new IllegalArgumentException("title은 필수입니다");
        if (req.getTitle().length() > 100)
            throw new IllegalArgumentException("title은 100자를 초과할 수 없습니다");
        if (req.getContent() == null || req.getContent().isBlank())
            throw new IllegalArgumentException("content는 필수입니다");
        if (req.getContent().length() > 1000)
            throw new IllegalArgumentException("content는 1000자를 초과할 수 없습니다");
        if (req.getType() == Popup.Type.IMAGE_TEXT &&
                (req.getImageUrl() == null || req.getImageUrl().isBlank())) {
            throw new IllegalArgumentException("IMAGE_TEXT 타입은 imageUrl이 필수입니다");
        }
        if (req.getTriggers() == null || req.getTriggers().isEmpty())
            throw new IllegalArgumentException("triggers는 최소 1개 이상 선택해야 합니다");
        if (req.getCloseOptions() == null || req.getCloseOptions().isEmpty())
            throw new IllegalArgumentException("closeOptions는 최소 1개 이상 선택해야 합니다");
        if (req.getStartAt() != null && req.getEndAt() != null &&
                req.getEndAt().isBefore(req.getStartAt())) {
            throw new IllegalArgumentException("endAt은 startAt보다 이후여야 합니다");
        }
    }

    private <E extends Enum<E>> String joinEnums(List<E> values) {
        return values.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
