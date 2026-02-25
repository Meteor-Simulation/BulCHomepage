package com.bulc.homepage.licensing.repository;

import com.bulc.homepage.licensing.domain.RedeemCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedeemCodeRepository extends JpaRepository<RedeemCode, UUID> {

    Optional<RedeemCode> findByCodeHash(String codeHash);

    Page<RedeemCode> findByCampaignId(UUID campaignId, Pageable pageable);

    long countByCampaignId(UUID campaignId);

    boolean existsByCodeHash(String codeHash);

    @Modifying
    @Query(value = "UPDATE redeem_codes SET current_redemptions = current_redemptions + 1, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = :codeId AND current_redemptions < max_redemptions AND is_active = true",
            nativeQuery = true)
    int incrementRedemptionsAtomically(@Param("codeId") UUID codeId);
}
