# Quick Start Guide - Subscription Manager

## 快速开始指南

### 方式 1：下载预构建的 JAR 包（最简单）

#### 步骤 1：下载 JAR

访问 [Releases 页面](https://github.com/wang-zewen/sub_manager/releases) 下载最新版本的 JAR 文件。

#### 步骤 2：检查 Java 版本

```bash
java -version
```

确保 Java 版本为 17 或更高。如果没有安装 Java，请访问：
- [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
- [OpenJDK](https://adoptium.net/)

#### 步骤 3：运行应用

```bash
java -jar subscription-manager-1.0.0.jar
```

#### 步骤 4：访问应用

打开浏览器访问：http://localhost:8080

**就这么简单！**

---

### 方式 2：使用 Docker（推荐用于服务器部署）

#### 前提条件

安装 Docker 和 Docker Compose：
- [Docker 安装指南](https://docs.docker.com/get-docker/)

#### 步骤 1：下载配置文件

```bash
# 创建目录
mkdir subscription-manager
cd subscription-manager

# 下载 docker-compose.yml
wget https://raw.githubusercontent.com/wang-zewen/sub_manager/main/docker-compose.yml

# 或者手动创建 docker-compose.yml 文件
```

#### 步骤 2：启动服务

```bash
docker compose up -d
```

#### 步骤 3：查看日志（可选）

```bash
docker compose logs -f
```

#### 步骤 4：访问应用

打开浏览器访问：http://localhost:8080

#### 管理命令

```bash
# 停止服务
docker compose down

# 重启服务
docker compose restart

# 查看状态
docker compose ps
```

---

### 方式 3：从源代码构建

#### 前提条件

- Java 17+
- Maven 3.6+

#### 步骤

```bash
# 克隆仓库
git clone https://github.com/wang-zewen/sub_manager.git
cd sub_manager

# 构建
mvn clean package

# 运行
java -jar target/subscription-manager-1.0.0.jar
```

---

## 配置

### 修改端口

如果 8080 端口被占用，可以使用环境变量修改：

```bash
# Linux/Mac
SERVER_PORT=9090 java -jar subscription-manager-1.0.0.jar

# Windows (PowerShell)
$env:SERVER_PORT=9090; java -jar subscription-manager-1.0.0.jar

# Windows (CMD)
set SERVER_PORT=9090 && java -jar subscription-manager-1.0.0.jar
```

### 数据存储位置

应用会在当前目录下创建 `data/` 文件夹存储数据。

**备份数据**：只需复制 `data/` 文件夹即可。

---

## 使用说明

### 添加订阅

1. 在页面顶部填写表单：
   - **名称**：给订阅起个名字（必填）
   - **URL**：订阅链接（必填）
   - **描述**：备注信息（可选）
   - **激活状态**：是否启用该订阅

2. 点击"添加"按钮

### 管理订阅

- **编辑**：点击编辑按钮修改订阅信息
- **切换状态**：点击切换按钮启用/禁用订阅
- **删除**：点击删除按钮移除订阅
- **复制链接**：点击复制按钮快速复制 URL

### API 使用

查看所有订阅：
```bash
curl http://localhost:8080/api/subscriptions
```

查看激活的订阅：
```bash
curl http://localhost:8080/api/subscriptions/active
```

添加订阅：
```bash
curl -X POST http://localhost:8080/api/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "我的订阅",
    "url": "https://example.com/sub",
    "description": "备注",
    "isActive": true
  }'
```

---

## 常见问题

### Q: 端口 8080 被占用怎么办？

A: 使用环境变量 `SERVER_PORT` 修改端口，见上文"配置"部分。

### Q: 如何备份数据？

A: 复制 `data/` 文件夹即可。数据库文件位于 `data/subscriptions.mv.db`。

### Q: 如何在后台运行？

A: Linux/Mac 使用 nohup：
```bash
nohup java -jar subscription-manager-1.0.0.jar > app.log 2>&1 &
```

Windows 使用开始菜单或任务计划程序。

### Q: 如何设置开机自启？

#### Linux (systemd):

创建 `/etc/systemd/system/subscription-manager.service`：

```ini
[Unit]
Description=Subscription Manager
After=network.target

[Service]
Type=simple
User=your-username
WorkingDirectory=/path/to/app
ExecStart=/usr/bin/java -jar /path/to/subscription-manager-1.0.0.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

启用服务：
```bash
sudo systemctl enable subscription-manager
sudo systemctl start subscription-manager
```

#### Windows:

使用 [NSSM](https://nssm.cc/) 工具将应用安装为 Windows 服务。

---

## 技术支持

遇到问题？

1. 查看 [README.md](./README.md) 了解详细信息
2. 访问 [Issues](https://github.com/wang-zewen/sub_manager/issues) 查看或提交问题
3. 查看应用日志排查问题

---

**祝使用愉快！**
