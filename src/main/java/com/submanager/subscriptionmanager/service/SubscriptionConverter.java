package com.submanager.subscriptionmanager.service;

import com.submanager.subscriptionmanager.model.ProxyNode;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SubscriptionConverter {

    /**
     * Convert node configs to Clash YAML format
     */
    public String toClashYaml(List<ProxyNode> nodes) {
        List<Map<String, Object>> proxies = new ArrayList<>();

        for (ProxyNode node : nodes) {
            try {
                Map<String, Object> proxy = parseNodeToClashProxy(node);
                if (proxy != null) {
                    proxies.add(proxy);
                }
            } catch (Exception e) {
                // Skip invalid nodes
                System.err.println("Failed to parse node: " + node.getConfig());
            }
        }

        // Build Clash config
        Map<String, Object> clashConfig = new LinkedHashMap<>();

        // Port settings
        clashConfig.put("port", 7890);
        clashConfig.put("socks-port", 7891);
        clashConfig.put("allow-lan", false);
        clashConfig.put("mode", "Rule");
        clashConfig.put("log-level", "info");
        clashConfig.put("external-controller", "127.0.0.1:9090");

        // Proxies
        clashConfig.put("proxies", proxies);

        // Proxy groups
        List<Map<String, Object>> proxyGroups = new ArrayList<>();
        Map<String, Object> selectGroup = new LinkedHashMap<>();
        selectGroup.put("name", "PROXY");
        selectGroup.put("type", "select");
        List<String> proxyNames = proxies.stream()
                .map(p -> (String) p.get("name"))
                .collect(Collectors.toList());
        proxyNames.add(0, "DIRECT");
        selectGroup.put("proxies", proxyNames);
        proxyGroups.add(selectGroup);
        clashConfig.put("proxy-groups", proxyGroups);

        // Basic rules
        List<String> rules = Arrays.asList(
                "DOMAIN-SUFFIX,google.com,PROXY",
                "DOMAIN-KEYWORD,google,PROXY",
                "DOMAIN,google.com,PROXY",
                "DOMAIN-SUFFIX,github.com,PROXY",
                "GEOIP,CN,DIRECT",
                "MATCH,PROXY"
        );
        clashConfig.put("rules", rules);

        // Convert to YAML
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);

        return yaml.dump(clashConfig);
    }

    /**
     * Parse node to Clash proxy format using database fields
     */
    private Map<String, Object> parseNodeToClashProxy(ProxyNode node) {
        String nodeConfig = node.getConfig();
        if (nodeConfig.startsWith("vmess://")) {
            return parseVMessToClash(node);
        } else if (nodeConfig.startsWith("vless://")) {
            return parseVLESSToClash(node);
        } else if (nodeConfig.startsWith("trojan://")) {
            return parseTrojanToClash(node);
        } else if (nodeConfig.startsWith("ss://")) {
            return parseShadowsocksToClash(node);
        }
        return null;
    }

    private Map<String, Object> parseVMessToClash(ProxyNode node) {
        try {
            // Decode vmess:// URL to get defaults
            String vmessUrl = node.getConfig();
            String encoded = vmessUrl.substring(8); // Remove "vmess://"
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

            // Parse JSON for fallback values
            Map<String, Object> vmess = parseJson(decoded);

            Map<String, Object> proxy = new LinkedHashMap<>();

            // Use database fields with fallback to parsed values
            proxy.put("name", node.getName());
            proxy.put("type", "vmess");
            proxy.put("server", node.getServer() != null ? node.getServer() : vmess.get("add"));
            proxy.put("port", node.getPort() != null ? node.getPort() : Integer.parseInt(vmess.get("port").toString()));
            proxy.put("uuid", node.getUuid() != null ? node.getUuid() : vmess.get("id"));
            proxy.put("alterId", node.getAlterId() != null ? node.getAlterId() : Integer.parseInt(vmess.getOrDefault("aid", "0").toString()));
            proxy.put("cipher", node.getCipher() != null ? node.getCipher() : vmess.getOrDefault("scy", "auto"));

            // Use database network field with fallback
            String network = node.getNetwork() != null ? node.getNetwork() : vmess.getOrDefault("net", "tcp").toString();
            proxy.put("network", network);

            switch (network) {
                case "ws":
                    // WebSocket
                    Map<String, Object> wsOpts = new LinkedHashMap<>();
                    String wsPath = node.getPath() != null ? node.getPath() : vmess.getOrDefault("path", "/").toString();
                    wsOpts.put("path", wsPath);
                    String wsHost = node.getHost() != null ? node.getHost() :
                        (vmess.containsKey("host") ? vmess.get("host").toString() : "");
                    if (!wsHost.isEmpty()) {
                        Map<String, String> wsHeaders = new LinkedHashMap<>();
                        wsHeaders.put("Host", wsHost);
                        wsOpts.put("headers", wsHeaders);
                    }
                    proxy.put("ws-opts", wsOpts);
                    break;

                case "grpc":
                    // gRPC
                    Map<String, Object> grpcOpts = new LinkedHashMap<>();
                    String serviceName = node.getPath() != null ? node.getPath() : vmess.getOrDefault("path", "").toString();
                    if (serviceName.isEmpty()) {
                        serviceName = vmess.getOrDefault("serviceName", "GunService").toString();
                    }
                    grpcOpts.put("grpc-service-name", serviceName);
                    proxy.put("grpc-opts", grpcOpts);
                    break;

                case "http":
                case "h2":
                    // HTTP/2
                    Map<String, Object> h2Opts = new LinkedHashMap<>();
                    String h2Path = node.getPath() != null ? node.getPath() : vmess.getOrDefault("path", "/").toString();
                    if (!h2Path.isEmpty()) {
                        h2Opts.put("path", h2Path);
                    }
                    String h2Host = node.getHost() != null ? node.getHost() :
                        (vmess.containsKey("host") ? vmess.get("host").toString() : "");
                    if (!h2Host.isEmpty()) {
                        String[] hosts = h2Host.split(",");
                        h2Opts.put("host", Arrays.asList(hosts));
                    }
                    proxy.put("h2-opts", h2Opts);
                    break;

                case "tcp":
                    // TCP with HTTP obfuscation
                    String headerType = vmess.getOrDefault("type", "none").toString();
                    if (!"none".equals(headerType)) {
                        String tcpHost = node.getHost() != null ? node.getHost() :
                            (vmess.containsKey("host") ? vmess.get("host").toString() : "");
                        String tcpPath = node.getPath() != null ? node.getPath() :
                            (vmess.containsKey("path") ? vmess.get("path").toString() : "/");

                        Map<String, Object> httpOptsWrapper = new LinkedHashMap<>();
                        httpOptsWrapper.put("method", "GET");
                        httpOptsWrapper.put("path", Arrays.asList(tcpPath.split(",")));

                        Map<String, Object> headers = new LinkedHashMap<>();
                        headers.put("Host", Arrays.asList(tcpHost.split(",")));
                        httpOptsWrapper.put("headers", headers);

                        proxy.put("http-opts", httpOptsWrapper);
                    }
                    break;

                case "quic":
                    // QUIC
                    Map<String, Object> quicOpts = new LinkedHashMap<>();
                    String quicHost = node.getHost() != null ? node.getHost() :
                        (vmess.containsKey("host") ? vmess.get("host").toString() : "");
                    if (!quicHost.isEmpty()) {
                        quicOpts.put("host", quicHost);
                    }
                    String quicKey = node.getPath() != null ? node.getPath() :
                        (vmess.containsKey("path") ? vmess.get("path").toString() : "");
                    if (!quicKey.isEmpty()) {
                        quicOpts.put("key", quicKey);
                    }
                    proxy.put("quic-opts", quicOpts);
                    break;

                default:
                    // tcp or unknown, no additional options needed
                    break;
            }

            // Use database TLS settings with fallback
            Boolean nodeTls = node.getTls();
            if (nodeTls == null) {
                // Fallback to parsed value
                String tls = vmess.getOrDefault("tls", "").toString();
                nodeTls = "tls".equals(tls);
            }

            if (nodeTls) {
                proxy.put("tls", true);
                // Use database SNI with fallback
                String sni = node.getSni();
                if (sni == null || sni.isEmpty()) {
                    sni = vmess.getOrDefault("sni", "").toString();
                    if (sni.isEmpty() && node.getHost() != null) {
                        sni = node.getHost();
                    } else if (sni.isEmpty() && vmess.containsKey("host")) {
                        sni = vmess.get("host").toString();
                    }
                }
                if (sni != null && !sni.isEmpty()) {
                    proxy.put("servername", sni);
                }
                // Skip certificate verification (common in proxy configs)
                if (vmess.containsKey("skip-cert-verify")) {
                    proxy.put("skip-cert-verify", Boolean.parseBoolean(vmess.get("skip-cert-verify").toString()));
                }
            }

            return proxy;
        } catch (Exception e) {
            System.err.println("Failed to parse VMess: " + e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseVLESSToClash(ProxyNode node) {
        try {
            Map<String, Object> proxy = new LinkedHashMap<>();

            // Use database fields
            proxy.put("name", node.getName());
            proxy.put("type", "vless");
            proxy.put("server", node.getServer());
            proxy.put("port", node.getPort());
            proxy.put("uuid", node.getUuid());
            proxy.put("udp", true);

            // Network type
            String network = node.getNetwork() != null ? node.getNetwork() : "tcp";
            proxy.put("network", network);

            // Transport settings based on network type
            switch (network) {
                case "ws":
                    Map<String, Object> wsOpts = new LinkedHashMap<>();
                    if (node.getPath() != null && !node.getPath().isEmpty()) {
                        wsOpts.put("path", node.getPath());
                    }
                    if (node.getHost() != null && !node.getHost().isEmpty()) {
                        Map<String, String> wsHeaders = new LinkedHashMap<>();
                        wsHeaders.put("Host", node.getHost());
                        wsOpts.put("headers", wsHeaders);
                    }
                    if (!wsOpts.isEmpty()) {
                        proxy.put("ws-opts", wsOpts);
                    }
                    break;

                case "grpc":
                    Map<String, Object> grpcOpts = new LinkedHashMap<>();
                    String serviceName = node.getPath() != null ? node.getPath() : "GunService";
                    grpcOpts.put("grpc-service-name", serviceName);
                    proxy.put("grpc-opts", grpcOpts);
                    break;

                case "http":
                case "h2":
                    Map<String, Object> h2Opts = new LinkedHashMap<>();
                    if (node.getPath() != null && !node.getPath().isEmpty()) {
                        h2Opts.put("path", Arrays.asList(node.getPath().split(",")));
                    }
                    if (node.getHost() != null && !node.getHost().isEmpty()) {
                        h2Opts.put("host", Arrays.asList(node.getHost().split(",")));
                    }
                    if (!h2Opts.isEmpty()) {
                        proxy.put("h2-opts", h2Opts);
                    }
                    break;

                case "quic":
                    Map<String, Object> quicOpts = new LinkedHashMap<>();
                    if (node.getHost() != null && !node.getHost().isEmpty()) {
                        quicOpts.put("quic-host", node.getHost());
                    }
                    if (node.getPath() != null && !node.getPath().isEmpty()) {
                        quicOpts.put("quic-key", node.getPath());
                    }
                    if (!quicOpts.isEmpty()) {
                        proxy.put("quic-opts", quicOpts);
                    }
                    break;
            }

            // Security settings (TLS or Reality)
            String security = node.getSecurity();
            if (security != null && !security.isEmpty()) {
                // Reality protocol
                if ("reality".equals(security)) {
                    proxy.put("tls", true);
                    proxy.put("reality-opts", buildRealityOpts(node));
                    if (node.getSni() != null && !node.getSni().isEmpty()) {
                        proxy.put("servername", node.getSni());
                    }
                    // Add flow control if present
                    if (node.getFlow() != null && !node.getFlow().isEmpty()) {
                        proxy.put("flow", node.getFlow());
                    }
                } else if ("tls".equals(security)) {
                    // Standard TLS
                    proxy.put("tls", true);
                    if (node.getSni() != null && !node.getSni().isEmpty()) {
                        proxy.put("servername", node.getSni());
                    } else if (node.getHost() != null && !node.getHost().isEmpty()) {
                        proxy.put("servername", node.getHost());
                    }
                    proxy.put("skip-cert-verify", false);
                }
            } else {
                // Fallback: check TLS field
                Boolean tls = node.getTls();
                if (tls != null && tls) {
                    proxy.put("tls", true);
                    if (node.getSni() != null && !node.getSni().isEmpty()) {
                        proxy.put("servername", node.getSni());
                    } else if (node.getHost() != null && !node.getHost().isEmpty()) {
                        proxy.put("servername", node.getHost());
                    }
                    proxy.put("skip-cert-verify", false);
                }
            }

            return proxy;
        } catch (Exception e) {
            System.err.println("Failed to parse VLESS to Clash: " + e.getMessage());
            return null;
        }
    }

    /**
     * Build Reality options for Clash
     */
    private Map<String, Object> buildRealityOpts(ProxyNode node) {
        Map<String, Object> realityOpts = new LinkedHashMap<>();

        if (node.getPublicKey() != null && !node.getPublicKey().isEmpty()) {
            realityOpts.put("public-key", node.getPublicKey());
        }

        if (node.getShortId() != null && !node.getShortId().isEmpty()) {
            realityOpts.put("short-id", node.getShortId());
        }

        return realityOpts;
    }

    private Map<String, Object> parseTrojanToClash(ProxyNode node) {
        try {
            Map<String, Object> proxy = new LinkedHashMap<>();

            // Use database fields
            proxy.put("name", node.getName());
            proxy.put("type", "trojan");
            proxy.put("server", node.getServer());
            proxy.put("port", node.getPort());
            proxy.put("password", node.getUuid()); // Trojan uses password instead of uuid
            proxy.put("udp", true);

            // SNI - Trojan always uses TLS
            if (node.getSni() != null && !node.getSni().isEmpty()) {
                proxy.put("sni", node.getSni());
            } else if (node.getHost() != null && !node.getHost().isEmpty()) {
                proxy.put("sni", node.getHost());
            }
            proxy.put("skip-cert-verify", false);

            // Network type (default to tcp for Trojan)
            String network = node.getNetwork() != null ? node.getNetwork() : "tcp";
            if (!"tcp".equals(network)) {
                proxy.put("network", network);
            }

            // Transport settings based on network type
            switch (network) {
                case "ws":
                    Map<String, Object> wsOpts = new LinkedHashMap<>();
                    if (node.getPath() != null && !node.getPath().isEmpty()) {
                        wsOpts.put("path", node.getPath());
                    }
                    if (node.getHost() != null && !node.getHost().isEmpty()) {
                        Map<String, String> wsHeaders = new LinkedHashMap<>();
                        wsHeaders.put("Host", node.getHost());
                        wsOpts.put("headers", wsHeaders);
                    }
                    if (!wsOpts.isEmpty()) {
                        proxy.put("ws-opts", wsOpts);
                    }
                    break;

                case "grpc":
                    Map<String, Object> grpcOpts = new LinkedHashMap<>();
                    String serviceName = node.getPath() != null ? node.getPath() : "GunService";
                    grpcOpts.put("grpc-service-name", serviceName);
                    proxy.put("grpc-opts", grpcOpts);
                    break;

                case "http":
                case "h2":
                    Map<String, Object> h2Opts = new LinkedHashMap<>();
                    if (node.getPath() != null && !node.getPath().isEmpty()) {
                        h2Opts.put("path", Arrays.asList(node.getPath().split(",")));
                    }
                    if (node.getHost() != null && !node.getHost().isEmpty()) {
                        h2Opts.put("host", Arrays.asList(node.getHost().split(",")));
                    }
                    if (!h2Opts.isEmpty()) {
                        proxy.put("h2-opts", h2Opts);
                    }
                    break;
            }

            return proxy;
        } catch (Exception e) {
            System.err.println("Failed to parse Trojan to Clash: " + e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseShadowsocksToClash(ProxyNode node) {
        try {
            Map<String, Object> proxy = new LinkedHashMap<>();

            // Use database fields
            proxy.put("name", node.getName());
            proxy.put("type", "ss");
            proxy.put("server", node.getServer());
            proxy.put("port", node.getPort());
            proxy.put("cipher", node.getCipher() != null ? node.getCipher() : "aes-256-gcm");
            proxy.put("password", node.getUuid()); // SS uses password stored in uuid field
            proxy.put("udp", true);

            return proxy;
        } catch (Exception e) {
            System.err.println("Failed to parse Shadowsocks to Clash: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        // Simple JSON parser for VMess config
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        json = json.substring(1, json.length() - 1); // Remove { }

        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); // Split by comma not in quotes
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("\"", "");
                String value = kv[1].trim().replaceAll("\"", "");
                result.put(key, value);
            }
        }

        return result;
    }
}
