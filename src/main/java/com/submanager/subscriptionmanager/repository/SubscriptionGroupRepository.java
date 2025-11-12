package com.submanager.subscriptionmanager.repository;

import com.submanager.subscriptionmanager.model.SubscriptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionGroupRepository extends JpaRepository<SubscriptionGroup, Long> {
    Optional<SubscriptionGroup> findByToken(String token);
    List<SubscriptionGroup> findAllByOrderByCreatedAtDesc();
    List<SubscriptionGroup> findByIsActiveOrderByCreatedAtDesc(Boolean isActive);
}
