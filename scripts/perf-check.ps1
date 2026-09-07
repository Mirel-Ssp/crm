# Performance spot check (Batch 3 exit criteria): seed 500 customers via psql,
# measure page-query API P95 latency, then clean up seeded rows.
# Usage: powershell -ExecutionPolicy Bypass -File perf-check.ps1  (backend on 8080)
# Batch 6 (B6-07): -Scale parameter added. Default 5000 (same criteria as batch 5); -Scale 10000 for ten-thousand-level verification.
#   page-query seeds = Scale/10 (customers/trade orders); funnel/stat seeds = Scale (opportunities/stat orders)
param([int]$Scale = 5000)
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080/api'
$psql = 'C:\Users\21925\.local\share\crm-pgsql\bin\psql.exe'
$env:PGPASSWORD = 'crm_dev_2026'
$pageScale = [math]::Max(100, [int]($Scale / 10))
Write-Host "INFO  perf scale: opps/stat-orders=$Scale, page-query rows=$pageScale"

# 0. backend health
try { $health = Invoke-RestMethod -Uri "$base/health" -TimeoutSec 5 } catch { Write-Host 'FAIL  backend not reachable on 8080'; exit 1 }
if ($health.data.status -ne 'UP') { Write-Host 'FAIL  backend health not UP'; exit 1 }

# 1. login
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}'
$h = @{ Authorization = "Bearer $($login.data.accessToken)" }

# 2. seed page-scale customers (unique marker; id base beyond current max to avoid collision)
$marker = "perf" + (Get-Date -Format 'yyyyMMddHHmmss')
$seedSql = "INSERT INTO crm_customer (id, name, level, industry, owner_id, status, lifecycle_status, created_by, updated_by, deleted) SELECT (SELECT COALESCE(MAX(id),0) FROM crm_customer) + 1000000 + g, 'perf-$marker-' || g, CASE g % 3 WHEN 0 THEN 'VIP' WHEN 1 THEN 'IMPORTANT' ELSE 'NORMAL' END, 'IT', 1, 'ACTIVE', 'FOLLOWING', 1, 1, 0 FROM generate_series(1,$pageScale) g;"
& $psql -h 127.0.0.1 -p 5432 -U crm -d crm -v ON_ERROR_STOP=1 -c $seedSql | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host "FAIL  seed $pageScale customers via psql"; exit 1 }
Write-Host "PASS  seed $pageScale customers"

# 3. measure 60 page requests (keyword search over seeded set, pageSize=8 => 60 pages all hit data)
$times = @()
for ($i = 1; $i -le 60; $i++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-RestMethod -Uri "$base/customers?pageNum=$i&pageSize=8&keyword=perf-$marker" -Headers $h | Out-Null
    $sw.Stop()
    $times += $sw.Elapsed.TotalMilliseconds
}
$sorted = $times | Sort-Object
$p50 = [math]::Round($sorted[29], 1)
$p95 = [math]::Round($sorted[56], 1)
$max = [math]::Round($sorted[-1], 1)
Write-Host "INFO  page query latency ms: P50=$p50 P95=$p95 MAX=$max"
if ($p95 -lt 500) { Write-Host "PASS  P95 < 500ms (P95=$p95)" } else { Write-Host "FAIL  P95 >= 500ms (P95=$p95)" }

# 4. cleanup seeded rows
$cleanSql = "DELETE FROM crm_customer WHERE name LIKE 'perf-$marker-%';"
& $psql -h 127.0.0.1 -p 5432 -U crm -d crm -v ON_ERROR_STOP=1 -c $cleanSql | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host 'PASS  cleanup seeded rows' } else { Write-Host 'FAIL  cleanup' }

# ==================== Batch 4: trade orders page-query P95 ====================

# 5. seed 500 trade orders referencing one real item + one real customer (FK-safe)
$marker2 = "perf" + (Get-Date -Format 'yyyyMMddHHmmss')
$seedOrderSql = @"
INSERT INTO trade_order (id, order_no, item_id, customer_id, owner_id, direction, quantity, price, amount, fee_rate, fee_amount, total_amount, status, version, created_by, updated_by, deleted)
SELECT (SELECT COALESCE(MAX(id),0) FROM trade_order) + 1000000 + g,
       'TO-$marker2-' || lpad(g::text, 6, '0'),
       (SELECT MIN(id) FROM trade_item),
       (SELECT MIN(id) FROM crm_customer),
       1, 'BUY', 1, 100, 100, 0, 0, 100, 'CONFIRMED', 0, 1, 1, 0
