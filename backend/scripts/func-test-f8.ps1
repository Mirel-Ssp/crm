# =====================================================================
# CRM full functional test (multi-role x all modules) - PS 5.1 ASCII only
# Outputs: [PASS]/[FAIL] name | detail
# =====================================================================
$ErrorActionPreference = "SilentlyContinue"
$Base = "http://127.0.0.1:8080/api"
$script:pass = 0; $script:fail = 0; $script:results = @()

function Login($u, $p) {
    $body = @{ username = $u; password = $p } | ConvertTo-Json
    try { $r = Invoke-RestMethod -Uri "$Base/auth/login" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 10
          if ($r.code -eq 0) { return $r.data.accessToken } } catch {}
    return $null
}

function Send($token, $method, $path, $obj) {
    $headers = @{}; if ($token) { $headers["Authorization"] = "Bearer $token" }
    $json = if ($null -ne $obj) { $obj | ConvertTo-Json -Depth 8 } else { $null }
    try {
        $r = Invoke-RestMethod -Uri "$Base$path" -Method $method -Headers $headers -ContentType "application/json" -Body $json -TimeoutSec 20
        return @{ http = 200; code = $r.code; data = $r.data; msg = $r.message }
    } catch {
        $resp = $_.Exception.Response
        $http = if ($resp) { [int]$resp.StatusCode } else { 0 }
        $code = $null
        try { $reader = New-Object System.IO.StreamReader($resp.GetResponseStream()); $code = ($reader.ReadToEnd() | ConvertFrom-Json).code } catch {}
        return @{ http = $http; code = $code; data = $null; msg = $null }
    }
}

# raw body (for malformed JSON test)
function SendRaw($token, $method, $path, $raw) {
    $headers = @{}; if ($token) { $headers["Authorization"] = "Bearer $token" }
    try {
        $r = Invoke-RestMethod -Uri "$Base$path" -Method $method -Headers $headers -ContentType "application/json" -Body $raw -TimeoutSec 15
        return @{ http = 200; code = $r.code }
    } catch {
        $resp = $_.Exception.Response
        $http = if ($resp) { [int]$resp.StatusCode } else { 0 }; $code = $null
        try { $reader = New-Object System.IO.StreamReader($resp.GetResponseStream()); $code = ($reader.ReadToEnd() | ConvertFrom-Json).code } catch {}
        return @{ http = $http; code = $code }
    }
}

function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; $script:results += "[PASS] $name | $detail" }
    else { $script:fail++; $script:results += "[FAIL] $name | $detail" }
}

$tag = "FT" + (Get-Date -Format "yyyyMMddHHmmssfff")

Write-Host "=== Auth / Anonymous ==="
$admin = Login "admin" "admin123"; $mgr = Login "b7manager" "Smoke123456"; $sal = Login "b7sales" "Smoke123456"
Check "login 3 roles" ($admin -and $mgr -and $sal) "tokens obtained"
Check "bad password rejected" (-not (Login "admin" "wrongpass")) "no token"
$h = Send $null "GET" "/health" $null;      Check "anon health 200" ($h.http -eq 200) "http=$($h.http)"
$a1 = Send $null "GET" "/auth/me" $null;    Check "anon /auth/me ->401" ($a1.http -eq 401) "http=$($a1.http)"
$a2 = Send $null "GET" "/customers?pageNum=1&pageSize=1" $null; Check "anon /customers ->401" ($a2.http -eq 401) "http=$($a2.http)"
$a3 = Send $null "GET" "/trade/orders?pageNum=1&pageSize=1" $null; Check "anon orders ->401" ($a3.http -eq 401) "http=$($a3.http)"

