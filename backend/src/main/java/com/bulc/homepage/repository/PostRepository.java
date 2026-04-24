package com.bulc.homepage.repository;

import com.bulc.homepage.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByIsDeletedFalseOrderBySortOrderAscCreatedAtDesc(Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.isDeleted = false " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')))" +
           "ORDER BY p.createdAt DESC")
    Page<Post> searchByTitle(@Param("search") String search, Pageable pageable);
}
