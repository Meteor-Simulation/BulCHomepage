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

    /**
     * 검색 쿼리. 빈 필터는 '' (빈 문자열)로 전달해야 한다.
     * <p>null 파라미터를 :param IS NULL 로 비교하면 PostgreSQL JDBC가
     * 타입 추론에 실패해 bytea 로 바인딩 → LOWER(bytea) 미존재 에러 발생.
     */
    @Query("""
            SELECT lc FROM LeadContact lc
             WHERE (:emailQ = '' OR LOWER(lc.email) LIKE LOWER(CONCAT('%', :emailQ, '%')))
               AND (:nameQ = '' OR LOWER(COALESCE(lc.contactName, '')) LIKE LOWER(CONCAT('%', :nameQ, '%')))
               AND (:companyQ = '' OR LOWER(COALESCE(lc.companyName, '')) LIKE LOWER(CONCAT('%', :companyQ, '%')))
               AND (:tagQ = '' OR LOWER(COALESCE(lc.tags, '')) LIKE LOWER(CONCAT('%', :tagQ, '%')))
               AND (:sourceEventQ = '' OR LOWER(COALESCE(lc.sourceEvent, '')) LIKE LOWER(CONCAT('%', :sourceEventQ, '%')))
               AND (:q = '' OR (
                     LOWER(lc.email) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(COALESCE(lc.contactName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(COALESCE(lc.companyName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               ))
               AND (:activeOnly = false OR lc.unsubscribedAt IS NULL)
               AND (:inactiveOnly = false OR lc.unsubscribedAt IS NOT NULL)
            """)
    Page<LeadContact> search(
            @Param("emailQ") String emailQ,
            @Param("nameQ") String nameQ,
            @Param("companyQ") String companyQ,
            @Param("tagQ") String tagQ,
            @Param("sourceEventQ") String sourceEventQ,
            @Param("q") String q,
            @Param("activeOnly") boolean activeOnly,
            @Param("inactiveOnly") boolean inactiveOnly,
            Pageable pageable
    );
}
