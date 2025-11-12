package com.submanager.subscriptionmanager.controller;

import com.submanager.subscriptionmanager.model.ProxyNode;
import com.submanager.subscriptionmanager.model.SubscriptionGroup;
import com.submanager.subscriptionmanager.service.NodeParser;
import com.submanager.subscriptionmanager.service.SubscriptionService;
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
        String baseUrl = getBaseUrl(request);

        model.addAttribute("group", group);
        model.addAttribute("nodes", nodes);
        model.addAttribute("node", new ProxyNode());
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
