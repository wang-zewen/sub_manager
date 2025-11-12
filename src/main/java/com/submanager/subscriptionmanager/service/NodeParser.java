package com.submanager.subscriptionmanager.service;

import com.submanager.subscriptionmanager.model.ProxyNode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class NodeParser {

    /**
     * Parse node URL and populate ProxyNode fields
     */
    public void parseAndPopulateNode(ProxyNode node) {
        String config = node.getConfig();

        if (config == null || config.isEmpty()) {
            return;
        }

        try {
            if (config.startsWith("vmess://")) {
                parseVMessNode(node, config);
            } else if (config.startsWith("vless://")) {
                parseVLESSNode(node, config);
            } else if (config.startsWith("trojan://")) {
                parseTrojanNode(node, config);
            } else if (config.startsWith("ss://")) {
                parseShadowsocksNode(node, config);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse node: " + config + ", error: " + e.getMessage());
            // Don't fail the whole operation, just skip parsing
        }
    }

    private void parseVMessNode(ProxyNode node, String vmessUrl) {
        try {
            // Decode vmess:// URL
            String encoded = vmessUrl.substring(8); // Remove "vmess://"
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

            // Parse JSON
            Map<String, Object> vmess = parseJson(decoded);

            // Populate fields
            node.setServer(vmess.getOrDefault("add", "").toString());
            node.setPort(Integer.parseInt(vmess.getOrDefault("port", "0").toString()));
            node.setUuid(vmess.getOrDefault("id", "").toString());
            node.setAlterId(Integer.parseInt(vmess.getOrDefault("aid", "0").toString()));
            node.setCipher(vmess.getOrDefault("scy", "auto").toString());

            // Transport settings
            String network = vmess.getOrDefault("net", "tcp").toString();
            node.setNetwork(network);

            String host = vmess.getOrDefault("host", "").toString();
            node.setHost(host);

            String path = vmess.getOrDefault("path", "").toString();
            node.setPath(path);

            // TLS settings
            String tls = vmess.getOrDefault("tls", "").toString();
            node.setTls("tls".equals(tls));

            String sni = vmess.getOrDefault("sni", "").toString();
            if (sni.isEmpty() && !host.isEmpty()) {
                sni = host;
            }
            node.setSni(sni);

        } catch (Exception e) {
            System.err.println("Failed to parse VMess: " + e.getMessage());
        }
    }

    private void parseVLESSNode(ProxyNode node, String vlessUrl) {
        // TODO: Implement VLESS parsing
        // Format: vless://uuid@server:port?parameters
        try {
            String url = vlessUrl.substring(8); // Remove "vless://"

            // Basic parsing
            int atIndex = url.indexOf('@');
            if (atIndex > 0) {
                String uuid = url.substring(0, atIndex);
                node.setUuid(uuid);

                String remaining = url.substring(atIndex + 1);
                int queryIndex = remaining.indexOf('?');

                String serverPort;
                if (queryIndex > 0) {
                    serverPort = remaining.substring(0, queryIndex);
                } else {
                    serverPort = remaining;
                }

                int colonIndex = serverPort.lastIndexOf(':');
                if (colonIndex > 0) {
                    node.setServer(serverPort.substring(0, colonIndex));
                    try {
                        node.setPort(Integer.parseInt(serverPort.substring(colonIndex + 1)));
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse VLESS: " + e.getMessage());
        }
    }

    private void parseTrojanNode(ProxyNode node, String trojanUrl) {
        // TODO: Implement Trojan parsing
        // Format: trojan://password@server:port?parameters
        try {
            String url = trojanUrl.substring(9); // Remove "trojan://"

            int atIndex = url.indexOf('@');
            if (atIndex > 0) {
                String password = url.substring(0, atIndex);
                node.setUuid(password); // Store password in uuid field

                String remaining = url.substring(atIndex + 1);
                int queryIndex = remaining.indexOf('?');

                String serverPort;
                if (queryIndex > 0) {
                    serverPort = remaining.substring(0, queryIndex);
                } else {
                    serverPort = remaining;
                }

                int colonIndex = serverPort.lastIndexOf(':');
                if (colonIndex > 0) {
                    node.setServer(serverPort.substring(0, colonIndex));
                    try {
                        node.setPort(Integer.parseInt(serverPort.substring(colonIndex + 1)));
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse Trojan: " + e.getMessage());
        }
    }

    private void parseShadowsocksNode(ProxyNode node, String ssUrl) {
        // TODO: Implement Shadowsocks parsing
        // Format: ss://base64(method:password)@server:port
        try {
            String url = ssUrl.substring(5); // Remove "ss://"

            int atIndex = url.indexOf('@');
            if (atIndex > 0) {
                String encoded = url.substring(0, atIndex);
                String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

                int colonIndex = decoded.indexOf(':');
                if (colonIndex > 0) {
                    node.setCipher(decoded.substring(0, colonIndex));
                    node.setUuid(decoded.substring(colonIndex + 1)); // Store password in uuid
                }

                String serverPort = url.substring(atIndex + 1);
                colonIndex = serverPort.lastIndexOf(':');
                if (colonIndex > 0) {
                    node.setServer(serverPort.substring(0, colonIndex));
                    try {
                        node.setPort(Integer.parseInt(serverPort.substring(colonIndex + 1)));
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse Shadowsocks: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        // Simple JSON parser for VMess config
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON");
        }

        Map<String, Object> result = new HashMap<>();
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
