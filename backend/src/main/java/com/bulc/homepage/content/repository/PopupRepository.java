package com.bulc.homepage.content.repository;

import com.bulc.homepage.content.domain.Popup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PopupRepository extends JpaRepository<Popup, Long> {

    List<Popup> findAllByOrderByPriorityAscIdAsc();

    List<Popup> findAllByIsActiveTrueOrderByPriorityAscIdAsc();
}
