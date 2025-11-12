package com.submanager.subscriptionmanager.repository;

import com.submanager.subscriptionmanager.model.ProxyNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProxyNodeRepository extends JpaRepository<ProxyNode, Long> {
    List<ProxyNode> findBySubscriptionGroupIdOrderByOrderAsc(Long groupId);
    List<ProxyNode> findBySubscriptionGroupIdAndIsActiveTrueOrderByOrderAsc(Long groupId);
}
