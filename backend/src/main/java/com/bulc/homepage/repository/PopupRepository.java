package com.bulc.homepage.repository;

import com.bulc.homepage.entity.Popup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PopupRepository extends JpaRepository<Popup, Long> {

    List<Popup> findAllByOrderByPriorityAscIdAsc();

    List<Popup> findAllByIsActiveTrueOrderByPriorityAscIdAsc();
}
