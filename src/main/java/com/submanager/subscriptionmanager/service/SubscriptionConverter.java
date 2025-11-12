package com.submanager.subscriptionmanager.service;

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
    public String toClashYaml(List<String> nodeConfigs) {
        List<Map<String, Object>> proxies = new ArrayList<>();

        for (String config : nodeConfigs) {
            try {
                Map<String, Object> proxy = parseNodeToClashProxy(config);
                if (proxy != null) {
                    proxies.add(proxy);
                }
            } catch (Exception e) {
                // Skip invalid nodes
                System.err.println("Failed to parse node: " + config);
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
    private Map<String, Object> parseNodeToClashProxy(String nodeConfig) {
        if (nodeConfig.startsWith("vmess://")) {
            return parseVMessToClash(nodeConfig);
        } else if (nodeConfig.startsWith("vless://")) {
            return parseVLESSToClash(nodeConfig);
        } else if (nodeConfig.startsWith("trojan://")) {
            return parseTrojanToClash(nodeConfig);
        } else if (nodeConfig.startsWith("ss://")) {
            return parseShadowsocksToClash(nodeConfig);
        }
        return null;
    }

    private Map<String, Object> parseVMessToClash(String vmessUrl) {
        try {
            // Decode vmess:// URL
            String encoded = vmessUrl.substring(8); // Remove "vmess://"
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

            // Parse JSON
            Map<String, Object> vmess = parseJson(decoded);

            Map<String, Object> proxy = new LinkedHashMap<>();
            proxy.put("name", vmess.getOrDefault("ps", "VMess Node"));
            proxy.put("type", "vmess");
            proxy.put("server", vmess.get("add"));
            proxy.put("port", Integer.parseInt(vmess.get("port").toString()));
            proxy.put("uuid", vmess.get("id"));
            proxy.put("alterId", Integer.parseInt(vmess.getOrDefault("aid", "0").toString()));
            proxy.put("cipher", vmess.getOrDefault("scy", "auto"));

            String network = vmess.getOrDefault("net", "tcp").toString();
            proxy.put("network", network);

            if ("ws".equals(network)) {
                Map<String, Object> wsOpts = new LinkedHashMap<>();
                wsOpts.put("path", vmess.getOrDefault("path", "/"));
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("Host", vmess.getOrDefault("host", "").toString());
                wsOpts.put("headers", headers);
                proxy.put("ws-opts", wsOpts);
            }

            String tls = vmess.getOrDefault("tls", "").toString();
            if ("tls".equals(tls)) {
                proxy.put("tls", true);
                if (vmess.containsKey("sni")) {
                    proxy.put("servername", vmess.get("sni"));
                }
            }

            return proxy;
        } catch (Exception e) {
            System.err.println("Failed to parse VMess: " + e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseVLESSToClash(String vlessUrl) {
        // Simplified VLESS parsing
        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("name", "VLESS Node");
        proxy.put("type", "vless");
        // TODO: Implement full VLESS parsing
        return proxy;
    }

    private Map<String, Object> parseTrojanToClash(String trojanUrl) {
        // Simplified Trojan parsing
        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("name", "Trojan Node");
        proxy.put("type", "trojan");
        // TODO: Implement full Trojan parsing
        return proxy;
    }

    private Map<String, Object> parseShadowsocksToClash(String ssUrl) {
        // Simplified SS parsing
        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("name", "Shadowsocks Node");
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
