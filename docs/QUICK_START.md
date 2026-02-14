# ⚡ BLACKBOX Quick Start Guide

Get the adaptive API Gateway running on your local machine in under **5 minutes**.

## 📋 Prerequisites

Before you start, ensure you have:
- **Docker Desktop** (running)
- **Git**
- **cURL** (terminal) or **Postman**

---

## 🚀 1. Clone & Start

Open your terminal and run:

```bash
# 1. Clone the repository
git clone <repo-url>
cd blackbox

# 2. Start the entire stack (Gateway + Redis + Postgres + Dashboard)
docker compose up --build -d
```

> **Wait ~30 seconds** for all services to initialize.
> You can check status with: `docker compose ps`

---

## 🔑 2. Get an Access Token

The gateway uses JWT authentication. Generate a test token:

```bash
# Generate a Standard Tier token
curl -X POST http://localhost:8080/test/token \
  -H "Content-Type: application/json" \
  -d '{"clientId":"quickstart-user","tier":"STANDARD"}'
```

**Copy the `token` string** from the response.

---

## 📡 3. Make Your First Request

Use the token to call a protected API endpoint (proxied to the mock backend):

```bash
# Replace <YOUR_TOKEN> below
TOKEN="<paste_token_here>"

curl -v http://localhost:8080/api/hello \
  -H "Authorization: Bearer $TOKEN"
```

### What you should see:
- **HTTP 200 OK**
- Response from Mock Backend
- **Headers**:
  - `X-RateLimit-Limit`: 100
  - `X-RateLimit-Remaining`: 99

---

## 📊 4. See It In Action (dashboards)

While the app is running, open these URLs in your browser:

| Tool | URL | Credentials | Purpose |
|------|-----|-------------|---------|
| **Grafana** | [http://localhost:3000](http://localhost:3000) | `admin` / `admin` | Visualize traffic & errors |
| **Prometheus** | [http://localhost:9090](http://localhost:9090) | (none) | Raw metrics |
| **Gateway Info** | [http://localhost:8080/actuator/info](http://localhost:8080/actuator/info) | (none) | App status |

---

## 🛑 5. Stop & Clean Up

When you're done:

```bash
# Stop containers and remove networks
docker compose down

# (Optional) Remove database volume to reset data
docker compose down -v
```

---

## 🧩 Next Steps

- **Trouble?** Check [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)
- **Deep Dive?** Read [CODEBASE_GUIDE.md](./CODEBASE_GUIDE.md)