FROM generate_series(1,$pageScale) g;
"@
& $psql -h 127.0.0.1 -p 5432 -U crm -d crm -v ON_ERROR_STOP=1 -c $seedOrderSql | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host "FAIL  seed $pageScale trade orders via psql"; exit 1 }
Write-Host "PASS  seed $pageScale trade orders"

# 6. measure 60 trade-order page requests (keyword search, pageSize=8)
$times2 = @()
for ($i = 1; $i -le 60; $i++) {
    $sw2 = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-RestMethod -Uri "$base/trade/orders?pageNum=$i&pageSize=8&keyword=$marker2" -Headers $h | Out-Null
    $sw2.Stop()
    $times2 += $sw2.Elapsed.TotalMilliseconds
}
$sorted2 = $times2 | Sort-Object
$p50b = [math]::Round($sorted2[29], 1)
$p95b = [math]::Round($sorted2[56], 1)
$maxb = [math]::Round($sorted2[-1], 1)
Write-Host "INFO  trade order page latency ms: P50=$p50b P95=$p95b MAX=$maxb"
if ($p95b -lt 500) { Write-Host "PASS  trade P95 < 500ms (P95=$p95b)" } else { Write-Host "FAIL  trade P95 >= 500ms (P95=$p95b)" }

# 7. cleanup seeded trade orders
$cleanOrderSql = "DELETE FROM trade_order WHERE order_no LIKE 'TO-$marker2-%';"
& $psql -h 127.0.0.1 -p 5432 -U crm -d crm -v ON_ERROR_STOP=1 -c $cleanOrderSql | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host 'PASS  cleanup trade orders' } else { Write-Host 'FAIL  trade cleanup' }

# ==================== Batch 5→6: funnel/report + STAT on Scale rows ====================

# 8. seed $Scale opportunities spread over 7 stages (funnel aggregation workload)
$marker3 = "perf" + (Get-Date -Format 'yyyyMMddHHmmss')
$seedOppSql = @"
INSERT INTO crm_opportunity (id, customer_id, name, stage, amount, expected_date, owner_id, status, created_by, updated_by, deleted)
SELECT (SELECT COALESCE(MAX(id),0) FROM crm_opportunity) + 1000000 + g,
       (SELECT MIN(id) FROM crm_customer),
       'perf-$marker3-' || lpad(g::text, 6, '0'),
       (g % 7) + 1,
       1000 + (g % 50) * 100,
       CURRENT_DATE + ((g % 60) - 30),
       1,
       CASE WHEN (g % 7) + 1 = 6 THEN 'WON' WHEN (g % 7) + 1 = 7 THEN 'LOST' ELSE 'OPEN' END,
       1, 1, 0
FROM generate_series(1,$Scale) g;
"@
& $psql -h 127.0.0.1 -p 5432 -U crm -d crm -v ON_ERROR_STOP=1 -c $seedOppSql | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host "FAIL  seed $Scale opportunities via psql"; exit 1 }
Write-Host "PASS  seed $Scale opportunities"

# 9. measure funnel + opportunity report latency (60 rounds each)
function Measure-P95([string]$uri, [int]$rounds) {
    $ts = @()
    for ($i = 1; $i -le $rounds; $i++) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        Invoke-RestMethod -Uri $uri -Headers $h | Out-Null
        $sw.Stop()
        $ts += $sw.Elapsed.TotalMilliseconds
    }
    $s = $ts | Sort-Object
    return @{ P50 = [math]::Round($s[[int]($rounds/2)-1], 1); P95 = [math]::Round($s[[int][math]::Ceiling($rounds*0.95)-1], 1); MAX = [math]::Round($s[-1], 1) }
}

$r1 = Measure-P95 "$base/opps/funnel" 60
Write-Host "INFO  funnel latency ms on $Scale opps: P50=$($r1.P50) P95=$($r1.P95) MAX=$($r1.MAX)"
if ($r1.P95 -lt 500) { Write-Host "PASS  funnel P95 < 500ms (P95=$($r1.P95))" } else { Write-Host "FAIL  funnel P95 >= 500ms (P95=$($r1.P95))" }