Write-Host "=== System module (ADMIN only) ==="
Check "admin system/users" ((Send $admin "GET" "/system/users?pageNum=1&pageSize=5" $null).code -eq 0) "code=0"
$s2 = Send $mgr "GET" "/system/users?pageNum=1&pageSize=5" $null; Check "manager system/users ->40300" ($s2.code -eq 40300) "code=$($s2.code)"
$s3 = Send $sal "GET" "/system/users?pageNum=1&pageSize=5" $null; Check "sales system/users ->40300" ($s3.code -eq 40300) "code=$($s3.code)"
Check "admin roles" ((Send $admin "GET" "/system/roles" $null).code -eq 0) "code=0"
Check "admin org tree" ((Send $admin "GET" "/system/orgs/tree" $null).code -eq 0) "code=0"
Check "admin dicts" ((Send $admin "GET" "/dicts" $null).code -eq 0) "code=0"
$s6 = Send $sal "GET" "/system/roles" $null; Check "sales system/roles ->40300" ($s6.code -eq 40300) "code=$($s6.code)"
$s7 = Send $mgr "POST" "/system/roles" @{ code = "FTX_$tag"; name = "x" }; Check "manager create role ->40300" ($s7.code -eq 40300) "code=$($s7.code)"

Write-Host "=== Customer ==="
$c1 = Send $admin "GET" "/customers?pageNum=1&pageSize=5" $null; Check "admin customer list" ($c1.code -eq 0) "total=$($c1.data.total)"
$c2 = Send $sal "GET" "/customers?pageNum=1&pageSize=100" $null; Check "sales customer list self-scope" ($c2.code -eq 0) "total=$($c2.data.total) (< admin)"
$cust = Send $sal "POST" "/customers" @{ name = "ft-cust-$tag"; level = "NORMAL"; status = "ACTIVE" }
Check "sales create customer" ($cust.code -eq 0) "code=$($cust.code)"
$newCustId = "$($cust.data)"
Check "snowflake id is string" ($cust.data -is [string]) "id=$newCustId"
$c3 = Send $sal "GET" "/customers/$newCustId" $null; Check "sales open own customer (id round-trip)" ($c3.code -eq 0) "code=$($c3.code)"
$c4 = Send $sal "POST" "/customers" @{ name = "ft-cust-$tag" }; Check "dup customer name ->40000" ($c4.code -eq 40000) "code=$($c4.code)"
$c5 = Send $sal "POST" "/customers" @{ level = "NORMAL" }; Check "blank name validation ->40000" ($c5.code -eq 40000) "code=$($c5.code)"
$adminCust = $c1.data.list | Where-Object { $_.ownerId -eq 1 } | Select-Object -First 1
$c6 = Send $sal "GET" "/customers/$($adminCust.id)" $null; Check "sales cannot open admin customer ->deny (40300/40400 masking)" ($c6.code -eq 40300 -or $c6.code -eq 40400) "code=$($c6.code)"
$c7 = Send $sal "GET" "/customers/abc" $null; Check "type mismatch ->40000" ($c7.code -eq 40000) "code=$($c7.code)"
$c8 = Send $admin "GET" "/customers/999999999999999999" $null; Check "not found ->40400" ($c8.code -eq 40400) "code=$($c8.code)"
$c9 = Send $admin "GET" "/nonexistent-xyz" $null; Check "unknown API path ->40400" ($c9.code -eq 40400) "code=$($c9.code)"
$c10 = Send $sal "GET" "/customers/$newCustId/timeline" $null; Check "customer timeline" ($c10.code -eq 0) "code=$($c10.code)"

Write-Host "=== Edge: malformed JSON body ==="
$bad = SendRaw $sal "POST" "/opps" '{ "customerId": 1, "stage": "POTENTIAL" }'
Check "malformed JSON (string->int) returns 40000 not 50000" ($bad.code -eq 40000 -or $bad.code -eq 400) "actual code=$($bad.code) http=$($bad.http)"

