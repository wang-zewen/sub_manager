package com.submanager.subscriptionmanager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "proxy_nodes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProxyNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Type is required")
    @Column(nullable = false, length = 50)
    private String type; // vmess, vless, trojan, shadowsocks, etc.

    @NotBlank(message = "Config is required")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String config; // Store full node URL (vmess://, vless://, etc.)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_group_id", nullable = false)
    private SubscriptionGroup subscriptionGroup;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "sort_order")
    private Integer order = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
