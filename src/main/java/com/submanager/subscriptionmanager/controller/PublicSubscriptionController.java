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
     * Returns base64 encoded subscription content
     */
    @GetMapping("/{token}")
    public ResponseEntity<String> getSubscription(@PathVariable String token,
                                                  @RequestParam(value = "raw", required = false) Boolean raw) {
        String content;

        if (Boolean.TRUE.equals(raw)) {
            // Return raw content (not base64 encoded)
            content = subscriptionService.generateRawSubscriptionContent(token);
        } else {
            // Return base64 encoded content (default)
            content = subscriptionService.generateSubscriptionContent(token);
        }

        if (content == null || content.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Subscription not found or inactive");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.set("Subscription-Userinfo", "upload=0; download=0; total=1073741824; expire=0");
        headers.set("Content-Disposition", "attachment; filename=subscription.txt");

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }
}
