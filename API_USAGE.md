# Node Management API

REST API for managing proxy nodes in subscription groups.

## Base URL

```
http://your-server:8080/api/v1
```

## API Endpoints

### 1. 添加单个节点到组

**Endpoint:** `POST /api/v1/groups/{groupId}/nodes`

**描述:** 将一个代理节点添加到指定的订阅组

**请求示例:**

```bash
curl -X POST http://localhost:8080/api/v1/groups/1/nodes \
  -H "Content-Type: application/json" \
  -d '{
    "name": "HK-Node-1",
    "config": "vless://9afd1229-b893-40c1-84dd-51e7ce204913@cdns.doon.eu.org:443?encryption=none&security=tls&sni=jdkls.pyruспgd.de5.net&fp=firefox&type=ws&host=jdkls.pyruспgd.de5.net&path=%2Fvless-argo%3Fed%3D2560#HK-Node"
  }'
```

**请求体参数:**

| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| name | string | 否 | 节点名称（如果不提供，将自动生成） |
| config | string | 是 | 节点配置URL（支持 vmess://, vless://, trojan://, ss://, hysteria://, hysteria2://） |

**成功响应 (200):**

```json
{
  "success": true,
  "message": "Node added successfully",
  "node": {
    "id": 123,
    "name": "HK-Node-1",
    "type": "vless",
    "server": "cdns.doon.eu.org",
    "port": 443
  }
}
```

**错误响应 (400):**

```json
{
  "error": "Node config is required"
}
```

---

### 2. 批量添加节点

**Endpoint:** `POST /api/v1/groups/{groupId}/nodes/batch`

**描述:** 批量添加多个节点到指定的订阅组

**请求示例:**

```bash
curl -X POST http://localhost:8080/api/v1/groups/1/nodes/batch \
  -H "Content-Type: application/json" \
  -d '{
    "nodes": [
      {
        "name": "HK-Node-1",
        "config": "vless://uuid1@server1:443?..."
      },
      {
        "name": "US-Node-1",
        "config": "vmess://base64config..."
      },
      {
        "name": "JP-Node-1",
        "config": "trojan://password@server3:443?..."
      }
    ]
  }'
```

**请求体参数:**

| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| nodes | array | 是 | 节点对象数组 |
| nodes[].name | string | 否 | 节点名称 |
| nodes[].config | string | 是 | 节点配置URL |

**成功响应 (200):**

```json
{
  "success": true,
  "total": 3,
  "succeeded": 3,
  "failed": 0
}
```

**部分失败响应 (200):**

```json
{
  "success": true,
  "total": 3,
  "succeeded": 2,
  "failed": 1,
  "errors": [
    {
      "index": "1",
      "error": "Unknown node type"
    }
  ]
}
```

---

### 3. 获取组信息

**Endpoint:** `GET /api/v1/groups/{groupId}`

**描述:** 获取指定订阅组的详细信息

**请求示例:**

```bash
curl http://localhost:8080/api/v1/groups/1
```

**成功响应 (200):**

```json
{
  "id": 1,
  "name": "My VPN Group",
  "token": "abc123def456",
  "description": "My personal VPN nodes",
  "isActive": true,
  "nodeCount": 5
}
```

---

### 4. 列出所有组

**Endpoint:** `GET /api/v1/groups`

**描述:** 获取所有订阅组的列表

**请求示例:**

```bash
curl http://localhost:8080/api/v1/groups
```

**成功响应 (200):**

```json
{
  "groups": [
    {
      "id": 1,
      "name": "My VPN Group",
      "token": "abc123",
      "isActive": true,
      "nodeCount": 5
    },
    {
      "id": 2,
      "name": "Work VPN",
      "token": "def456",
      "isActive": true,
      "nodeCount": 3
    }
  ],
  "total": 2
}
```

---

## 支持的节点类型

- **vmess://** - VMess协议
- **vless://** - VLESS协议
- **trojan://** - Trojan协议
- **ss://** - Shadowsocks协议
- **hysteria://** - Hysteria协议
- **hysteria2://** 或 **hy2://** - Hysteria2协议

---

## 使用场景示例

### 场景1: VPS自动化部署

在VPS上搭建好节点后，通过脚本自动上传到订阅管理器：

```bash
#!/bin/bash

# VPS上的节点配置
NODE_CONFIG="vless://$(cat /etc/v2ray/uuid)@$(curl -s ifconfig.me):443?encryption=none&security=tls&type=ws&path=/ws#MyVPS-$(hostname)"

# 上传到订阅组
curl -X POST http://your-server:8080/api/v1/groups/1/nodes \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"VPS-$(hostname)\",
    \"config\": \"$NODE_CONFIG\"
  }"
```

### 场景2: 批量导入现有节点

```python
import requests
import json

# 准备节点数据
nodes = [
    {"name": "HK-1", "config": "vless://..."},
    {"name": "US-1", "config": "vmess://..."},
    {"name": "JP-1", "config": "trojan://..."}
]

# 批量上传
response = requests.post(
    "http://localhost:8080/api/v1/groups/1/nodes/batch",
    json={"nodes": nodes}
)

print(json.dumps(response.json(), indent=2))
```

### 场景3: 获取组ID

如果不知道组ID，先获取所有组：

```bash
# 获取所有组
curl http://localhost:8080/api/v1/groups

# 选择目标组的ID，然后添加节点
curl -X POST http://localhost:8080/api/v1/groups/1/nodes \
  -H "Content-Type: application/json" \
  -d '{"config": "vless://..."}'
```

---

## 错误码

| HTTP状态码 | 描述 |
|-----------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 404 | 资源不找到（如：组不存在） |
| 500 | 服务器内部错误 |

---

## 注意事项

1. **节点名称:** 如果不提供名称，系统会自动生成（格式：`server:port` 或 `type-node-timestamp`）
2. **节点配置:** 必须是完整的节点URL，包含所有必要的参数
3. **批量添加:** 即使部分节点失败，成功的节点仍会被添加
4. **CSRF:** API端点已禁用CSRF保护，可以直接调用
5. **认证:** 当前API无需认证，建议在生产环境中添加API Token验证

---

## 未来增强

以下功能可在需要时添加：

- [ ] API Token 认证
- [ ] 速率限制
- [ ] 节点更新API
- [ ] 节点删除API
- [ ] 节点健康检查API
- [ ] Webhook回调通知