$r2 = Measure-P95 "$base/reports/opportunity" 60
Write-Host "INFO  opportunity report latency ms: P50=$($r2.P50) P95=$($r2.P95) MAX=$($r2.MAX)"
if ($r2.P95 -lt 500) { Write-Host "PASS  report P95 < 500ms (P95=$($r2.P95))" } else { Write-Host "FAIL  report P95 >= 500ms (P95=$($r2.P95))" }

# 10. cleanup seeded opportunities
$cleanOppSql = "DELETE FROM crm_opportunity WHERE name LIKE 'perf-$marker3-%';"
& $psql -h 127.0.0.1 -p 5432 -U crm -d crm -v ON_ERROR_STOP=1 -c $cleanOppSql | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host 'PASS  cleanup opportunities' } else { Write-Host 'FAIL  opportunity cleanup' }

# 11. seed $Scale CONFIRMED orders across 30 days for STAT aggregation workload
$marker4 = "perf" + (Get-Date -Format 'yyyyMMddHHmmss')
$seedStatSql = @"
INSERT INTO trade_order (id, order_no, item_id, customer_id, owner_id, direction, quantity, price, amount, fee_rate, fee_amount, total_amount, status, version, created_by, updated_by, created_at, updated_at, deleted)
SELECT (SELECT COALESCE(MAX(id),0) FROM trade_order) + 1000000 + g,
       'TO-$marker4-' || lpad(g::text, 6, '0'),
       (SELECT MIN(id) FROM trade_item),
       (SELECT MIN(id) FROM crm_customer),
       1, 'BUY', 1, 100, 100, 0, 0, 100, 'CONFIRMED', 0, 1, 1,
       CURRENT_DATE - (g % 30) + time '10:00', CURRENT_DATE - (g % 30) + time '10:00', 0
FROM generate_series(1,$Scale) g;
"@
& $psql -h 127.0.0.1 -p 5432 -U crm -d crm -v ON_ERROR_STOP=1 -c $seedStatSql | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host "FAIL  seed $Scale stat orders via psql"; exit 1 }
Write-Host "PASS  seed $Scale stat orders (30-day spread)"

# 12. rebuild stat aggregation tables, then measure summary/rank/items (P95 < 300ms, ST-1)
$rebuildBody = Invoke-RestMethod -Method Post -Uri "$base/stat/rebuild" -Headers $h
Write-Host "INFO  stat rebuild rows: $($rebuildBody.data)"
$r3 = Measure-P95 "$base/stat/summary?dim=DAY" 30
Write-Host "INFO  stat summary latency ms: P50=$($r3.P50) P95=$($r3.P95) MAX=$($r3.MAX)"
if ($r3.P95 -lt 300) { Write-Host "PASS  stat summary P95 < 300ms (P95=$($r3.P95))" } else { Write-Host "FAIL  stat summary P95 >= 300ms (P95=$($r3.P95))" }

$r4 = Measure-P95 "$base/stat/rank?dim=DAY" 30
Write-Host "INFO  stat rank latency ms: P50=$($r4.P50) P95=$($r4.P95) MAX=$($r4.MAX)"
if ($r4.P95 -lt 300) { Write-Host "PASS  stat rank P95 < 300ms (P95=$($r4.P95))" } else { Write-Host "FAIL  stat rank P95 >= 300ms (P95=$($r4.P95))" }

$r5 = Measure-P95 "$base/stat/items?dim=DAY" 30
Write-Host "INFO  stat items latency ms: P50=$($r5.P50) P95=$($r5.P95) MAX=$($r5.MAX)"
if ($r5.P95 -lt 300) { Write-Host "PASS  stat items P95 < 300ms (P95=$($r5.P95))" } else { Write-Host "FAIL  stat items P95 >= 300ms (P95=$($r5.P95))" }

# 13. cleanup seeded stat orders and rebuild aggregation to restore baseline
$cleanStatSql = "DELETE FROM trade_order WHERE order_no LIKE 'TO-$marker4-%';"
& $psql -h 127.0.0.1 -p 5432 -U crm -d crm -v ON_ERROR_STOP=1 -c $cleanStatSql | Out-Null
if ($LASTEXITCODE -eq 0) { Write-Host 'PASS  cleanup stat orders' } else { Write-Host 'FAIL  stat cleanup' }
Invoke-RestMethod -Method Post -Uri "$base/stat/rebuild" -Headers $h | Out-Null
Write-Host 'PASS  stat aggregation restored'
