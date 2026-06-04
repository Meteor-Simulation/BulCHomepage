package com.bulc.homepage.repository;

import com.bulc.homepage.entity.LeadContact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LeadContactRepository extends JpaRepository<LeadContact, Long> {

    Optional<LeadContact> findByEmail(String email);

    Optional<LeadContact> findByUnsubscribeToken(UUID token);

    boolean existsByEmail(String email);

    @Query("""
            SELECT lc FROM LeadContact lc
             WHERE (:emailQ IS NULL OR LOWER(lc.email) LIKE LOWER(CONCAT('%', :emailQ, '%')))
               AND (:nameQ IS NULL OR LOWER(lc.contactName) LIKE LOWER(CONCAT('%', :nameQ, '%')))
               AND (:companyQ IS NULL OR LOWER(lc.companyName) LIKE LOWER(CONCAT('%', :companyQ, '%')))
               AND (:tagQ IS NULL OR LOWER(lc.tags) LIKE LOWER(CONCAT('%', :tagQ, '%')))
               AND (:sourceEventQ IS NULL OR LOWER(lc.sourceEvent) LIKE LOWER(CONCAT('%', :sourceEventQ, '%')))
               AND (:activeOnly = false OR lc.unsubscribedAt IS NULL)
            """)
    Page<LeadContact> search(
            @Param("emailQ") String emailQ,
            @Param("nameQ") String nameQ,
            @Param("companyQ") String companyQ,
            @Param("tagQ") String tagQ,
            @Param("sourceEventQ") String sourceEventQ,
            @Param("activeOnly") boolean activeOnly,
            Pageable pageable
    );
}
