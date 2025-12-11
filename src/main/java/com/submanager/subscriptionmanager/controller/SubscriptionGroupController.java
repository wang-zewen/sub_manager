package com.submanager.subscriptionmanager.controller;

import com.submanager.subscriptionmanager.model.ProxyNode;
import com.submanager.subscriptionmanager.model.SubscriptionGroup;
import com.submanager.subscriptionmanager.model.SubscriptionSource;
import com.submanager.subscriptionmanager.repository.SubscriptionSourceRepository;
import com.submanager.subscriptionmanager.service.NodeParser;
import com.submanager.subscriptionmanager.service.NodeHealthCheckService;
import com.submanager.subscriptionmanager.service.SubscriptionService;
import com.submanager.subscriptionmanager.service.SubscriptionFetchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/groups")
public class SubscriptionGroupController {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private NodeParser nodeParser;

    @Autowired
    private NodeHealthCheckService healthCheckService;

    @Autowired
    private SubscriptionSourceRepository subscriptionSourceRepository;

    @Autowired
    private SubscriptionFetchService subscriptionFetchService;

    @GetMapping
    public String listGroups(Model model, HttpServletRequest request) {
        List<SubscriptionGroup> groups = subscriptionService.getAllGroups();
        String baseUrl = getBaseUrl(request);

        model.addAttribute("groups", groups);
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("group", new SubscriptionGroup());
        return "groups";
    }

    @PostMapping
    public String createGroup(@Valid @ModelAttribute("group") SubscriptionGroup group,
                             BindingResult result,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fill all required fields");
            return "redirect:/groups";
        }

        // Validate custom token if provided
        if (group.getToken() != null && !group.getToken().trim().isEmpty()) {
            String token = group.getToken().trim();

            // Validate token format: only alphanumeric and hyphens
            if (!token.matches("^[a-zA-Z0-9-_]+$")) {
                redirectAttributes.addFlashAttribute("error", "Token can only contain letters, numbers, hyphens, and underscores");
                return "redirect:/groups";
            }

            // Validate token length
            if (token.length() < 4 || token.length() > 50) {
                redirectAttributes.addFlashAttribute("error", "Token must be between 4 and 50 characters");
                return "redirect:/groups";
            }

            // Check if token already exists
            if (subscriptionService.getGroupByToken(token).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Token already exists. Please choose a different token");
                return "redirect:/groups";
            }

            group.setToken(token);
        }

