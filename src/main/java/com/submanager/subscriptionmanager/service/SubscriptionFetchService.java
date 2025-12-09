package com.submanager.subscriptionmanager.service;

import com.submanager.subscriptionmanager.model.ProxyNode;
import com.submanager.subscriptionmanager.model.SubscriptionGroup;
import com.submanager.subscriptionmanager.model.SubscriptionSource;
import com.submanager.subscriptionmanager.repository.ProxyNodeRepository;
import com.submanager.subscriptionmanager.repository.SubscriptionSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubscriptionFetchService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionFetchService.class);
    private static final int CONNECTION_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 15000; // 15 seconds

    @Autowired
    private SubscriptionSourceRepository subscriptionSourceRepository;

    @Autowired
    private ProxyNodeRepository proxyNodeRepository;

    @Autowired
    private NodeParser nodeParser;

    @Autowired
    private NodeSaveService nodeSaveService;

    /**
     * Fetch subscription content from URL
     */
    public String fetchSubscriptionContent(String subscriptionUrl) throws Exception {
        logger.info("Fetching subscription from: {}", subscriptionUrl);

        URL url = new URL(subscriptionUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("HTTP error code: " + responseCode);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Parse subscription content to node URLs
     */
    public List<String> parseSubscriptionContent(String content) {
        List<String> nodeUrls = new ArrayList<>();

        try {
            // Try to decode as Base64 first
            String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(content.trim()), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                // If not Base64, use as is
                decoded = content;
            }

            // Split by newlines and filter valid node URLs
            String[] lines = decoded.split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && isValidNodeUrl(line)) {
                    nodeUrls.add(line);
                }
            }

            logger.info("Parsed {} nodes from subscription content", nodeUrls.size());
        } catch (Exception e) {
            logger.error("Error parsing subscription content", e);
        }

        return nodeUrls;
    }

    /**
     * Check if a string is a valid node URL
     */
    private boolean isValidNodeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        String lowerUrl = url.toLowerCase();
        return lowerUrl.startsWith("vmess://") ||
               lowerUrl.startsWith("vless://") ||
               lowerUrl.startsWith("trojan://") ||
               lowerUrl.startsWith("ss://") ||
               lowerUrl.startsWith("hysteria://") ||
               lowerUrl.startsWith("hysteria2://") ||
               lowerUrl.startsWith("hy2://");
    }

    /**
     * Update nodes from a subscription source
     */
    public int updateNodesFromSubscription(Long subscriptionSourceId) {
        logger.info("Updating nodes from subscription source: {}", subscriptionSourceId);

        SubscriptionSource source = subscriptionSourceRepository.findById(subscriptionSourceId)
                .orElseThrow(() -> new RuntimeException("Subscription source not found"));

        try {
            // Fetch subscription content
            String content = fetchSubscriptionContent(source.getUrl());

            // Parse node URLs
            List<String> nodeUrls = parseSubscriptionContent(content);

            if (nodeUrls.isEmpty()) {
                logger.warn("No valid nodes found in subscription: {}", source.getUrl());
                nodeSaveService.updateSubscriptionSourceStatus(subscriptionSourceId, "SUCCESS",
                    "No nodes found in subscription", 0, LocalDateTime.now());
                return 0;
            }

            SubscriptionGroup group = source.getSubscriptionGroup();
            int addedCount = 0;
            int failedCount = 0;

            for (int i = 0; i < nodeUrls.size(); i++) {
                String nodeUrl = nodeUrls.get(i);
                try {
                    ProxyNode node = new ProxyNode();
                    node.setConfig(nodeUrl);
                    node.setSubscriptionGroup(group);

                    // Try to parse the node
                    nodeParser.parseAndPopulateNode(node);

                    // Generate a name if not parsed
                    if (node.getName() == null || node.getName().isEmpty()) {
                        if (node.getServer() != null && node.getPort() != null) {
                            node.setName(node.getServer() + ":" + node.getPort());
                        } else {
                            node.setName(node.getType() + "-node-" + (i + 1));
                        }
                    }

                    node.setIsActive(true);

                    // Save in a separate transaction to avoid rollback issues
                    if (nodeSaveService.saveNode(node)) {
                        addedCount++;
                    } else {
                        failedCount++;
                    }
                } catch (Exception e) {
                    logger.error("Failed to parse node: {}", nodeUrl, e);
                    failedCount++;
                }
            }

            // Update subscription source status in a separate transaction
            nodeSaveService.updateSubscriptionSourceStatus(subscriptionSourceId, "SUCCESS", null,
                addedCount, LocalDateTime.now());

            logger.info("Successfully added {} nodes, failed: {}", addedCount, failedCount);
            return addedCount;

        } catch (Exception e) {
            logger.error("Error updating subscription source: {}", subscriptionSourceId, e);

            // Update subscription source with error in a separate transaction
            nodeSaveService.updateSubscriptionSourceStatus(subscriptionSourceId, "FAILED",
                e.getMessage(), 0, LocalDateTime.now());

            throw new RuntimeException("Failed to update subscription: " + e.getMessage(), e);
        }
    }

    /**
     * Update all subscription sources that need updating
     */
    public void updateAllDueSubscriptions() {
        logger.info("Starting automatic subscription update check");

        List<SubscriptionSource> sources = subscriptionSourceRepository.findByAutoUpdateTrueAndIsActiveTrue();

        for (SubscriptionSource source : sources) {
            try {
                // Check if update is due
                if (source.getLastUpdated() == null) {
                    // Never updated, update now
                    updateNodesFromSubscription(source.getId());
                } else {
                    LocalDateTime nextUpdate = source.getLastUpdated()
                            .plusHours(source.getUpdateInterval());

                    if (LocalDateTime.now().isAfter(nextUpdate)) {
                        logger.info("Subscription source {} is due for update", source.getId());
                        updateNodesFromSubscription(source.getId());
                    }
                }
            } catch (Exception e) {
                logger.error("Error updating subscription source {}", source.getId(), e);
            }
        }

        logger.info("Finished automatic subscription update check");
    }
}
