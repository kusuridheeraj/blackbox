# Contributing to BLACKBOX

**Thank you for considering contributing to BLACKBOX!** This guide will help you understand how to contribute effectively to this open-source API Gateway project.

---

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [How Can I Contribute?](#how-can-i-contribute)
3. [Development Process](#development-process)
4. [Pull Request Process](#pull-request-process)
5. [Coding Standards](#coding-standards)
6. [Testing Requirements](#testing-requirements)
7. [Documentation](#documentation)
8. [Community](#community)

---

## Code of Conduct

### Our Pledge

We are committed to making participation in this project a harassment-free experience for everyone, regardless of experience level, gender, identity, race, religion, or technology choices.

### Our Standards

**Positive behavior:**
- ✅ Using welcoming and inclusive language
- ✅ Respecting differing viewpoints and experiences
- ✅ Gracefully accepting constructive criticism
- ✅ Focusing on what is best for the community

**Unacceptable behavior:**
- ❌ Trolling, insulting comments, personal attacks
- ❌ Public or private harassment
- ❌ Publishing others' private information
- ❌ Other conduct considered inappropriate in a professional setting

### Enforcement

Violations can be reported to the project maintainers. All complaints will be reviewed and investigated promptly and fairly.

---

## How Can I Contribute?

### Reporting Bugs

**Before submitting a bug report:**
1. Check existing [GitHub Issues](https://github.com/your-org/blackbox/issues)
2. Check if it's already fixed in `main` branch
3. Collect information:
   - OS and version (Windows 11, Ubuntu 22.04, macOS 14)
   - Java version (`java -version`)
   - Docker version (`docker --version`)
   - Steps to reproduce
   - Expected vs actual behavior
   - Logs (`docker compose logs gateway`)

**Submit a bug report:**
```markdown
**Title:** [Bug] Short description

**Environment:**
- OS: Windows 11
- Java: OpenJDK 21.0.1
- Docker: 24.0.5

**Steps to Reproduce:**
1. Start services: `docker compose up`
2. Generate token: `curl http://localhost:8080/test/token`
3. Make request: `curl http://localhost:8080/api/test -H "Authorization: Bearer <token>"`

**Expected Behavior:**
Should return 200 OK

**Actual Behavior:**
Returns 429 Too Many Requests immediately

**Logs:**
```
[gateway logs here]
```

**Possible Cause:**
Rate limiter might be using wrong initial token count
```

---

### Suggesting Enhancements

**Good enhancement suggestions include:**
- Clear use case ("As a developer, I want to...")
- Why current approach doesn't work
- Proposed solution
- Alternatives considered

**Example:**
```markdown
**Title:** [Feature] Support for API Key Authentication

**Use Case:**
As a third-party integrator, I want to use API keys instead of JWT tokens for simpler authentication.

**Current Limitation:**
JWT requires token generation and refresh logic. For simple integrations, this is overkill.

**Proposed Solution:**
Add `X-API-Key` header support:
- Store API keys in PostgreSQL
- Hash keys with bcrypt
- Rate limit by API key

**Alternatives:**
1. Keep JWT-only (simpler, but less flexible)
2. Support OAuth2 (too complex)

**Tradeoffs:**
- Adds complexity to auth filter
- Need API key management endpoints
- Simpler for integrators
```

---

### Good First Issues

**Start with these labels:**
- `good-first-issue`: Small, well-defined tasks
- `help-wanted`: Bigger tasks, mentoring available
- `documentation`: Improve docs
- `testing`: Add test coverage

**Examples of good first contributions:**

1. **Add New Rate Limit Tier**
   - Difficulty: Easy
   - Files: `application.yml`
   - Skills: YAML, basic Spring config

2. **Improve Error Messages**
   - Difficulty: Easy
   - Files: `*Filter.java`, `GatewayErrorResponse.java`
   - Skills: Java, JSON

3. **Add Grafana Panel**
   - Difficulty: Medium
   - Files: `blackbox-gateway.json`
   - Skills: PromQL, JSON

4. **Write Integration Test**
   - Difficulty: Medium
   - Files: `src/test/java/integration/`
   - Skills: JUnit, RestAssured

---

## Development Process

### 1. Fork and Clone

```bash
# Fork repository on GitHub (click "Fork" button)

# Clone your fork
git clone https://github.com/YOUR-USERNAME/blackbox.git
cd blackbox

# Add upstream remote
git remote add upstream https://github.com/your-org/blackbox.git

# Verify remotes
git remote -v
```

---

### 2. Create Feature Branch

```bash
# Update main from upstream
git checkout main
git pull upstream main

# Create feature branch
git checkout -b feature/add-enterprise-tier

# Or for bug fix
git checkout -b fix/circuit-breaker-threshold
```

**Branch naming conventions:**
- `feature/description` - New features
- `fix/description` - Bug fixes
- `docs/description` - Documentation
- `refactor/description` - Code refactoring
- `test/description` - Test additions

---

### 3. Make Changes

**Follow these principles:**

1. **Single Responsibility:** Each PR should do ONE thing
   - ✅ Good: "Add ENTERPRISE tier"
   - ❌ Bad: "Add tier, fix bug, update docs, refactor constants"

2. **Small Commits:** Commit often with clear messages
   ```bash
   git commit -m "feat(ratelimit): add ENTERPRISE tier config"
   git commit -m "test(ratelimit): add test for ENTERPRISE tier"
   git commit -m "docs(readme): document ENTERPRISE tier usage"
   ```

3. **Test Locally:** Always test before pushing
   ```bash
   # Rebuild and test
   docker compose up gateway --build -d
   k6 run k6/baseline.js
   
   # Check Grafana
   open http://localhost:3000
   ```

---

### 4. Keep Branch Updated

```bash
# Fetch latest from upstream
git fetch upstream

# Rebase on upstream/main
git rebase upstream/main

# Resolve conflicts if any
git add <resolved-files>
git rebase --continue

# Force push to your fork
git push origin feature/add-enterprise-tier --force
```

---

## Pull Request Process

### Before Creating PR

**Checklist:**
- [ ] Code follows [Coding Standards](#coding-standards)
- [ ] All tests pass
- [ ] Added tests for new functionality
- [ ] Updated documentation
- [ ] No breaking changes (or documented)
- [ ] Commit messages follow convention
- [ ] Branch is up-to-date with main

---

### Creating the PR

**Title Format:**
```
<type>(<scope>): <short description>
```

**Examples:**
- `feat(ratelimit): add ENTERPRISE tier support`
- `fix(auth): handle expired JWT gracefully`
- `docs(architecture): add decision records for Redis choice`

**PR Description Template:**
````markdown
## What does this PR do?

[Clear one-line summary]

## Why are we doing this?

[Problem statement or feature request]

## How was this tested?

- [ ] Unit tests passing (`mvn test`)
- [ ] Integration tests passing
- [ ] Manual testing:
  ```bash
  # Steps taken
  docker compose up --build
  curl http://localhost:8080/test/token...
  k6 run k6/baseline.js
  ```
- [ ] Grafana metrics verified

## Screenshots / Logs

[If applicable, include before/after screenshots or relevant logs]

## Breaking Changes

[List any breaking changes or "N/A"]

## Checklist

- [ ] Code follows style guide
- [ ] Self-reviewed my code
- [ ] Commented hard-to-understand areas
- [ ] Updated documentation
- [ ] No new warnings
- [ ] Added tests that prove fix/feature works
- [ ] New and existing tests pass locally
````

---

### Review Process

**What reviewers look for:**

1. **Correctness:**
   - Does code do what it claims?
   - Are edge cases handled?
   - Are there bugs?

2. **Design:**
   - Is this the right approach?
   - Does it fit the architecture?
   - Are there simpler alternatives?

3. **Readability:**
   - Are variable names clear?
   - Are complex sections commented?
   - Is the code self-documenting?

4. **Tests:**
   - Do tests cover the functionality?
   - Are edge cases tested?
   - Are tests clear and maintainable?

**Responding to feedback:**
- ✅ Be receptive to suggestions
- ✅ Ask for clarification if unclear
- ✅ Discuss alternatives professionally
- ❌ Don't take feedback personally
- ❌ Don't ignore requested changes

**Making changes:**
```bash
# Make requested changes
git add <files>
git commit -m "fix: address review feedback"
git push origin feature/add-enterprise-tier
```

---

### Merge Requirements

**Before merge:**
- ✅ 2 approvals from maintainers
- ✅ All CI checks passing
- ✅ No unresolved conversations
- ✅ Branch up-to-date with main

**After merge:**
```bash
# Update your local main
git checkout main
git pull upstream main

# Delete feature branch
git branch -d feature/add-enterprise-tier
git push origin --delete feature/add-enterprise-tier
```

---

## Coding Standards

### Java Code Style

**Follow Google Java Style Guide:**
- https://google.github.io/styleguide/javaguide.html

**Key points:**

1. **Indentation:** 4 spaces (not tabs)
2. **Line length:** 120 characters max
3. **Naming:**
   - Classes: `PascalCase` (e.g., `RateLimitFilter`)
   - Methods: `camelCase` (e.g., `allowRequest`)
   - Constants: `UPPER_SNAKE_CASE` (e.g., `FAILURE_THRESHOLD`)
   - Packages: `lowercase` (e.g., `com.blackbox.gateway`)

4. **Braces:** Always use, even for single-line if
   ```java
   // ✅ Good
   if (condition) {
       doSomething();
   }
   
   // ❌ Bad
   if (condition)
       doSomething();
   ```

5. **Annotations:** One per line
   ```java
   // ✅ Good
   @Service
   @Slf4j
   @RequiredArgsConstructor
   public class MyService {
   
   // ❌ Bad
   @Service @Slf4j @RequiredArgsConstructor
   public class MyService {
   ```

---

### Comments and Documentation

**When to comment:**

1. **Why, not What:**
   ```java
   // ✅ Good - Explains WHY
   // Use Lua script for atomicity. GET + DECR is not atomic and can have race conditions.
   String luaScript = """...""";
   
   // ❌ Bad - States WHAT (obvious from code)
   // Call Redis
   redisTemplate.execute(script);
   ```

2. **Complex Logic:**
   ```java
   // ✅ Good
   // Token bucket algorithm: refill rate = 500 tokens/sec, capacity = 750 tokens.
   // This allows bursts up to 750 req, then sustains at 500 req/s.
   long addTokens = elapsed * refillRate;
   tokens = Math.min(capacity, tokens + addTokens);
   ```

3. **Important Decisions:**
   ```java
   // ✅ Good
   // Use 30-second cooldown instead of 5 min. Backend typically recovers within seconds.
   // Longer cooldown frustrates users waiting for service restoration.
   private static final long COOLDOWN_MS = 30_000;
   ```

**Javadoc for public APIs:**
```java
/**
 * Checks if a request to the specified route should be allowed based on circuit state.
 *
 * @param routeId the unique identifier for the route
 * @return true if request can proceed, false if circuit is open
 * @throws IllegalArgumentException if routeId is null
 */
public boolean allowRequest(String routeId) {
    // ...
}
```

---

### Logging Best Practices

**Log Levels:**

| Level | When to Use | Example |
|-------|-------------|---------|
| **ERROR** | Unrecoverable errors | `log.error("Failed to connect to Redis: {}", e.getMessage())` |
| **WARN** | Recoverable issues | `log.warn("Circuit breaker OPEN for route: {}", routeId)` |
| **INFO** | Important events | `log.info("Adaptive mode changed: {} → {}", old, new)` |
| **DEBUG** | Debugging details | `log.debug("Token count: {}", tokens)` |

**Structured Logging:**
```java
// ✅ Good - Structured, searchable
log.info("Rate limit adjustment: from={} to={} errorRate={} multiplier={}",
    previousMode, newMode, errorRate, multiplier);

// ❌ Bad - Unstructured, hard to parse
log.info("Adjusted from " + previousMode + " to " + newMode);
```

**Performance:**
```java
// ✅ Good - Only build message if DEBUG enabled
log.debug("Processing request: {}", () -> expensiveDebugString());

// ❌ Bad - Always builds string
log.debug("Processing request: " + expensiveDebugString());
```

---

## Testing Requirements

### Test Coverage Expectations

**Minimum coverage:**
- New features: 80% coverage
- Bug fixes: Test that proves fix works
- Refactors: Maintain existing coverage

---

### Unit Tests

**Example:**
```java
@SpringBootTest
class RateLimitFilterTest {
    
    @Test
    void shouldAllowRequestWhenTokensAvailable() {
        // Given
        RateLimitFilter filter = new RateLimitFilter(rateLimiter, metrics);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("clientId", "test-client");
        request.setAttribute("tier", "PREMIUM");
        
        // When
        boolean allowed = filter.checkRateLimit(request);
        
        // Then
        assertTrue(allowed);
        verify(metrics).recordRequest(eq("test-client"), eq(true));
    }
    
    @Test
    void shouldRejectRequestWhenNoTokensAvailable() {
        // Given: Exhaust token bucket
        for (int i = 0; i < 750; i++) {
            rateLimiter.tryConsume("test-client", "PREMIUM");
        }
        
        // When
        boolean allowed = rateLimiter.tryConsume("test-client", "PREMIUM");
        
        // Then
        assertFalse(allowed);
    }
}
```

---

### Integration Tests (with k6)

**Create test files:** `k6/feature-name-test.js`

```javascript
import http from 'k6/http';
import { check, group } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 10 },  // Ramp up
        { duration: '30s', target: 10 },  // Sustained
        { duration: '10s', target: 0 },   // Ramp down
    ],
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    // Generate token
    const res = http.get(`${BASE_URL}/test/token?clientId=feature-test&tier=ENTERPRISE`);
    return { token: res.json('token') };
}

export default function (data) {
    group('ENTERPRISE tier test', () => {
        const res = http.get(`${BASE_URL}/api/test`, {
            headers: { 'Authorization': `Bearer ${data.token}` },
        });
        
        check(res, {
            'status is 200': (r) => r.status === 200,
            'latency < 100ms': (r) => r.timings.duration < 100,
            'no throttling': (r) => r.status !== 429,
        });
    });
}
```

---

## Documentation

### What to Document

**Code changes:**
- [ ] Update relevant `docs/*.md` files
- [ ] Add inline comments for complex logic
- [ ] Update `README.md` if user-facing

**New features:**
- [ ] Add section to `docs/ONBOARDING.md`
- [ ] Update `docs/ARCHITECTURE.md` with design decisions
- [ ] Add example to `docs/DEVELOPMENT.md`

**Configuration changes:**
- [ ] Document in `application.yml` comments
- [ ] Update `docs/DEVELOPMENT.md` setup section

---

### Documentation Style

**Be concise but complete:**
```markdown
✅ Good:
## Rate Limiting
BLACKBOX uses token bucket algorithm with Redis storage for distributed rate limiting.

**Configuration:**
- `requestsPerSecond`: Sustained rate (e.g., 500)
- `burstSize`: Maximum burst (e.g., 750)

❌ Bad:
## Rate Limiting
We do rate limiting.
```

**Use examples:**
```markdown
✅ Good:
### Adding a New Tier

edit `application.yml`:
yaml
gateway:
  ratelimit:
    tiers:
      ENTERPRISE:
        requestsPerSecond: 1000
        burstSize: 1500


❌ Bad:
### Adding a New Tier
Edit the config file.
```

---

## Community

### Communication Channels

- **GitHub Issues:** Bug reports, feature requests
- **GitHub Discussions:** General questions, ideas
- **Pull Requests:** Code review, technical discussion

### Getting Help

**Before asking:**
1. Check `docs/` folder
2. Search existing issues
3. Read relevant blog posts in `docs/`

**When asking:**
- Be specific
- Include context (what you tried)
- Share logs/errors
- Mention OS and versions

**Example:**
```markdown
**Question:** How do I customize circuit breaker thresholds?

**Context:**
I want to open circuit after 10 failures instead of 5.

**What I Tried:**
- Read CircuitBreaker.java
- Searched for "threshold" in code
- Found FAILURE_THRESHOLD constant

**Specific Question:**
Should I change the constant or make it configurable via `application.yml`?
```

---

## Recognition

**Contributors will be:**
- ✨ Listed in `CONTRIBUTORS.md`
- 🏆 Credited in release notes
- 💌 Thanked publicly

**Significant contributions may earn:**
- 🎖️ Maintainer role
- 🔑 Triage permissions
- 🎤 Co-author on blog posts

---

## License

By contributing, you agree that your contributions will be licensed under the same license as the project (MIT License).

---

## Thank You!

**Every contribution matters:**
- 🐛 Reporting bugs improves quality
- 📖 Improving docs helps newcomers
- 💡 Suggesting features drives innovation
- 💻 Writing code builds the future

**Welcome to the BLACKBOX community!** 🚀

---

**Questions?**
- Read: `docs/ONBOARDING.md`
- Ask: GitHub Discussions
- Email: maintainers@example.com
