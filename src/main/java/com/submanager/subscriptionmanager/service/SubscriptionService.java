package com.submanager.subscriptionmanager.service;

import com.submanager.subscriptionmanager.model.ProxyNode;
import com.submanager.subscriptionmanager.model.SubscriptionGroup;
import com.submanager.subscriptionmanager.repository.ProxyNodeRepository;
import com.submanager.subscriptionmanager.repository.SubscriptionGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class SubscriptionService {

    @Autowired
    private SubscriptionGroupRepository groupRepository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    // Subscription Group methods
    public List<SubscriptionGroup> getAllGroups() {
        return groupRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<SubscriptionGroup> getGroupById(Long id) {
        return groupRepository.findById(id);
    }

    public Optional<SubscriptionGroup> getGroupByToken(String token) {
        return groupRepository.findByToken(token);
    }

    public SubscriptionGroup createGroup(SubscriptionGroup group) {
        return groupRepository.save(group);
    }

    public SubscriptionGroup updateGroup(Long id, SubscriptionGroup group) {
        group.setId(id);
        return groupRepository.save(group);
    }

    public void deleteGroup(Long id) {
        groupRepository.deleteById(id);
    }

    // ProxyNode methods
    public List<ProxyNode> getNodesByGroupId(Long groupId) {
        return nodeRepository.findBySubscriptionGroupIdOrderByOrderAsc(groupId);
    }

    public List<ProxyNode> getActiveNodesByGroupId(Long groupId) {
        return nodeRepository.findBySubscriptionGroupIdAndIsActiveTrueOrderByOrderAsc(groupId);
    }

    public Optional<ProxyNode> getNodeById(Long id) {
        return nodeRepository.findById(id);
    }

    public ProxyNode createNode(ProxyNode node) {
        return nodeRepository.save(node);
    }

    public ProxyNode updateNode(Long id, ProxyNode node) {
        node.setId(id);
        return nodeRepository.save(node);
    }

    public void deleteNode(Long id) {
        nodeRepository.deleteById(id);
    }

    // Subscription content generation
    public String generateSubscriptionContent(String token) {
        Optional<SubscriptionGroup> groupOpt = groupRepository.findByToken(token);

        if (groupOpt.isEmpty() || !groupOpt.get().getIsActive()) {
            return "";
        }

        SubscriptionGroup group = groupOpt.get();
        List<ProxyNode> activeNodes = nodeRepository.findBySubscriptionGroupIdAndIsActiveTrueOrderByOrderAsc(group.getId());

        if (activeNodes.isEmpty()) {
            return "";
        }

        // Collect all node configs (vmess://, vless://, etc.)
        String content = activeNodes.stream()
                .map(ProxyNode::getConfig)
                .collect(Collectors.joining("\n"));

        // Encode to base64
        return Base64.getEncoder().encodeToString(content.getBytes());
    }

    public String generateRawSubscriptionContent(String token) {
        Optional<SubscriptionGroup> groupOpt = groupRepository.findByToken(token);

        if (groupOpt.isEmpty() || !groupOpt.get().getIsActive()) {
            return "";
        }

        SubscriptionGroup group = groupOpt.get();
        List<ProxyNode> activeNodes = nodeRepository.findBySubscriptionGroupIdAndIsActiveTrueOrderByOrderAsc(group.getId());

        return activeNodes.stream()
                .map(ProxyNode::getConfig)
                .collect(Collectors.joining("\n"));
    }
}
