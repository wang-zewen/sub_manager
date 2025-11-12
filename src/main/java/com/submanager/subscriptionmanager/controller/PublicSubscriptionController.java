package com.submanager.subscriptionmanager.controller;

import com.submanager.subscriptionmanager.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sub")
public class PublicSubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    /**
     * Public subscription endpoint (no authentication required)
     * Supports different client formats via 'target' parameter:
     * - target=clash: Clash YAML format
     * - target=v2ray: V2Ray format (base64, default)
     * - target=raw: Raw node list (no encoding)
     */
    @GetMapping("/{token}")
    public ResponseEntity<String> getSubscription(
            @PathVariable String token,
            @RequestParam(value = "target", required = false, defaultValue = "v2ray") String target) {

        String content = subscriptionService.generateSubscriptionByTarget(token, target);

        if (content == null || content.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Subscription not found or inactive");
        }

        HttpHeaders headers = new HttpHeaders();

        // Set content type based on target
        if ("clash".equalsIgnoreCase(target)) {
            headers.setContentType(MediaType.valueOf("application/x-yaml"));
            headers.set("Content-Disposition", "attachment; filename=clash.yaml");
            headers.set("profile-update-interval", "24");
            headers.set("subscription-userinfo", "upload=0; download=0; total=10737418240; expire=0");
        } else {
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set("Content-Disposition", "attachment; filename=subscription.txt");
            headers.set("Subscription-Userinfo", "upload=0; download=0; total=10737418240; expire=0");
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }
}
