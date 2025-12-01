package com.submanager.subscriptionmanager.service;

import com.submanager.subscriptionmanager.model.ProxyNode;
import com.submanager.subscriptionmanager.model.SubscriptionGroup;
import com.submanager.subscriptionmanager.repository.SubscriptionGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionAutoUpdateService {

    private final SubscriptionGroupRepository groupRepository;
    private final SubscriptionService subscriptionService;
    private final NodeParser nodeParser;

    /**
     * Scheduled task to auto-update subscriptions
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * ?") // Every hour at :00
    public void autoUpdateSubscriptions() {
        log.info("Starting auto-update task for subscriptions");

        List<SubscriptionGroup> groups = groupRepository.findAll();
        int updatedCount = 0;
        int failedCount = 0;

        for (SubscriptionGroup group : groups) {
            if (shouldUpdate(group)) {
                try {
                    updateGroupFromSource(group.getId());
                    updatedCount++;
                    log.info("Successfully updated group: {} (ID: {})", group.getName(), group.getId());
                } catch (Exception e) {
                    failedCount++;
                    log.error("Failed to update group: {} (ID: {}) - {}",
                             group.getName(), group.getId(), e.getMessage());
                }
            }
        }

        log.info("Auto-update task completed. Updated: {}, Failed: {}", updatedCount, failedCount);
    }

    /**
     * Check if group should be updated
     */
    private boolean shouldUpdate(SubscriptionGroup group) {
        // Check if auto-update is enabled
        if (group.getAutoUpdateEnabled() == null || !group.getAutoUpdateEnabled()) {
            return false;
        }

        // Check if source URL is configured
        if (group.getSourceUrl() == null || group.getSourceUrl().trim().isEmpty()) {
            return false;
        }

        // Check if enough time has passed since last update
        if (group.getLastUpdateTime() == null) {
            return true; // Never updated before
        }

        LocalDateTime nextUpdateTime = group.getLastUpdateTime()
                .plusHours(group.getUpdateIntervalHours() != null ? group.getUpdateIntervalHours() : 24);

        return LocalDateTime.now().isAfter(nextUpdateTime);
    }

    /**
     * Update group from upstream source
     */
    @Transactional
    public void updateGroupFromSource(Long groupId) throws Exception {
        SubscriptionGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found: " + groupId));

        if (group.getSourceUrl() == null || group.getSourceUrl().trim().isEmpty()) {
            throw new RuntimeException("No source URL configured");
        }

        log.info("Fetching subscription from: {}", group.getSourceUrl());

        // Fetch content from upstream
        String content = fetchSubscriptionContent(group.getSourceUrl());

        // Parse and import nodes
        List<ProxyNode> newNodes = parseSubscriptionContent(content, group);

        if (newNodes.isEmpty()) {
            throw new RuntimeException("No valid nodes found in subscription");
        }

        // Clear existing nodes
        List<ProxyNode> existingNodes = new ArrayList<>(group.getNodes());
        for (ProxyNode node : existingNodes) {
            subscriptionService.deleteNode(node.getId());
        }

        // Add new nodes
        int successCount = 0;
        for (ProxyNode node : newNodes) {
            try {
                subscriptionService.createNode(node);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to import node: {}", node.getName(), e);
            }
        }

        // Update group status
        group.setLastUpdateTime(LocalDateTime.now());
        group.setLastUpdateStatus("SUCCESS");
        groupRepository.save(group);

        log.info("Updated group {} with {} nodes (success: {})",
                group.getName(), newNodes.size(), successCount);
    }

    /**
     * Fetch subscription content from URL
     */
    private String fetchSubscriptionContent(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("HTTP error code: " + responseCode);
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        return content.toString();
    }

    /**
     * Parse subscription content and create node list
     */
    private List<ProxyNode> parseSubscriptionContent(String content, SubscriptionGroup group) {
        List<ProxyNode> nodes = new ArrayList<>();

        // Try to decode as Base64
        String decodedContent = content.trim();
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(content.trim());
            decodedContent = new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Not Base64, use original content
        }

        // Split by newlines and parse each line
        String[] lines = decodedContent.split("\\r?\\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Check if line is a valid node URL
            if (line.startsWith("vmess://") || line.startsWith("vless://") ||
                line.startsWith("trojan://") || line.startsWith("ss://") ||
                line.startsWith("hysteria://") || line.startsWith("hysteria2://")) {

                try {
                    ProxyNode node = new ProxyNode();
                    node.setConfig(line);
                    node.setSubscriptionGroup(group);

                    // Determine node type
                    if (line.startsWith("vmess://")) {
                        node.setType("vmess");
                    } else if (line.startsWith("vless://")) {
                        node.setType("vless");
                    } else if (line.startsWith("trojan://")) {
                        node.setType("trojan");
                    } else if (line.startsWith("ss://")) {
                        node.setType("shadowsocks");
                    } else if (line.startsWith("hysteria://")) {
                        node.setType("hysteria");
                    } else if (line.startsWith("hysteria2://")) {
                        node.setType("hysteria2");
                    }

                    // Parse node details
                    nodeParser.parseAndPopulateNode(node);

                    // Ensure node has a name
                    if (node.getName() == null || node.getName().isEmpty()) {
                        if (node.getServer() != null && !node.getServer().isEmpty()) {
                            node.setName(node.getServer() + ":" + node.getPort());
                        } else {
                            node.setName(node.getType() + "-node");
                        }
                    }

                    nodes.add(node);
                } catch (Exception e) {
                    log.error("Failed to parse node: {}", line, e);
                }
            }
        }

        return nodes;
    }

    /**
     * Manual trigger for updating a specific group
     */
    public void triggerManualUpdate(Long groupId) throws Exception {
        SubscriptionGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        try {
            updateGroupFromSource(groupId);
        } catch (Exception e) {
            group.setLastUpdateTime(LocalDateTime.now());
            group.setLastUpdateStatus("FAILED");
            groupRepository.save(group);
            throw e;
        }
    }
}
