package com.submanager.subscriptionmanager.repository;

import com.submanager.subscriptionmanager.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByIsActiveOrderByCreatedAtDesc(Boolean isActive);
    List<Subscription> findAllByOrderByCreatedAtDesc();
}
