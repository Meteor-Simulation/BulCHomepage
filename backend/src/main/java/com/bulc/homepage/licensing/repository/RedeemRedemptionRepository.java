package com.bulc.homepage.licensing.repository;

import com.bulc.homepage.licensing.domain.RedeemRedemption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RedeemRedemptionRepository extends JpaRepository<RedeemRedemption, UUID> {

    Page<RedeemRedemption> findByCampaignId(UUID campaignId, Pageable pageable);

    List<RedeemRedemption> findByUserId(UUID userId);
}
