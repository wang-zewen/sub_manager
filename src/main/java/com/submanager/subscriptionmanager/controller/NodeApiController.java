package com.submanager.subscriptionmanager.controller;

import com.submanager.subscriptionmanager.model.ProxyNode;
import com.submanager.subscriptionmanager.model.SubscriptionGroup;
import com.submanager.subscriptionmanager.service.NodeParser;
import com.submanager.subscriptionmanager.service.SubscriptionService;
import com.submanager.subscriptionmanager.service.NodeSaveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API for managing nodes
 * Allows external systems to add nodes to groups via API
 */
@RestController
@RequestMapping("/api/v1")
public class NodeApiController {

    private static final Logger logger = LoggerFactory.getLogger(NodeApiController.class);

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private NodeParser nodeParser;

    @Autowired
    private NodeSaveService nodeSaveService;

    /**
     * Add a single node to a group
     * POST /api/v1/groups/{groupId}/nodes
     *
     * Request body:
     * {
     *   "name": "HK-Node-1",
     *   "config": "vless://uuid@server:port?..."
     * }
     *
     * Optional header: X-API-Token (for authentication)
     */
    @PostMapping("/groups/{groupId}/nodes")
    public ResponseEntity<?> addNode(
            @PathVariable Long groupId,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-API-Token", required = false) String apiToken) {

        try {
            // Validate group exists
            SubscriptionGroup group = subscriptionService.getGroupById(groupId)
                    .orElseThrow(() -> new RuntimeException("Group not found with id: " + groupId));

            // Optional: Validate API token
            // if (!validateToken(apiToken, group)) {
            //     return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            //         .body(Map.of("error", "Invalid or missing API token"));
            // }

            String config = request.get("config");
            String name = request.get("name");

            if (config == null || config.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Node config is required"));
            }

            // Create and configure node
            ProxyNode node = new ProxyNode();
            node.setConfig(config.trim());
            node.setSubscriptionGroup(group);

            // Determine node type from URL
            String lowerUrl = config.toLowerCase();
            if (lowerUrl.startsWith("vmess://")) {
                node.setType("vmess");
            } else if (lowerUrl.startsWith("vless://")) {
                node.setType("vless");
            } else if (lowerUrl.startsWith("trojan://")) {
                node.setType("trojan");
            } else if (lowerUrl.startsWith("ss://")) {
                node.setType("shadowsocks");
            } else if (lowerUrl.startsWith("hysteria://")) {
                node.setType("hysteria");
            } else if (lowerUrl.startsWith("hysteria2://") || lowerUrl.startsWith("hy2://")) {
                node.setType("hysteria2");
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Unknown node type. Supported: vmess, vless, trojan, ss, hysteria, hysteria2"));
            }

            // Parse node details
            nodeParser.parseAndPopulateNode(node);

            // Set name
            if (name != null && !name.trim().isEmpty()) {
                node.setName(name.trim());
            } else if (node.getName() == null || node.getName().isEmpty() || node.getName().equals("-")) {
                // Generate default name
                if (node.getServer() != null) {
                    node.setName(node.getServer() + ":" + node.getPort());
                } else {
                    node.setName(node.getType() + "-node-" + System.currentTimeMillis());
                }
            }

            node.setIsActive(true);

            // Save node
            if (nodeSaveService.saveNode(node)) {
                logger.info("API: Successfully added node {} to group {}", node.getName(), groupId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Node added successfully");
                response.put("node", Map.of(
                    "id", node.getId(),
                    "name", node.getName(),
                    "type", node.getType(),
                    "server", node.getServer() != null ? node.getServer() : "",
                    "port", node.getPort() != null ? node.getPort() : 0
                ));

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to save node"));
            }

        } catch (Exception e) {
            logger.error("API: Failed to add node to group {}", groupId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Batch add nodes to a group
     * POST /api/v1/groups/{groupId}/nodes/batch
     *
     * Request body:
     * {
     *   "nodes": [
     *     {"name": "Node-1", "config": "vless://..."},
     *     {"name": "Node-2", "config": "vmess://..."}
     *   ]
     * }
     */
    @PostMapping("/groups/{groupId}/nodes/batch")
    public ResponseEntity<?> addNodesBatch(
            @PathVariable Long groupId,
            @RequestBody Map<String, List<Map<String, String>>> request,
            @RequestHeader(value = "X-API-Token", required = false) String apiToken) {

        try {
            // Validate group exists
            SubscriptionGroup group = subscriptionService.getGroupById(groupId)
                    .orElseThrow(() -> new RuntimeException("Group not found with id: " + groupId));

            List<Map<String, String>> nodesList = request.get("nodes");
            if (nodesList == null || nodesList.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Nodes array is required and must not be empty"));
            }

            int successCount = 0;
            int failedCount = 0;
            List<Map<String, String>> errors = new ArrayList<>();

            for (int i = 0; i < nodesList.size(); i++) {
                Map<String, String> nodeData = nodesList.get(i);
                String config = nodeData.get("config");
                String name = nodeData.get("name");

                try {
                    if (config == null || config.trim().isEmpty()) {
                        errors.add(Map.of("index", String.valueOf(i), "error", "Config is required"));
                        failedCount++;
                        continue;
                    }

                    ProxyNode node = new ProxyNode();
                    node.setConfig(config.trim());
                    node.setSubscriptionGroup(group);

                    // Determine node type
                    String lowerUrl = config.toLowerCase();
                    if (lowerUrl.startsWith("vmess://")) {
                        node.setType("vmess");
                    } else if (lowerUrl.startsWith("vless://")) {
                        node.setType("vless");
                    } else if (lowerUrl.startsWith("trojan://")) {
                        node.setType("trojan");
                    } else if (lowerUrl.startsWith("ss://")) {
                        node.setType("shadowsocks");
                    } else if (lowerUrl.startsWith("hysteria://")) {
                        node.setType("hysteria");
                    } else if (lowerUrl.startsWith("hysteria2://") || lowerUrl.startsWith("hy2://")) {
                        node.setType("hysteria2");
                    } else {
                        errors.add(Map.of("index", String.valueOf(i), "error", "Unknown node type"));
                        failedCount++;
                        continue;
                    }

                    // Parse node
                    nodeParser.parseAndPopulateNode(node);

                    // Set name
                    if (name != null && !name.trim().isEmpty()) {
                        node.setName(name.trim());
                    } else if (node.getName() == null || node.getName().isEmpty() || node.getName().equals("-")) {
                        if (node.getServer() != null) {
                            node.setName(node.getServer() + ":" + node.getPort());
                        } else {
                            node.setName(node.getType() + "-node-" + (i + 1));
                        }
                    }

                    node.setIsActive(true);

                    if (nodeSaveService.saveNode(node)) {
                        successCount++;
                    } else {
                        errors.add(Map.of("index", String.valueOf(i), "error", "Failed to save node"));
                        failedCount++;
                    }

                } catch (Exception e) {
                    errors.add(Map.of("index", String.valueOf(i), "error", e.getMessage()));
                    failedCount++;
                }
            }

            logger.info("API: Batch add to group {}: {} succeeded, {} failed", groupId, successCount, failedCount);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total", nodesList.size());
            response.put("succeeded", successCount);
            response.put("failed", failedCount);
            if (!errors.isEmpty()) {
                response.put("errors", errors);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("API: Failed batch add to group {}", groupId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get group information
     * GET /api/v1/groups/{groupId}
     */
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<?> getGroup(@PathVariable Long groupId) {
        try {
            SubscriptionGroup group = subscriptionService.getGroupById(groupId)
                    .orElseThrow(() -> new RuntimeException("Group not found with id: " + groupId));

            Map<String, Object> response = new HashMap<>();
            response.put("id", group.getId());
            response.put("name", group.getName());
            response.put("token", group.getToken());
            response.put("description", group.getDescription());
            response.put("isActive", group.getIsActive());
            response.put("nodeCount", group.getNodes().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * List all groups
     * GET /api/v1/groups
     */
    @GetMapping("/groups")
    public ResponseEntity<?> listGroups() {
        try {
            List<SubscriptionGroup> groups = subscriptionService.getAllGroups();

            List<Map<String, Object>> groupList = new ArrayList<>();
            for (SubscriptionGroup group : groups) {
                Map<String, Object> groupData = new HashMap<>();
                groupData.put("id", group.getId());
                groupData.put("name", group.getName());
                groupData.put("token", group.getToken());
                groupData.put("isActive", group.getIsActive());
                groupData.put("nodeCount", group.getNodes().size());
                groupList.add(groupData);
            }

            return ResponseEntity.ok(Map.of("groups", groupList, "total", groupList.size()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
