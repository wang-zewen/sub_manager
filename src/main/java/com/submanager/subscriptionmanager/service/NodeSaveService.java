package com.submanager.subscriptionmanager.service;

import com.submanager.subscriptionmanager.model.ProxyNode;
import com.submanager.subscriptionmanager.model.SubscriptionGroup;
import com.submanager.subscriptionmanager.model.SubscriptionSource;
import com.submanager.subscriptionmanager.repository.ProxyNodeRepository;
import com.submanager.subscriptionmanager.repository.SubscriptionGroupRepository;
import com.submanager.subscriptionmanager.repository.SubscriptionSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Helper service to save nodes in separate transactions
 * This is needed to avoid transaction rollback issues when parsing fails
 */
@Service
public class NodeSaveService {

    private static final Logger logger = LoggerFactory.getLogger(NodeSaveService.class);

    @Autowired
    private ProxyNodeRepository proxyNodeRepository;

    @Autowired
    private SubscriptionSourceRepository subscriptionSourceRepository;

    @Autowired
    private SubscriptionGroupRepository subscriptionGroupRepository;

    /**
     * Save a single node in a separate transaction
     * This method also ensures the bidirectional relationship is maintained
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveNode(ProxyNode node) {
        try {
            // Save the node (this will persist the foreign key relationship)
            ProxyNode savedNode = proxyNodeRepository.save(node);

            // Maintain bidirectional relationship by adding node to group's collection
            if (savedNode.getSubscriptionGroup() != null) {
                SubscriptionGroup group = savedNode.getSubscriptionGroup();
                // Only add if not already in the list (to avoid duplicates)
                if (!group.getNodes().contains(savedNode)) {
                    group.getNodes().add(savedNode);
                }
            }

            logger.debug("Successfully saved node: {}", node.getName());
            return true;
        } catch (Exception e) {
            logger.error("Failed to save node: {}", node.getConfig(), e);
            return false;
        }
    }

    /**
     * Update subscription source status in a separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSubscriptionSourceStatus(Long sourceId, String status, String errorMessage,
                                               Integer nodeCount, LocalDateTime lastUpdated) {
        try {
            SubscriptionSource source = subscriptionSourceRepository.findById(sourceId).orElse(null);
            if (source != null) {
                source.setLastUpdateStatus(status);
                source.setLastErrorMessage(errorMessage);
                source.setNodeCount(nodeCount);
                source.setLastUpdated(lastUpdated);
                subscriptionSourceRepository.save(source);
                logger.debug("Updated subscription source status: {}", status);
            }
        } catch (Exception e) {
            logger.error("Failed to update subscription source status", e);
        }
    }
}
