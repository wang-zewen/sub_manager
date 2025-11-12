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
                Map<String, Object> proxy = parseNodeToClashProxy(node.getConfig(), node.getName());
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
     * Parse node config URL to Clash proxy format
     */
    private Map<String, Object> parseNodeToClashProxy(String nodeConfig, String nodeName) {
        if (nodeConfig.startsWith("vmess://")) {
            return parseVMessToClash(nodeConfig, nodeName);
        } else if (nodeConfig.startsWith("vless://")) {
            return parseVLESSToClash(nodeConfig, nodeName);
        } else if (nodeConfig.startsWith("trojan://")) {
            return parseTrojanToClash(nodeConfig, nodeName);
        } else if (nodeConfig.startsWith("ss://")) {
            return parseShadowsocksToClash(nodeConfig, nodeName);
        }
        return null;
    }

    private Map<String, Object> parseVMessToClash(String vmessUrl, String nodeName) {
        try {
            // Decode vmess:// URL
            String encoded = vmessUrl.substring(8); // Remove "vmess://"
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

            // Parse JSON
            Map<String, Object> vmess = parseJson(decoded);

            Map<String, Object> proxy = new LinkedHashMap<>();
            // Use the database node name instead of the name from vmess URL
            proxy.put("name", nodeName != null && !nodeName.isEmpty() ? nodeName : vmess.getOrDefault("ps", "VMess Node"));
            proxy.put("type", "vmess");
            proxy.put("server", vmess.get("add"));
            proxy.put("port", Integer.parseInt(vmess.get("port").toString()));
            proxy.put("uuid", vmess.get("id"));
            proxy.put("alterId", Integer.parseInt(vmess.getOrDefault("aid", "0").toString()));
            proxy.put("cipher", vmess.getOrDefault("scy", "auto"));

            // Parse transport protocol
            String network = vmess.getOrDefault("net", "tcp").toString();
            proxy.put("network", network);

            switch (network) {
                case "ws":
                    // WebSocket
                    Map<String, Object> wsOpts = new LinkedHashMap<>();
                    String wsPath = vmess.getOrDefault("path", "/").toString();
                    wsOpts.put("path", wsPath);
                    if (vmess.containsKey("host") && !vmess.get("host").toString().isEmpty()) {
                        Map<String, String> wsHeaders = new LinkedHashMap<>();
                        wsHeaders.put("Host", vmess.get("host").toString());
                        wsOpts.put("headers", wsHeaders);
                    }
                    proxy.put("ws-opts", wsOpts);
                    break;

                case "grpc":
                    // gRPC
                    Map<String, Object> grpcOpts = new LinkedHashMap<>();
                    String serviceName = vmess.getOrDefault("path", "").toString();
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
                    String h2Path = vmess.getOrDefault("path", "/").toString();
                    if (!h2Path.isEmpty()) {
                        h2Opts.put("path", h2Path);
                    }
                    if (vmess.containsKey("host") && !vmess.get("host").toString().isEmpty()) {
                        String[] hosts = vmess.get("host").toString().split(",");
                        h2Opts.put("host", Arrays.asList(hosts));
                    }
                    proxy.put("h2-opts", h2Opts);
                    break;

                case "tcp":
                    // TCP with HTTP obfuscation
                    String headerType = vmess.getOrDefault("type", "none").toString();
                    if (!"none".equals(headerType)) {
                        Map<String, Object> httpOpts = new LinkedHashMap<>();
                        if (vmess.containsKey("host") && !vmess.get("host").toString().isEmpty()) {
                            String[] hosts = vmess.get("host").toString().split(",");
                            httpOpts.put("host", Arrays.asList(hosts));
                        }
                        if (vmess.containsKey("path") && !vmess.get("path").toString().isEmpty()) {
                            String[] paths = vmess.get("path").toString().split(",");
                            httpOpts.put("path", Arrays.asList(paths));
                        }
                        Map<String, Object> httpObfs = new LinkedHashMap<>();
                        httpObfs.put("method", "GET");
                        httpObfs.put("path", httpOpts.getOrDefault("path", Arrays.asList("/")));
                        Map<String, Object> headers = new LinkedHashMap<>();
                        headers.put("Host", httpOpts.getOrDefault("host", Arrays.asList("")));
                        httpObfs.put("headers", headers);

                        Map<String, Object> httpOptsWrapper = new LinkedHashMap<>();
                        httpOptsWrapper.put("method", "GET");
                        httpOptsWrapper.put("path", httpOpts.getOrDefault("path", Arrays.asList("/")));
                        httpOptsWrapper.put("headers", headers);

                        proxy.put("http-opts", httpOptsWrapper);
                    }
                    break;

                case "quic":
                    // QUIC
                    Map<String, Object> quicOpts = new LinkedHashMap<>();
                    if (vmess.containsKey("host") && !vmess.get("host").toString().isEmpty()) {
                        quicOpts.put("host", vmess.get("host").toString());
                    }
                    if (vmess.containsKey("path") && !vmess.get("path").toString().isEmpty()) {
                        quicOpts.put("key", vmess.get("path").toString());
                    }
                    proxy.put("quic-opts", quicOpts);
                    break;

                default:
                    // tcp or unknown, no additional options needed
                    break;
            }

            // Parse TLS settings
            String tls = vmess.getOrDefault("tls", "").toString();
            if ("tls".equals(tls)) {
                proxy.put("tls", true);
                String sni = vmess.getOrDefault("sni", "").toString();
                if (sni.isEmpty() && vmess.containsKey("host")) {
                    sni = vmess.get("host").toString();
                }
                if (!sni.isEmpty()) {
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

    private Map<String, Object> parseVLESSToClash(String vlessUrl, String nodeName) {
        // Simplified VLESS parsing
        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("name", nodeName != null && !nodeName.isEmpty() ? nodeName : "VLESS Node");
        proxy.put("type", "vless");
        // TODO: Implement full VLESS parsing
        return proxy;
    }

    private Map<String, Object> parseTrojanToClash(String trojanUrl, String nodeName) {
        // Simplified Trojan parsing
        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("name", nodeName != null && !nodeName.isEmpty() ? nodeName : "Trojan Node");
        proxy.put("type", "trojan");
        // TODO: Implement full Trojan parsing
        return proxy;
    }

    private Map<String, Object> parseShadowsocksToClash(String ssUrl, String nodeName) {
        // Simplified SS parsing
        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("name", nodeName != null && !nodeName.isEmpty() ? nodeName : "Shadowsocks Node");
        proxy.put("type", "ss");
        // TODO: Implement full SS parsing
        return proxy;
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
