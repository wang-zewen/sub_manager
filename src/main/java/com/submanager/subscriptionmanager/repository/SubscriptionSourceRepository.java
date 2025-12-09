package com.submanager.subscriptionmanager.repository;

import com.submanager.subscriptionmanager.model.SubscriptionSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SubscriptionSourceRepository extends JpaRepository<SubscriptionSource, Long> {
    List<SubscriptionSource> findBySubscriptionGroupId(Long groupId);

    List<SubscriptionSource> findByIsActiveTrue();

    List<SubscriptionSource> findByAutoUpdateTrueAndIsActiveTrue();

    List<SubscriptionSource> findByAutoUpdateTrueAndIsActiveTrueAndLastUpdatedBefore(LocalDateTime dateTime);
}
