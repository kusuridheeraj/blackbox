# Quick test script for BLACKBOX Gateway
# Run this after `docker compose up -d`

Remove-Item Alias:curl -ErrorAction SilentlyContinue

Write-Host "`n=== BLACKBOX Gateway Test ===" -ForegroundColor Cyan
Write-Host "`n1. Checking gateway health..." -ForegroundColor Yellow
$health = curl -s http://localhost:8080/actuator/health | ConvertFrom-Json
if ($health.status -eq "UP") {
    Write-Host "   [OK] Gateway is UP" -ForegroundColor Green
}
else {
    Write-Host "   [FAIL] Gateway is DOWN" -ForegroundColor Red
    exit 1
}

Write-Host "`n2. Generating JWT token..." -ForegroundColor Yellow
$tokenResponse = curl -s "http://localhost:8080/test/token?clientId=client-1`&tier=STANDARD`&name=TestClient" | ConvertFrom-Json
$TOKEN = $tokenResponse.token
Write-Host "   [OK] Token: $($TOKEN.Substring(0,50))..." -ForegroundColor Green

Write-Host "`n3. Testing authenticated API call..." -ForegroundColor Yellow
$apiResponse = curl -s -w "`n%{http_code}" http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN"
$statusCode = $apiResponse[-1]
$body = $apiResponse[0..($apiResponse.Length - 2)] -join "`n"

if ($statusCode -eq "200") {
    Write-Host "   [OK] API call successful (200 OK)" -ForegroundColor Green
    Write-Host "   Response: $body" -ForegroundColor Gray
}
else {
    Write-Host "   [FAIL] API call failed ($statusCode)" -ForegroundColor Red
    Write-Host "   Response: $body" -ForegroundColor Gray
}

Write-Host "`n4. Testing rate limiting (sending 100 requests)..." -ForegroundColor Yellow
$success = 0
$throttled = 0
for ($i = 0; $i -lt 100; $i++) {
    $code = curl -s -o $null -w "%{http_code}" http://localhost:8080/api/test -H "Authorization: Bearer $TOKEN"
    if ($code -eq "200") { $success++ }
    if ($code -eq "429") { $throttled++ }
}
Write-Host "   [OK] Success: $success | Throttled (429): $throttled" -ForegroundColor Green

Write-Host "`n5. Testing unauthenticated request (should fail)..." -ForegroundColor Yellow
$unauthCode = curl -s -o $null -w "%{http_code}" http://localhost:8080/api/test
if ($unauthCode -eq "401") {
    Write-Host "   [OK] Correctly rejected with 401 Unauthorized" -ForegroundColor Green
}
else {
    Write-Host "   [FAIL] Expected 401, got $unauthCode" -ForegroundColor Red
}

Write-Host "`n=== All Tests Complete ===" -ForegroundColor Cyan
Write-Host "`nNext steps:" -ForegroundColor Yellow
Write-Host "  - Open Grafana: http://localhost:3000 (admin/admin)"
Write-Host "  - Open Prometheus: http://localhost:9090"
Write-Host "  - Full testing guide: docs/TESTING_GUIDE.md"
Write-Host ""
