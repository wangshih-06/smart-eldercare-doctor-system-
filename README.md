# 智慧医养大数据公共服务平台 · 医生服务系统

面向社区医生 / 护士的老年健康管理平台。后端 Spring Boot，前端 Vue 3 + Element Plus + ECharts 单页应用，采用「墨青 · 临床守护」深色视觉主题，签名元素为生命体征 ECG 律动。

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 2.7 · MyBatis-Plus · Druid · JWT · Redis · Knife4j |
| 前端 | Vue 3 · Element Plus · ECharts · Axios（CDN 引入，无需构建） |
| 数据库 | MySQL 5.7+ / 8.0+ |
| 缓存 | Redis（Windows 版已内置在仓库中，开箱即用） |
| 构建 | Maven（已内置 Maven Wrapper，无需预装 Maven） |

## 环境要求

拉取后需要**自行准备**的只有两样：

1. **JDK 17+** — 下载：<https://adoptium.net/>
2. **MySQL 5.7+ / 8.0+** — 需运行在 `localhost:3306`，账号 `root`，密码 `123456`
   （如你的密码不同，改 `src/main/resources/application.yml` 里的 `spring.datasource.password`）

> Maven 和 Redis 无需另装：Maven 用仓库内的 Wrapper 自动下载；Windows 版 Redis 已随仓库提供。
> 数据库无需手动建表：应用首次启动会**自动建库、建表并灌入测试数据**。

## 快速启动

### Windows（最简单）

双击运行，或在项目根目录执行：

```bat
start.bat
```

脚本会自动：检查 Java → 启动内置 Redis → 用 Maven Wrapper 拉依赖并启动应用。

### macOS / Linux

内置 Redis 是 Windows 二进制，需先自行安装 Redis：

```bash
# macOS
brew install redis && brew services start redis
# Ubuntu
sudo apt install redis-server && sudo systemctl start redis

# 然后启动应用
./start.sh
```

### 手动启动（任意平台）

```bash
# 1. 启动 Redis（Windows 用内置的，Mac/Linux 用系统安装的）
#    Windows: Redis-8.8.0-Windows-x64-cygwin-with-Service\redis-server.exe

# 2. 启动应用（mvnw 会自动下载 Maven）
./mvnw spring-boot:run        # macOS / Linux
mvnw.cmd spring-boot:run      # Windows
```

## 访问系统

启动成功后打开：**<http://localhost:8080>**

| 地址 | 说明 |
|------|------|
| `http://localhost:8080` | 登录页 |
| `http://localhost:8080/druid` | Druid 连接池监控（admin / 123456） |

### 测试账号

| 账号 | 密码 | 角色 |
|------|------|------|
| `admin` | `admin123` | 系统管理员 |
| `doctor01` | `doctor123` | 医生 |
| `nurse01` | `nurse123` | 护士 |

> 登录需输入页面上的图形验证码，点击图片可刷新。

## 常见问题

- **端口 8080 被占用**：`netstat -ano | findstr :8080` 找到 PID，`taskkill /F /PID <PID>`。
- **MySQL 连接失败**：确认 MySQL 已启动，且 `application.yml` 里的密码与你本机一致。
- **首次启动慢**：Maven Wrapper 在下载依赖，属正常，耐心等待。
- **登录后页面空白**：`Ctrl+F5` 强刷清缓存，或清 `localStorage` 后重试。

## 安全提示

仓库中的数据库密码为本地开发用弱密码（`123456`）。若部署到真实环境，请务必改用强密码，并通过环境变量注入而非硬编码进 `application.yml`。
