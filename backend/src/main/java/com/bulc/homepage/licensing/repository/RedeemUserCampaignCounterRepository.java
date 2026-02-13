package com.bulc.homepage.licensing.repository;

import com.bulc.homepage.licensing.domain.RedeemUserCampaignCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedeemUserCampaignCounterRepository extends JpaRepository<RedeemUserCampaignCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RedeemUserCampaignCounter> findByUserIdAndCampaignId(UUID userId, UUID campaignId);
}