        subscriptionService.createGroup(group);
        redirectAttributes.addFlashAttribute("success", "Subscription group created successfully");
        return "redirect:/groups";
    }

    @PostMapping("/{id}/delete")
    public String deleteGroup(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        subscriptionService.deleteGroup(id);
        redirectAttributes.addFlashAttribute("success", "Subscription group deleted successfully");
        return "redirect:/groups";
    }

    @GetMapping("/{id}/nodes")
    public String manageNodes(@PathVariable Long id, Model model, HttpServletRequest request) {
        SubscriptionGroup group = subscriptionService.getGroupById(id)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        List<ProxyNode> nodes = subscriptionService.getNodesByGroupId(id);
        List<SubscriptionSource> subscriptionSources = subscriptionSourceRepository.findBySubscriptionGroupId(id);
        String baseUrl = getBaseUrl(request);

        model.addAttribute("group", group);
        model.addAttribute("nodes", nodes);
        model.addAttribute("node", new ProxyNode());
        model.addAttribute("subscriptionSources", subscriptionSources);
        model.addAttribute("subscriptionSource", new SubscriptionSource());
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("subscriptionUrl", baseUrl + "/sub/" + group.getToken());
        return "nodes";
    }

    @PostMapping("/{groupId}/nodes")
    public String createNode(@PathVariable Long groupId,
                            @Valid @ModelAttribute("node") ProxyNode node,
                            BindingResult result,
                            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fill all required fields");
            return "redirect:/groups/" + groupId + "/nodes";
        }

        SubscriptionGroup group = subscriptionService.getGroupById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        node.setSubscriptionGroup(group);

        // Parse node URL to extract detailed information
        nodeParser.parseAndPopulateNode(node);

        subscriptionService.createNode(node);

        redirectAttributes.addFlashAttribute("success", "Node added successfully");
        return "redirect:/groups/" + groupId + "/nodes";
    }

    @PostMapping("/{groupId}/nodes/batch-import")
    public String batchImportNodes(@PathVariable Long groupId,
                                   @RequestParam("subscriptionContent") String subscriptionContent,
                                   RedirectAttributes redirectAttributes) {
        if (subscriptionContent == null || subscriptionContent.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Subscription content is empty");
            return "redirect:/groups/" + groupId + "/nodes";
        }

        SubscriptionGroup group = subscriptionService.getGroupById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        try {
            // Try to decode as Base64 first
            String decodedContent = subscriptionContent.trim();
            try {
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(subscriptionContent.trim());
                decodedContent = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                // Not Base64, use original content
            }

            // Split by newlines and filter valid node URLs
            String[] lines = decodedContent.split("\\r?\\n");
            int successCount = 0;
            int errorCount = 0;

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

                        // Determine node type from URL
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

                        // Parse node URL to extract detailed information
                        nodeParser.parseAndPopulateNode(node);

                        // Ensure node has a name (fallback to server:port or type+index)
                        if (node.getName() == null || node.getName().isEmpty()) {
                            if (node.getServer() != null && !node.getServer().isEmpty()) {
                                node.setName(node.getServer() + ":" + node.getPort());
                            } else {
                                node.setName(node.getType() + "-node-" + (successCount + 1));
                            }
                        }

                        subscriptionService.createNode(node);
                        successCount++;
                    } catch (Exception e) {
                        System.err.println("Failed to import node: " + line + " - " + e.getMessage());
                        errorCount++;
                    }
                }
            }

            if (successCount > 0) {
                redirectAttributes.addFlashAttribute("success",
                    "Successfully imported " + successCount + " node(s)" +
                    (errorCount > 0 ? " (" + errorCount + " failed)" : ""));
            } else {
                redirectAttributes.addFlashAttribute("error", "No valid nodes found in the content");
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to import nodes: " + e.getMessage());
        }

        return "redirect:/groups/" + groupId + "/nodes";
    }

    @GetMapping("/nodes/{id}/edit")
    public String editNodeForm(@PathVariable Long id, Model model, HttpServletRequest request) {
        ProxyNode node = subscriptionService.getNodeById(id)
                .orElseThrow(() -> new RuntimeException("Node not found"));

        SubscriptionGroup group = node.getSubscriptionGroup();
        List<ProxyNode> nodes = subscriptionService.getNodesByGroupId(group.getId());
        String baseUrl = getBaseUrl(request);

        model.addAttribute("group", group);
        model.addAttribute("nodes", nodes);
        model.addAttribute("node", node);
        model.addAttribute("editMode", true);
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("subscriptionUrl", baseUrl + "/sub/" + group.getToken());
        return "nodes";
    }

    @PostMapping("/nodes/{id}/update")
    public String updateNode(@PathVariable Long id,
                            @Valid @ModelAttribute("node") ProxyNode node,
                            BindingResult result,
                            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fill all required fields");
            ProxyNode existingNode = subscriptionService.getNodeById(id)
                    .orElseThrow(() -> new RuntimeException("Node not found"));
            return "redirect:/groups/" + existingNode.getSubscriptionGroup().getId() + "/nodes";
        }

        ProxyNode existingNode = subscriptionService.getNodeById(id)
                .orElseThrow(() -> new RuntimeException("Node not found"));

        Long groupId = existingNode.getSubscriptionGroup().getId();

        // Update fields
        existingNode.setName(node.getName());
        existingNode.setType(node.getType());
        existingNode.setConfig(node.getConfig());
        existingNode.setServer(node.getServer());
        existingNode.setPort(node.getPort());
        existingNode.setUuid(node.getUuid());
        existingNode.setAlterId(node.getAlterId());
        existingNode.setCipher(node.getCipher());
        existingNode.setNetwork(node.getNetwork());
        existingNode.setTls(node.getTls());
        existingNode.setSni(node.getSni());
        existingNode.setHost(node.getHost());
        existingNode.setPath(node.getPath());
        existingNode.setIsActive(node.getIsActive());

        // Reality protocol fields
        existingNode.setSecurity(node.getSecurity());
        existingNode.setFlow(node.getFlow());
        existingNode.setPublicKey(node.getPublicKey());
        existingNode.setShortId(node.getShortId());
        existingNode.setFingerprint(node.getFingerprint());

        subscriptionService.updateNode(id, existingNode);

        redirectAttributes.addFlashAttribute("success", "Node updated successfully");
        return "redirect:/groups/" + groupId + "/nodes";
    }

    @PostMapping("/nodes/{id}/delete")
    public String deleteNode(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        ProxyNode node = subscriptionService.getNodeById(id)
                .orElseThrow(() -> new RuntimeException("Node not found"));

        Long groupId = node.getSubscriptionGroup().getId();
        subscriptionService.deleteNode(id);

        redirectAttributes.addFlashAttribute("success", "Node deleted successfully");
        return "redirect:/groups/" + groupId + "/nodes";
    }

    @PostMapping("/nodes/batch-delete")
    public String batchDeleteNodes(@RequestParam("nodeIds") List<Long> nodeIds,
                                   @RequestParam("groupId") Long groupId,
                                   RedirectAttributes redirectAttributes) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No nodes selected");
            return "redirect:/groups/" + groupId + "/nodes";
        }

        int deletedCount = 0;
        for (Long nodeId : nodeIds) {
            try {
                subscriptionService.deleteNode(nodeId);
                deletedCount++;
            } catch (Exception e) {
                System.err.println("Failed to delete node " + nodeId + ": " + e.getMessage());
            }
        }

        if (deletedCount > 0) {
            redirectAttributes.addFlashAttribute("success",
                "Successfully deleted " + deletedCount + " node(s)");
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to delete nodes");
        }

        return "redirect:/groups/" + groupId + "/nodes";
    }

    @PostMapping("/{groupId}/nodes/check-health")
    public String checkGroupNodesHealth(@PathVariable Long groupId,
                                       RedirectAttributes redirectAttributes) {
        try {
            healthCheckService.checkGroupNodesHealthAsync(groupId);
            redirectAttributes.addFlashAttribute("success",
                "Health check started. Please refresh the page in a few seconds to see the results.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to start health check: " + e.getMessage());
        }

        return "redirect:/groups/" + groupId + "/nodes";
    }

    @PostMapping("/nodes/{id}/check-health")
    public String checkNodeHealth(@PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {
        ProxyNode node = subscriptionService.getNodeById(id)
                .orElseThrow(() -> new RuntimeException("Node not found"));

        Long groupId = node.getSubscriptionGroup().getId();

        try {
            healthCheckService.checkNodeHealthAsync(id);
            redirectAttributes.addFlashAttribute("success",
                "Health check started for node: " + node.getName());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to check node health: " + e.getMessage());
        }

        return "redirect:/groups/" + groupId + "/nodes";
    }

    @PostMapping("/{groupId}/subscription-sources")
    public String addSubscriptionSource(@PathVariable Long groupId,
                                       @RequestParam("url") String url,
                                       @RequestParam("name") String name,
                                       @RequestParam(value = "autoUpdate", required = false, defaultValue = "true") Boolean autoUpdate,
                                       @RequestParam(value = "updateInterval", required = false, defaultValue = "24") Integer updateInterval,
                                       RedirectAttributes redirectAttributes) {
        try {
            SubscriptionGroup group = subscriptionService.getGroupById(groupId)
                    .orElseThrow(() -> new RuntimeException("Group not found"));

            SubscriptionSource source = new SubscriptionSource();
            source.setUrl(url);
            source.setName(name);
            source.setAutoUpdate(autoUpdate);
            source.setUpdateInterval(updateInterval);
            source.setSubscriptionGroup(group);
            source.setIsActive(true);
            source.setLastUpdateStatus("PENDING");

            subscriptionSourceRepository.save(source);

            redirectAttributes.addFlashAttribute("success", "Subscription source added successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to add subscription source: " + e.getMessage());
        }

        return "redirect:/groups/" + groupId + "/nodes";
    }

    @PostMapping("/subscription-sources/{id}/delete")
    public String deleteSubscriptionSource(@PathVariable Long id,
                                          RedirectAttributes redirectAttributes) {
        try {
            SubscriptionSource source = subscriptionSourceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Subscription source not found"));

            Long groupId = source.getSubscriptionGroup().getId();

            subscriptionSourceRepository.delete(source);

            redirectAttributes.addFlashAttribute("success", "Subscription source deleted successfully");
            return "redirect:/groups/" + groupId + "/nodes";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete subscription source: " + e.getMessage());
            return "redirect:/groups";
        }
    }

    @PostMapping("/subscription-sources/{id}/refresh")
    public String refreshSubscriptionSource(@PathVariable Long id,
                                           RedirectAttributes redirectAttributes) {
        try {
            SubscriptionSource source = subscriptionSourceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Subscription source not found"));

            Long groupId = source.getSubscriptionGroup().getId();

            int addedCount = subscriptionFetchService.updateNodesFromSubscription(id);

            redirectAttributes.addFlashAttribute("success",
                "Subscription refreshed successfully. Added " + addedCount + " node(s)");

            return "redirect:/groups/" + groupId + "/nodes";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to refresh subscription: " + e.getMessage());

            // Try to get group ID from error context
            try {
                SubscriptionSource source = subscriptionSourceRepository.findById(id).orElse(null);
                if (source != null) {
                    return "redirect:/groups/" + source.getSubscriptionGroup().getId() + "/nodes";
                }
            } catch (Exception ignored) {}

            return "redirect:/groups";
        }
    }

    @PostMapping("/subscription-sources/{id}/toggle")
    public String toggleSubscriptionSource(@PathVariable Long id,
                                          RedirectAttributes redirectAttributes) {
        try {
            SubscriptionSource source = subscriptionSourceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Subscription source not found"));

            Long groupId = source.getSubscriptionGroup().getId();

            source.setIsActive(!source.getIsActive());
            subscriptionSourceRepository.save(source);

            redirectAttributes.addFlashAttribute("success",
                "Subscription source " + (source.getIsActive() ? "activated" : "deactivated"));

            return "redirect:/groups/" + groupId + "/nodes";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to toggle subscription source: " + e.getMessage());
            return "redirect:/groups";
        }
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        if ((scheme.equals("http") && serverPort == 80) || (scheme.equals("https") && serverPort == 443)) {
            return scheme + "://" + serverName;
        } else {
            return scheme + "://" + serverName + ":" + serverPort;
        }
    }
}
