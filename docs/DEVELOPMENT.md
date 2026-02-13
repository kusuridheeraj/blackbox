# BLACKBOX Development Guide

**Last Updated:** February 2026  
**Supported Platforms:** Windows, Linux, macOS

---

##Table of Contents

1. [Prerequisites](#prerequisites)
2. [Initial Setup](#initial-setup)
3. [Platform-Specific Instructions](#platform-specific-instructions)
4. [Running the Project](#running-the-project)
5. [Development Workflow](#development-workflow)
6. [Testing](#testing)
7. [Building for Production](#building-for-production)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

| Tool | Version | Purpose |
|------|---------|---------|
| **Java JDK** | 21+ | Compile and run gateway |
| **Maven** | 3.9+ | Build tool |
| **Docker** | 24+ | Run services locally |
| **Docker Compose** | 2.20+ | Orchestrate services |
| **Git** | 2.40+ | Version control |
| **k6** | 0.45+ | Load testing |

### Optional (Recommended)

| Tool | Purpose |
|------|---------|
| **IntelliJ IDEA** | Java IDE with Spring support |
| **VS Code** | Lightweight editor |
| **Postman** | API testing |
| **Redis CLI** | Debug Redis |
| **pgAdmin** | PostgreSQL admin |

---

## Initial Setup

### Step 1: Clone Repository

```bash
# Clone the repository
git clone https://github.com/your-org/blackbox.git
cd blackbox
```

### Step 2: Install Platform-Specific Tools

**Choose your platform:**
- [Windows Setup](#windows-setup)
- [Linux Setup](#linux-setup)
- [macOS Setup](#macos-setup)

---

## Platform-Specific Instructions

### Windows Setup

#### Install Java 21

**Option 1: Installer (Recommended)**
```powershell
# Download from Adoptium
# https://adoptium.net/temurin/releases/?version=21

# Verify installation
java -version
# Should show: openjdk version "21.0.x"
```

**Option 2: Chocolatey**
```powershell
# Install Chocolatey first (if not installed)
# https://chocolatey.org/install

choco install openjdk21 -y
java -version
```

#### Install Maven

**Option 1: Manual**
```powershell
# Download from https://maven.apache.org/download.cgi
# Extract to C:\Program Files\Maven
# Add to PATH: C:\Program Files\Maven\bin

mvn -version
```

**Option 2: Chocolatey**
```powershell
choco install maven -y
mvn -version
```

#### Install Docker Desktop

```powershell
# Download from https://www.docker.com/products/docker-desktop/
# Install and restart computer

docker --version
docker compose version
```

**Important Windows Settings:**
1. Open Docker Desktop
2. Settings → Resources → WSL Integration
3. Enable integration with your WSL distro (if using WSL)
4. Settings → Resources → Advanced
   - Memory: 4GB minimum (8GB recommended)
   - CPUs: 2 minimum (4 recommended)

#### Install k6

```powershell
choco install k6 -y
k6 version
```

#### Set Environment Variables (Windows)

```powershell
# PowerShell
$env:JWT_SECRET = "your-secret-key-here"

# Permanent (via System Properties)
[System.Environment]::SetEnvironmentVariable("JWT_SECRET", "your-secret-key", "User")
```

---

### Linux Setup

#### Install Java 21 (Ubuntu/Debian)

```bash
# Update package list
sudo apt update

# Install Java 21
sudo apt install -y openjdk-21-jdk

# Verify
java -version
```

#### Install Java 21 (Fedora/RHEL)

```bash
sudo dnf install -y java-21-openjdk-devel
java -version
```

#### Install Maven

```bash
# Ubuntu/Debian
sudo apt install -y maven

# Fedora/RHEL
sudo dnf install -y maven

# Verify
mvn -version
```

#### Install Docker

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y docker.io docker-compose-plugin

# Start Docker
sudo systemctl start docker
sudo systemctl enable docker

# Add user to docker group (avoid sudo)
sudo usermod -aG docker $USER
newgrp docker  # Or logout/login

# Verify
docker --version
docker compose version
```

#### Install k6

```bash
# Ubuntu/Debian
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt update
sudo apt install k6

# Verify
k6 version
```

#### Set Environment Variables (Linux)

```bash
# Temporary (current session)
export JWT_SECRET="your-secret-key-here"

# Permanent (add to ~/.bashrc or ~/.zshrc)
echo 'export JWT_SECRET="your-secret-key-here"' >> ~/.bashrc
source ~/.bashrc
```

---

### macOS Setup

#### Install Java 21

**Option 1: Homebrew (Recommended)**
```bash
# Install Homebrew (if not installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install Java 21
brew install openjdk@21

# Link for system Java wrappers
sudo ln -sfn $(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# Verify
java -version
```

#### Install Maven

```bash
brew install maven
mvn -version
```

#### Install Docker Desktop

```bash
# Download from https://www.docker.com/products/docker-desktop/
# Or via Homebrew
brew install --cask docker

# Start Docker Desktop from Applications
# Verify
docker --version
docker compose version
```

#### Install k6

```bash
brew install k6
k6 version
```

#### Set Environment Variables (macOS)

```bash
# Temporary
export JWT_SECRET="your-secret-key-here"

# Permanent (Bash)
echo 'export JWT_SECRET="your-secret-key-here"' >> ~/.bash_profile
source ~/.bash_profile

# Permanent (Zsh - default on macOS Catalina+)
echo 'export JWT_SECRET="your-secret-key-here"' >> ~/.zshrc
source ~/.zshrc
```

---

## Running the Project

### Quick Start (All Platforms)

```bash
# 1. Navigate to project directory
cd blackbox

# 2. Start all services
docker compose up -d

# 3. Wait for services to be healthy (~30 seconds)
docker compose ps

# 4. Verify gateway is running
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}

# 5. Generate a test token
curl "http://localhost:8080/test/token?clientId=test&tier=PREMIUM"
# Returns: {"token":"eyJhbGci..."}

# 6. Make a test request
curl http://localhost:8080/api/test \
  -H "Authorization: Bearer <token-from-step-5>"
# Should return: {"status":200,"message":"Success from mock-backend"}
```

---

### Services Overview

After `docker compose up`, you'll have:

| Service | URL | Purpose |
|---------|-----|---------|
| **Gateway** | http://localhost:8080 | Main API Gateway |
| **Mock Backend** | http://localhost:8081 | Test backend service |
| **Redis** | localhost:6379 | Rate limit state |
| **PostgreSQL** | localhost:5432 | Audit logs |
| **Prometheus** | http://localhost:9090 | Metrics collection |
| **Grafana** | http://localhost:3000 | Visualization |

**Grafana Login:**
- Username: `admin`
- Password: `BlackBox2026!`

---

### Viewing Logs

**All services:**
```bash
docker compose logs -f
```

**Specific service:**
```bash
# Gateway logs
docker compose logs -f gateway

# Backend logs
docker compose logs -f mock-backend
```

**Filter by time:**
```bash
# Last 100 lines
docker compose logs --tail=100 gateway

# Since 5 minutes ago
docker compose logs --since=5m gateway
```

---

### Stopping Services

```bash
# Stop all services (keeps data)
docker compose stop

# Stop and remove containers (keeps data volumes)
docker compose down

# Stop and remove everything (including data!)
docker compose down -v  # ⚠️ Deletes Redis/PostgreSQL data
```

---

## Development Workflow

### Making Code Changes

#### 1. Create Feature Branch

```bash
git checkout -b feature/my-new-feature
```

#### 2. Make Changes

**Example: Add a new rate limit tier**

Edit `src/main/resources/application.yml`:
```yaml
gateway:
  ratelimit:
    tiers:
      ENTERPRISE:  # New tier
        requestsPerSecond: 1000
        burstSize: 1500
```

#### 3. Test Locally

**Rebuild and restart gateway:**
```bash
# Rebuild gateway image
docker compose up gateway --build -d

# Wait for startup
docker compose logs -f gateway

# Ctrl+C when you see "Started GatewayApplication"
```

**Generate token for new tier:**
```bash
curl "http://localhost:8080/test/token?clientId=enterprise-client&tier=ENTERPRISE"
```

**Test:**
```bash
# Manual test
curl http://localhost:8080/api/test \
  -H "Authorization: Bearer <token>"

# Load test (create k6/enterprise-test.js first)
k6 run k6/enterprise-test.js
```

#### 4. Verify Metrics

```bash
# Check Prometheus
open http://localhost:9090
# Query: gateway_request_total{tier="ENTERPRISE"}

# Check Grafana
open http://localhost:3000/d/blackbox-gateway
```

#### 5. Run Tests (if any)

```bash
# Unit tests
mvn test

# Integration tests
mvn verify
```

#### 6. Commit Changes

```bash
git add .
git commit -m "feat: add ENTERPRISE tier with 1000 req/s limit"
```

### Commit Message Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `refactor`: Code change that neither fixes a bug nor adds a feature
- `test`: Adding missing tests
- `chore`: Changes to build process or auxiliary tools

**Examples:**
```bash
git commit -m "feat(ratelimit): add ENTERPRISE tier"
git commit -m "fix(auth): handle expired JWT gracefully"
git commit -m "docs(readme): add macOS setup instructions"
```

---

## Testing

### Manual Testing

#### Generate Token

```bash
# Windows (PowerShell)
$token = (curl.exe -s "http://localhost:8080/test/token?clientId=test&tier=PREMIUM" | ConvertFrom-Json).token
echo $token

# Linux/macOS
TOKEN=$(curl -s "http://localhost:8080/test/token?clientId=test&tier=PREMIUM" | jq -r '.token')
echo $TOKEN
```

#### Make Requests

```bash
# Windows (PowerShell)
curl.exe -s http://localhost:8080/api/test -H "Authorization: Bearer $token"

# Linux/macOS
curl -s http://localhost:8080/api/test \
  -H "Authorization: Bearer $TOKEN"
```

---

### Load Testing with k6

#### Run Existing Tests

```bash
# Baseline test (100 req/s for 1 minute)
k6 run k6/baseline.js

# Spike test (500 req/s burst)
k6 run k6/spike.js

# Sustained 1000 req/s
k6 run k6/sustained-1000.js
```

#### Create Custom Test

Create `k6/my-test.js`:
```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 10,          // 10 virtual users
    duration: '30s',  // Run for 30 seconds
};

const BASE_URL = 'http://localhost:8080';
const TOKEN = __ENV.JWT_TOKEN || '';

export default function () {
    const res = http.get(`${BASE_URL}/api/test`, {
        headers: { 'Authorization': `Bearer ${TOKEN}` },
    });

    check(res, {
        'status is 200': (r) => r.status === 200,
        'latency < 100ms': (r) => r.timings.duration < 100,
    });
}
```

Run:
```bash
# Generate token first
TOKEN=$(curl -s "http://localhost:8080/test/token?clientId=loadtest&tier=PREMIUM" | jq -r '.token')

# Run test
JWT_TOKEN=$TOKEN k6 run k6/my-test.js
```

---

### Debugging

#### View Redis Data

```bash
# Connect to Redis
docker exec -it blackbox-redis redis-cli

# View rate limit for a client
GET blackbox:ratelimit:test:tokens
GET blackbox:ratelimit:test:lastRefill

# View adaptive multiplier
GET blackbox:adaptive:multiplier

# Exit
exit
```

#### View PostgreSQL Data

```bash
# Connect to PostgreSQL
docker exec -it blackbox-postgres psql -U blackbox

# View audit logs
SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 10;

# View rate limit adjustments
SELECT * FROM audit_log WHERE event_type = 'RATE_LIMIT_ADJUST';

# Exit
\q
```

#### Check Prometheus Metrics

```bash
# All gateway metrics
curl -s http://localhost:8080/actuator/prometheus | grep gateway_

# Specific metric
curl -s http://localhost:8080/actuator/prometheus | grep gateway_request_total
```

---

## Building for Production

### Build JAR (Without Docker)

```bash
# Clean and build
mvn clean package

# JAR location
ls -l target/gateway-*.jar

# Run JAR
java -jar target/gateway-1.0.0.jar
```

### Build Docker Image

```bash
# Build gateway image
docker build -t blackbox-gateway:latest .

# Build mock-backend image
docker build -t blackbox-mock-backend:latest -f mock-backend/Dockerfile .

# Verify images
docker images | grep blackbox
```

### Multi-Platform Build (Docker Buildx)

```bash
# Enable buildx
docker buildx create --use

# Build for multiple architectures
docker buildx build --platform linux/amd64,linux/arm64 \
  -t blackbox-gateway:latest .
```

**Why multi-platform?**
- M1/M2 Macs: ARM64 architecture
- AWS EC2: Graviton instances (ARM64, cheaper)
- Intel servers: AMD64 architecture

---

### Environment Variables for Production

**Never use defaults in production!**

```bash
# Required
export JWT_SECRET=$(openssl rand -base64 32)

# Optional (Override defaults)
export REDIS_HOST=prod-redis.example.com
export REDIS_PORT=6379
export POSTGRES_HOST=prod-db.example.com
export POSTGRES_PORT=5432
export POSTGRES_DB=blackbox_prod
export POSTGRES_USER=app_user
export POSTGRES_PASSWORD=<secure-password>

# Logging
export LOGGING_LEVEL_ROOT=INFO  # Not DEBUG in production!

# Ports
export SERVER_PORT=8080
```

---

## Troubleshooting

### Issue: Docker Compose Fails to Start

**Symptoms:**
```
Error response from daemon: Ports are not available
```

**Solution (Windows):**
```powershell
# Check if port is in use
netstat -ano | findstr :8080

# Kill process using port
taskkill /PID <process-id> /F

# Restart Docker Desktop
```

**Solution (Linux/macOS):**
```bash
# Check port usage
lsof -i :8080

# Kill process
kill -9 <PID>
```

---

### Issue: Gateway Fails to Connect to Redis

**Symptoms:**
```
Unable to connect to Redis at localhost:6379
```

**Check:**
```bash
# Is Redis running?
docker compose ps redis

# Check Redis logs
docker compose logs redis

# Test connection
docker exec -it blackbox-redis redis-cli ping
# Should return: PONG
```

**Fix:**
```bash
# Restart Redis
docker compose restart redis

# Or recreate
docker compose up redis -d --force-recreate
```

---

### Issue: Out of Memory (Windows Docker)

**Symptoms:**
```
docker: Error response: container killed due to memory limit
```

**Fix:**
1. Open Docker Desktop
2. Settings → Resources → Advanced
3. Increase Memory to 4GB minimum
4. Apply & Restart
5. Run `docker compose up` again

---

### Issue: Maven Build Fails

**Symptoms:**
```
[ERROR] Failed to execute goal... compilation failure
```

**Common Causes:**

1. **Wrong Java Version**
```bash
java -version
# Should show Java 21
# If not, install Java 21
```

2. **Dependencies Not Downloaded**
```bash
# Force update
mvn clean install -U
```

3. **IDE Cache Issues (IntelliJ)**
- File → Invalidate Caches → Invalidate and Restart

---

### Issue: k6 Tests Fail with "Connection Refused"

**Symptoms:**
```
error: connection refused
```

**Check:**
```bash
# Is gateway running?
curl http://localhost:8080/actuator/health

# Check gateway logs
docker compose logs gateway
```

**Common Cause:** Gateway still starting up (takes ~30 seconds)

**Fix:** Wait longer or check for errors in logs.

---

### Issue: Grafana Shows "No Data"

**Symptoms:**
All panels show "No data"

**Checklist:**
1. **Is Prometheus scraping gateway?**
```bash
# Check targets
open http://localhost:9090/targets
# Should show "gateway" as UP
```

2. **Is gateway exposing metrics?**
```bash
curl http://localhost:8080/actuator/prometheus | grep gateway_
```

3. **Time range correct?**
- In Grafana, set time range to "Last 5 minutes"

4. **Metrics exist?**
- Generate traffic first (run k6 test)
- Then check Grafana

---

## Platform Differences Summary

| Aspect | Windows | Linux | macOS |
|--------|---------|-------|-------|
| **File Paths** | `C:\path\to\file` | `/path/to/file` | `/path/to/file` |
| **Line Endings** | CRLF (`\r\n`) | LF (`\n`) | LF (`\n`) |
| **curl** | `curl.exe` | `curl` | `curl` |
| **Environment Vars** | `$env:VAR` (PS) | `$VAR` | `$VAR` |
| **Docker VM** | WSL2/Hyper-V | Native | HyperKit |
| **Package Manager** | Chocolatey | apt/dnf | Homebrew |

**Git Configuration (Recommended for All Platforms):**
```bash
# Normalize line endings
git config --global core.autocrlf input  # Linux/Mac
git config --global core.autocrlf true   # Windows
```

---

## Next Steps

1. ✅ **Setup Complete:** Environment ready for development
2. 📖 **Read:** `docs/ONBOARDING.md` for learning path
3. 🔍 **Explore:** `docs/ARCHITECTURE.md` for design decisions
4. 🤝 **Contribute:** `docs/CONTRIBUTING.md` for collaboration
5. 🚀 **Deploy:** See production deployment guide (TBD)

**Happy Coding!** 🎉
