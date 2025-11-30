package com.submanager.subscriptionmanager.service;

import com.submanager.subscriptionmanager.model.ProxyNode;
import com.submanager.subscriptionmanager.repository.ProxyNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeHealthCheckService {

    private final ProxyNodeRepository proxyNodeRepository;

    private static final int TIMEOUT_MS = 5000; // 5 seconds timeout
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";
    private static final String STATUS_UNKNOWN = "UNKNOWN";

    /**
     * Check health of a single node by attempting to connect to its server:port
     */
    @Transactional
    public void checkNodeHealth(Long nodeId) {
        ProxyNode node = proxyNodeRepository.findById(nodeId).orElse(null);
        if (node == null) {
            log.warn("Node not found: {}", nodeId);
            return;
        }

        performHealthCheck(node);
        proxyNodeRepository.save(node);
    }

    /**
     * Check health of multiple nodes
     */
    @Transactional
    public void checkNodesHealth(List<Long> nodeIds) {
        for (Long nodeId : nodeIds) {
            checkNodeHealth(nodeId);
        }
    }

    /**
     * Check health of all nodes in a subscription group
     */
    @Transactional
    public void checkGroupNodesHealth(Long groupId) {
        List<ProxyNode> nodes = proxyNodeRepository.findBySubscriptionGroupIdOrderByOrderAsc(groupId);
        for (ProxyNode node : nodes) {
            performHealthCheck(node);
        }
        proxyNodeRepository.saveAll(nodes);
    }

    /**
     * Async health check for a single node
     */
    @Async
    public CompletableFuture<Void> checkNodeHealthAsync(Long nodeId) {
        checkNodeHealth(nodeId);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Async health check for a group
     */
    @Async
    public CompletableFuture<Void> checkGroupNodesHealthAsync(Long groupId) {
        checkGroupNodesHealth(groupId);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Perform the actual health check by attempting TCP connection
     */
    private void performHealthCheck(ProxyNode node) {
        if (node.getServer() == null || node.getPort() == null) {
            node.setHealthStatus(STATUS_UNKNOWN);
            node.setLastCheckTime(LocalDateTime.now());
            node.setResponseTime(null);
            log.warn("Node {} has no server/port configured", node.getId());
            return;
        }

        long startTime = System.currentTimeMillis();
        boolean isReachable = false;

        try (Socket socket = new Socket()) {
            // Attempt to connect to the server
            InetSocketAddress address = new InetSocketAddress(node.getServer(), node.getPort());
            socket.connect(address, TIMEOUT_MS);
            isReachable = socket.isConnected();

            long responseTime = System.currentTimeMillis() - startTime;
            node.setResponseTime(responseTime);
            node.setHealthStatus(STATUS_UP);

            log.debug("Node {} ({}) is UP - Response time: {}ms",
                     node.getId(), node.getName(), responseTime);

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            node.setResponseTime(responseTime);
            node.setHealthStatus(STATUS_DOWN);

            log.debug("Node {} ({}) is DOWN - Error: {}",
                     node.getId(), node.getName(), e.getMessage());
        }

        node.setLastCheckTime(LocalDateTime.now());
    }

    /**
     * Get health status summary for a group
     */
    public HealthSummary getGroupHealthSummary(Long groupId) {
        List<ProxyNode> nodes = proxyNodeRepository.findBySubscriptionGroupIdOrderByOrderAsc(groupId);

        long totalNodes = nodes.size();
        long upNodes = nodes.stream().filter(n -> STATUS_UP.equals(n.getHealthStatus())).count();
        long downNodes = nodes.stream().filter(n -> STATUS_DOWN.equals(n.getHealthStatus())).count();
        long unknownNodes = nodes.stream().filter(n -> STATUS_UNKNOWN.equals(n.getHealthStatus())).count();

        return new HealthSummary(totalNodes, upNodes, downNodes, unknownNodes);
    }

    /**
     * Health summary data class
     */
    public static class HealthSummary {
        private final long total;
        private final long up;
        private final long down;
        private final long unknown;

        public HealthSummary(long total, long up, long down, long unknown) {
            this.total = total;
            this.up = up;
            this.down = down;
            this.unknown = unknown;
        }

        public long getTotal() { return total; }
        public long getUp() { return up; }
        public long getDown() { return down; }
        public long getUnknown() { return unknown; }
        public double getUpPercentage() {
            return total > 0 ? (up * 100.0 / total) : 0;
        }
    }
}
