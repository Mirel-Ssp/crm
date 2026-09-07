# boundary-test.ps1 - boundary / exception / security tests (correct plural paths)
$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8080"
$results = @()

function Add-Result($id, $cat, $name, $expect, $actual, $pass) {
    $script:results += [PSCustomObject]@{ID=$id;Category=$cat;Name=$name;Expect=$expect;Actual=$actual;Pass=$pass}
}
function PostJson($url, $body, $token) {
    $h = @{ "Content-Type"="application/json" }
    if ($token) { $h.Authorization = "Bearer $token" }
    try { return Invoke-RestMethod -Uri $url -Method Post -Headers $h -Body ($body | ConvertTo-Json -Depth 8) -TimeoutSec 15 }
    catch { return $_.Exception.Response }
}
function PutJson($url, $body, $token) {
    $h = @{ "Content-Type"="application/json" }
    if ($token) { $h.Authorization = "Bearer $token" }
    try { return Invoke-RestMethod -Uri $url -Method Put -Headers $h -Body ($body | ConvertTo-Json -Depth 8) -TimeoutSec 15 }
    catch { return $_.Exception.Response }
}
function GetJson($url, $token) {
    $h = @{}
    if ($token) { $h.Authorization = "Bearer $token" }
    try { return Invoke-RestMethod -Uri $url -Method Get -Headers $h -TimeoutSec 15 }
    catch { return $_.Exception.Response }
}

$ts = Get-Date -Format HHmmssfff
$loginBody = @{ username="admin"; password="admin123" }
$r = PostJson "$base/api/auth/login" $loginBody $null
$adminToken = $r.data.accessToken
Add-Result "AUTH-1" "auth" "admin login returns token" "code=0" "code=$($r.code)" ($r.code -eq 0)

$r2 = PostJson "$base/api/auth/login" @{ username="admin"; password="wrongpass" } $null
Add-Result "AUTH-2" "auth" "wrong password rejected" "code!=0" "code=$($r2.code)" ($r2.code -ne 0)

$r3 = PostJson "$base/api/auth/login" @{ username="admin"; password="" } $null
Add-Result "AUTH-3" "auth" "empty password rejected" "code!=0" "code=$($r3.code)" ($r3.code -ne 0)

try { Invoke-RestMethod -Uri "$base/api/customers" -Method Get -TimeoutSec 10 | Out-Null; $anonCode=200 }
catch { $anonCode = [int]$_.Exception.Response.StatusCode }
Add-Result "AUTH-4" "auth" "anonymous protected returns 401" "401" "$anonCode" ($anonCode -eq 401)

$inj = "' OR '1'='1"
$enc = [System.Uri]::EscapeDataString($inj)
$r5 = GetJson "$base/api/customers?keyword=$enc&page=1&size=5" $adminToken
Add-Result "SEC-1" "security" "SQL injection keyword safe" "code=0" "code=$($r5.code)" ($r5.code -eq 0)

$xssName = "<script>alert(1)</script>test_$ts"
$r6 = PostJson "$base/api/customers" @{ name=$xssName; source="NETWORK" } $adminToken
Add-Result "SEC-2" "security" "XSS payload stored safely" "code=0 or 40000" "code=$($r6.code)" ($r6.code -eq 0 -or $r6.code -eq 40000)

$r7 = PostJson "$base/api/customers" @{ name="lvl_test_$ts"; level="INVALID_LEVEL"; source="NETWORK" } $adminToken
Add-Result "BIZ-1" "boundary" "invalid level rejected" "code!=0" "code=$($r7.code)" ($r7.code -ne 0)

$r8 = PostJson "$base/api/customers" @{ name=""; source="NETWORK" } $adminToken
Add-Result "BIZ-2" "boundary" "empty name rejected" "code!=0" "code=$($r8.code)" ($r8.code -ne 0)

$r9 = GetJson "$base/api/customers?page=1&size=-1" $adminToken
Add-Result "BIZ-3" "boundary" "negative size handled" "code=0 or 40000" "code=$($r9.code)" ($r9.code -eq 0 -or $r9.code -eq 40000)

$cust = PostJson "$base/api/customers" @{ name="sm_order_$ts"; source="NETWORK" } $adminToken
$custId = $cust.data
$item = PostJson "$base/api/trade/items" @{ code="IT$ts"; name="item_$ts"; category="POINT"; market="PRIMARY"; referencePrice=100; riskLevel="LOW"; feeRate=0; minQuantity=1 } $adminToken
$itemId = $item.data
PostJson "$base/api/trade/items/$itemId/list" @{} $adminToken | Out-Null
$order = PostJson "$base/api/trade/orders" @{ customerId=$custId; itemId=$itemId; direction="BUY"; quantity=5000; price=100 } $adminToken
$orderId = $order.data
$cancel = PostJson "$base/api/trade/orders/$orderId/cancel" @{} $adminToken
Add-Result "SM-1" "statemachine" "owner cancels pending-confirm order" "code=0" "code=$($cancel.code)" ($cancel.code -eq 0)

