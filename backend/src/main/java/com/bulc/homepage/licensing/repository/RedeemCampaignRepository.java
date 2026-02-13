package com.bulc.homepage.licensing.repository;

import com.bulc.homepage.licensing.domain.RedeemCampaign;
import com.bulc.homepage.licensing.domain.RedeemCampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RedeemCampaignRepository extends JpaRepository<RedeemCampaign, UUID> {

    Page<RedeemCampaign> findAll(Pageable pageable);

    Page<RedeemCampaign> findByStatus(RedeemCampaignStatus status, Pageable pageable);

    @Modifying
    @Query(value = "UPDATE redeem_campaigns SET seats_used = seats_used + 1, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = :campaignId AND (seat_limit IS NULL OR seats_used < seat_limit)",
            nativeQuery = true)
    int incrementSeatsUsedAtomically(@Param("campaignId") UUID campaignId);
}