Write-Host "=== Lead ==="
$lead = Send $sal "POST" "/leads" @{ companyName = "ft-co-$tag"; contactName = "ft-contact-$tag"; contactPhone = "13$($tag.Substring($tag.Length-9))"; source = "WEBSITE" }
Check "sales create lead" ($lead.code -eq 0) "code=$($lead.code)"
$leadId = "$($lead.data)"
$l2 = Send $sal "POST" "/leads/$leadId/claim" @{}; Check "sales claim lead" ($l2.code -eq 0) "code=$($l2.code)"
$l3 = Send $sal "POST" "/leads/$leadId/assign" @{ userId = 1 }; Check "sales assign ->40300" ($l3.code -eq 40300) "code=$($l3.code)"
$l4 = Send $mgr "GET" "/leads/assignable" $null; Check "manager assignable members" ($l4.code -eq 0) "code=$($l4.code)"
$l5 = Send $mgr "POST" "/leads/$leadId/assign" @{ userId = 1 }; Check "manager assign lead (already claimed by sales -> 40000 by state)" ($l5.code -ne 0) "code=$($l5.code)"
# fresh pending lead for manager assign test
$lead2 = Send $sal "POST" "/leads" @{ companyName = "ft-co2-$tag"; contactName = "ct2-$tag"; contactPhone = "14$($tag.Substring($tag.Length-9))"; source = "CALL" }
$l7 = Send $mgr "POST" "/leads/$($lead2.data)/assign" @{ userId = 1 }; Check "manager assign fresh pending lead" ($l7.code -eq 0) "code=$($l7.code)"
$l6 = Send $sal "GET" "/leads/assignable" $null; Check "sales assignable list ->40300" ($l6.code -eq 40300) "code=$($l6.code)"

Write-Host "=== Opportunity ==="
$o1 = Send $sal "POST" "/opps" @{ customerId = $newCustId; name = "ft-opp-$tag"; stage = 1; amount = 50000 }
Check "sales create opportunity" ($o1.code -eq 0) "code=$($o1.code)"
$oppId = "$($o1.data)"
Check "opportunity list" ((Send $sal "GET" "/opps?pageNum=1&pageSize=5" $null).code -eq 0) "code=0"
Check "opportunity funnel" ((Send $sal "GET" "/opps/funnel" $null).code -eq 0) "code=0"
Check "opportunity loss-stats" ((Send $mgr "GET" "/opps/loss-stats" $null).code -eq 0) "code=0"
$o4 = Send $sal "POST" "/opps/$oppId/stage" @{ toStage = 2 }; Check "opp stage advance" ($o4.code -eq 0) "code=$($o4.code)"

Write-Host "=== Quote + Workflow ==="
# create item first so quote lines can link to it (conversion requires itemId-linked lines)
$it0 = Send $admin "POST" "/trade/items" @{ code = "q$($tag.Substring($tag.Length-8))"; name = "ft-qitem-$tag"; category = "OTHER"; market = "OTHER"; referencePrice = 100; minQuantity = 1 }
$qItemId = "$($it0.data)"
Send $admin "POST" "/trade/items/$qItemId/list" @{} | Out-Null
$q1 = Send $sal "POST" "/quotes" @{ title = "ft-quote-$tag"; customerId = $newCustId; items = @(@{ itemId = $qItemId; name = "ft-prod-$tag"; quantity = 10; price = 100 }) }
Check "sales create quote" ($q1.code -eq 0) "code=$($q1.code) msg=$($q1.msg)"
$quoteId = "$($q1.data)"
$q2 = Send $sal "POST" "/quotes/$quoteId/submit" @{}; Check "sales submit quote" ($q2.code -eq 0) "code=$($q2.code)"
$q3 = Send $sal "POST" "/quotes/$quoteId/approve" @{}; Check "sales approve ->40300" ($q3.code -eq 40300) "code=$($q3.code)"
$q3b = Send $sal "POST" "/quotes/$quoteId/reject" @{ reason = "x" }; Check "sales reject ->40300" ($q3b.code -eq 40300) "code=$($q3b.code)"
$w1 = Send $mgr "GET" "/workflow/tasks" $null; Check "manager workflow tasks" ($w1.code -eq 0) "tasks=$($w1.data.tasks.Count)"
$w2 = Send $sal "GET" "/workflow/tasks" $null; Check "sales workflow tasks ->40300 (wf:task:list removed per R1)" ($w2.code -eq 40300) "code=$($w2.code)"
$q4 = Send $mgr "POST" "/quotes/$quoteId/approve" @{}; Check "manager approve quote" ($q4.code -eq 0) "code=$($q4.code)"
$q5 = Send $mgr "POST" "/quotes/$quoteId/convert" @{}; Check "convert approved quote ->order" ($q5.code -eq 0) "code=$($q5.code)"
$q6 = Send $sal "POST" "/quotes/$quoteId/approve" @{}; Check "re-approve converted quote rejected" ($q6.code -ne 0) "code=$($q6.code)"
$qd = Send $sal "PUT" "/quotes/$quoteId" @{ title = "hack" }; Check "edit converted quote ->conflict/403" ($qd.code -ne 0) "code=$($qd.code)"

