package com.submanager.subscriptionmanager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "subscription_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String token;

    @Column(length = 1000)
    private String description;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @OneToMany(mappedBy = "subscriptionGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProxyNode> nodes = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void generateToken() {
        if (this.token == null || this.token.isEmpty()) {
            this.token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }

    public String getSubscriptionUrl(String baseUrl) {
        return baseUrl + "/sub/" + token;
    }
}
