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

    // Parsed node information for visualization and editing
    @Column(length = 255)
    private String server; // Server address/hostname

    @Column
    private Integer port; // Server port

    @Column(length = 100)
    private String uuid; // UUID or password

    @Column(name = "alter_id")
    private Integer alterId; // VMess alterId

    @Column(length = 50)
    private String cipher; // Encryption method

    @Column(length = 50)
    private String network; // Transport protocol: tcp, ws, grpc, h2, quic

    @Column(columnDefinition = "TEXT")
    private String networkSettings; // Transport settings as JSON

    @Column
    private Boolean tls; // TLS enabled

    @Column(length = 255)
    private String sni; // Server Name Indication

    @Column(length = 255)
    private String host; // Host header for WS/HTTP

    @Column(length = 500)
    private String path; // Path for WS/HTTP/gRPC

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_group_id", nullable = false)
    private SubscriptionGroup subscriptionGroup;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "sort_order")
    private Integer order = 0;

    // Health check fields
    @Column(name = "health_status", length = 20)
    private String healthStatus = "UNKNOWN"; // UP, DOWN, UNKNOWN

    @Column(name = "last_check_time")
    private LocalDateTime lastCheckTime;

    @Column(name = "response_time")
    private Long responseTime; // Response time in milliseconds

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