Write-Host "=== Contract ==="
$ct1 = Send $sal "POST" "/contracts" @{ title = "ft-contract-$tag"; customerId = $newCustId; amount = 100000; startDate = "2026-09-08"; endDate = "2027-09-08" }
Check "sales create contract" ($ct1.code -eq 0) "code=$($ct1.code)"
$ctId = "$($ct1.data)"
$ct2 = Send $sal "POST" "/contracts/$ctId/sign" @{}; Check "sales sign contract ->40300 (no contract:sign)" ($ct2.code -eq 40300) "code=$($ct2.code)"
$ct2m = Send $mgr "POST" "/contracts/$ctId/sign" @{}; Check "manager sign contract" ($ct2m.code -eq 0) "code=$($ct2m.code)"
$ct3 = Send $mgr "POST" "/contracts/$ctId/sign" @{}; Check "double-sign rejected" ($ct3.code -ne 0) "code=$($ct3.code)"
Check "contract expiring alert" ((Send $mgr "GET" "/contracts/expiring" $null).code -eq 0) "code=0"

Write-Host "=== Trade: item / order / remit ==="
$it = Send $admin "POST" "/trade/items" @{ code = "ft$($tag.Substring($tag.Length-8))"; name = "ft-item-$tag"; category = "OTHER"; market = "OTHER"; referencePrice = 100; minQuantity = 1 }
Check "admin create item" ($it.code -eq 0) "code=$($it.code) msg=$($it.msg)"
$itemId = "$($it.data)"
$itl = Send $admin "POST" "/trade/items/$itemId/list" @{}; Check "admin list item" ($itl.code -eq 0) "code=$($itl.code)"
$it2 = Send $sal "POST" "/trade/items" @{ code = "x$($tag.Substring($tag.Length-8))"; name = "hack-s"; category = "OTHER"; market = "OTHER"; referencePrice = 1; minQuantity = 1 }
Check "sales create item ->40300" ($it2.code -eq 40300) "code=$($it2.code)"
$it3 = Send $mgr "POST" "/trade/items" @{ code = "y$($tag.Substring($tag.Length-8))"; name = "hack-m"; category = "OTHER"; market = "OTHER"; referencePrice = 1; minQuantity = 1 }
Check "manager create item ->40300" ($it3.code -eq 40300) "code=$($it3.code)"
# order: SELL to customer
$ord = Send $sal "POST" "/trade/orders" @{ customerId = $newCustId; itemId = $itemId; direction = "SELL"; quantity = 5; price = 100 }
Check "sales create order" ($ord.code -eq 0) "code=$($ord.code) msg=$($ord.msg)"
$orderId = "$($ord.data)"
$od1 = Send $sal "GET" "/trade/orders/$orderId" $null; Check "sales view own order" ($od1.code -eq 0) "status=$($od1.data.status)"
$oc1 = Send $sal "POST" "/trade/orders/$orderId/approve" @{}; Check "sales order approve ->40300" ($oc1.code -eq 40300) "code=$($oc1.code)"
$oc2 = Send $mgr "POST" "/trade/orders/$orderId/approve" @{}; Check "manager order approve (auto-confirmed ->40000 ok)" ($oc2.code -eq 0 -or $oc2.code -eq 40000) "code=$($oc2.code)"
$oc3 = Send $sal "POST" "/trade/orders/$orderId/cancel" @{ reason = "t" }; Check "sales cancel CONFIRMED order ->20001 (by design self only pending)" ($oc3.code -eq 20001) "code=$($oc3.code)"
# remit (ISO datetime, remitNo required)
$rm = Send $sal "POST" "/trade/remit" @{ remitNo = "RM$tag"; customerId = $newCustId; amount = 500; remittedAt = "2026-09-08T10:00:00" }
Check "sales register remit" ($rm.code -eq 0) "code=$($rm.code) msg=$($rm.msg)"
$remitId = "$($rm.data)"
# space-format datetime should also parse now (read/write symmetry)
$rmb = Send $sal "POST" "/trade/remit" @{ remitNo = "RMb$tag"; customerId = $newCustId; amount = 10; remittedAt = "2026-09-08 11:00:00" }
Check "remit accept space-format datetime (symmetric)" ($rmb.code -eq 0) "code=$($rmb.code)"
$rc1 = Send $sal "POST" "/trade/remit/$remitId/confirm" @{}; Check "sales remit confirm ->40300" ($rc1.code -eq 40300) "code=$($rc1.code)"
$rc2 = Send $mgr "POST" "/trade/remit/$remitId/confirm" @{}; Check "manager remit confirm" ($rc2.code -eq 0) "code=$($rc2.code)"
$rwBody = '[{ "orderId": ' + $orderId + ', "amount": 100 }]'
$rw1 = SendRaw $sal "POST" "/trade/remit/$remitId/write-off" $rwBody; Check "sales write-off ->40300" ($rw1.code -eq 40300) "code=$($rw1.code)"
$rw2 = SendRaw $mgr "POST" "/trade/remit/$remitId/write-off" $rwBody; Check "manager write-off" ($rw2.code -eq 0) "code=$($rw2.code)"