$dupName = "dup_$ts"
PostJson "$base/api/customers" @{ name=$dupName; source="NETWORK" } $adminToken | Out-Null
$r11 = PostJson "$base/api/customers" @{ name=$dupName; source="NETWORK" } $adminToken
Add-Result "BIZ-4" "boundary" "duplicate name rejected" "code!=0" "code=$($r11.code)" ($r11.code -ne 0)

$me = GetJson "$base/api/auth/me" $adminToken
Add-Result "AUTH-5" "auth" "admin /me works" "code=0" "code=$($me.code)" ($me.code -eq 0)

$refreshToken = $r.data.refreshToken
$r13 = PostJson "$base/api/auth/refresh" @{ refreshToken=$refreshToken } $null
Add-Result "AUTH-6" "auth" "refresh token works" "code=0" "code=$($r13.code)" ($r13.code -eq 0)

$r14 = PostJson "$base/api/auth/refresh" @{ refreshToken="invalid.token.here" } $null
Add-Result "AUTH-7" "auth" "invalid refresh rejected" "code!=0" "code=$($r14.code)" ($r14.code -ne 0)

$r15 = GetJson "$base/api/customers?page=1&size=10" $adminToken
Add-Result "FUNC-1" "functional" "customer page list+total" "code=0" "code=$($r15.code) total=$($r15.data.total)" ($r15.code -eq 0)

$r16 = GetJson "$base/api/stat/summary?dim=INVALID" $adminToken
Add-Result "BIZ-5" "boundary" "invalid stat dim rejected" "code!=0" "code=$($r16.code)" ($r16.code -ne 0)

$r17 = PutJson "$base/api/value/weights" @{ freqWeight=0.5; amountWeight=0.5; activeWeight=0.5; remitWeight=0.5; followupWeight=0.5 } $adminToken
Add-Result "BIZ-6" "boundary" "VA weights sum!=1 rejected" "code!=0" "code=$($r17.code)" ($r17.code -ne 0)

$tk = PostJson "$base/api/tickets" @{ title="tk_$ts"; customerId=$custId; priority="MEDIUM" } $adminToken
$tkId = $tk.data
$r18 = PostJson "$base/api/tickets/$tkId/close" @{} $adminToken
Add-Result "SM-2" "statemachine" "close ticket without resolve rejected" "code!=0" "code=$($r18.code)" ($r18.code -ne 0)

$r19 = GetJson "$base/api/customers/99999999" $adminToken
Add-Result "BIZ-7" "boundary" "non-existent customer 40400" "code=40400" "code=$($r19.code)" ($r19.code -eq 40400)

$r20 = PostJson "$base/api/opps" @{ customerId=$custId; title="opp_$ts"; amount=1000; stage=99 } $adminToken
Add-Result "BIZ-8" "boundary" "opp stage out of range rejected" "code!=0" "code=$($r20.code)" ($r20.code -ne 0)

# B6-P2: 非数字路径变量 + 未知 API 路径异常码收口（检测报告 P1 #1 #2）
try { $r21 = GetJson "$base/api/customers/abc" $adminToken } catch { $r21 = @{ code = ($_.ErrorDetails.Message | ConvertFrom-Json).code } }
Add-Result "BIZ-9" "boundary" "non-numeric path var returns 40000" "code=40000" "code=$($r21.code)" ($r21.code -eq 40000)

try { $r22 = GetJson "$base/api/nonexistent" $adminToken } catch { $r22 = @{ code = ($_.ErrorDetails.Message | ConvertFrom-Json).code } }
Add-Result "BIZ-10" "boundary" "unknown api path returns 40400" "code=40400" "code=$($r22.code)" ($r22.code -eq 40400)

$pass = ($results | Where-Object { $_.Pass }).Count
$fail = $results.Count - $pass
Write-Output "========================================"
Write-Output "BOUNDARY TEST RESULT: $pass passed / $fail failed / $($results.Count) total"
Write-Output "========================================"
$results | Format-Table ID, Category, Name, Pass -AutoSize
if ($fail -gt 0) {
    Write-Output "--- FAILED CASES ---"
    $results | Where-Object { -not $_.Pass } | Format-List ID, Name, Expect, Actual
}