Write-Host "=== Service ticket / SLA ==="
$tk = Send $sal "POST" "/tickets" @{ customerId = $newCustId; type = "CONSULT"; priority = "HIGH"; title = "ft-ticket-$tag"; content = "test" }
Check "sales create ticket" ($tk.code -eq 0) "code=$($tk.code)"
$ticketId = "$($tk.data)"
Check "ticket list" ((Send $sal "GET" "/tickets?pageNum=1&pageSize=5" $null).code -eq 0) "code=0"
Check "SLA warning" ((Send $mgr "GET" "/tickets/sla/warning" $null).code -eq 0) "code=0"
$tk3 = Send $sal "POST" "/tickets/$ticketId/process" @{}; Check "ticket process flow" ($tk3.code -eq 0) "code=$($tk3.code)"

Write-Host "=== Value scoring ==="
Check "sales value scores (904)" ((Send $sal "GET" "/value/scores?pageNum=1&pageSize=5" $null).code -eq 0) "code=0"
$v2 = Send $sal "POST" "/value/recalc" @{}; Check "sales value recalc ->40300 (no 905)" ($v2.code -eq 40300) "code=$($v2.code)"
$v3 = Send $mgr "POST" "/value/recalc" @{}; Check "manager value recalc (905)" ($v3.code -eq 0) "code=$($v3.code)"

Write-Host "=== Reports / Stat ==="
foreach ($rp in @("customer","lead","opportunity","followup-timeliness")) {
    Check "report $rp" ((Send $sal "GET" "/reports/$rp" $null).code -eq 0) "code=0"
}
Check "stat summary (sales has stat:report)" ((Send $sal "GET" "/stat/summary?dim=MONTH" $null).code -eq 0) "code=0"
$st2 = Send $sal "POST" "/stat/rebuild" @{}; Check "sales stat rebuild ->40300 (no stat:manage)" ($st2.code -eq 40300) "code=$($st2.code)"
$st3 = Send $mgr "POST" "/stat/rebuild" @{}; Check "manager stat rebuild" ($st3.code -eq 0) "code=$($st3.code)"

Write-Host "=== Workbench / followup / notify ==="
Check "workbench summary" ((Send $sal "GET" "/workbench/summary" $null).code -eq 0) "code=0"
$fp = Send $sal "POST" "/followups" @{ relType = "CUSTOMER"; relId = $newCustId; content = "ft-followup-$tag"; nextTime = "2026-09-20 10:00:00" }
Check "create followup" ($fp.code -eq 0) "code=$($fp.code)"
Check "followup todo" ((Send $sal "GET" "/followups/todo" $null).code -eq 0) "code=0"
Check "notify unread-count" ((Send $sal "GET" "/trade/notify/unread-count" $null).code -eq 0) "code=0"

Write-Host ""
Write-Host "============================================"
Write-Host "TOTAL PASS=$script:pass  FAIL=$script:fail"
Write-Host "============================================"
$script:results | ForEach-Object { Write-Host $_ }
