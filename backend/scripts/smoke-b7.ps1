# =====================================================================
# Batch 7 smoke: R1 quote / R3 contract / F4 workflow / F3 timeliness
# PS 5.1 compatible: ASCII only, PostJson for bodies
# =====================================================================
$ErrorActionPreference = 'Stop'
$BASE = 'http://127.0.0.1:8080/api'
$pass = 0; $fail = 0

function Check($name, $cond, $extra = '') {
    if ($cond) { $script:pass++; Write-Output "PASS  $name" }
    else { $script:fail++; Write-Output "FAIL  $name $extra" }
}

function Login($u, $p) {
    $r = Invoke-RestMethod -Uri "$BASE/auth/login" -Method Post -ContentType 'application/json' -Body (@{ username = $u; password = $p } | ConvertTo-Json)
    return $r.data.accessToken
}

# ---------------- 1. login ----------------
$adminTok = Login 'admin' 'admin123'
Check 'admin login' ($adminTok -ne $null)
$H = @{ Authorization = "Bearer $adminTok" }

# ---------------- 2. quote: create ----------------
$custs = Invoke-RestMethod -Uri "$BASE/customers?pageNum=1&pageSize=5" -Headers $H
$cid = $custs.data.list[0].id
Check 'customer list for quote' ($cid -gt 0)

$items = @(
    @{ name = 'srv-consult'; spec = 'v1'; quantity = 2; price = 500 },
    @{ name = 'free-line'; quantity = 1; price = 100 }
)
$body = @{
    title      = 'smoke-quote-b7'
    customerId = $cid
    discountRate = 0.9
    taxRate    = 0.06
    items      = $items
} | ConvertTo-Json -Depth 5
$create = Invoke-RestMethod -Uri "$BASE/quotes" -Method Post -Headers $H -ContentType 'application/json' -Body $body
$qid = $create.data
Check 'quote create DRAFT' ($qid -gt 0)

$det = Invoke-RestMethod -Uri "$BASE/quotes/$qid" -Headers $H
Check 'quote amounts server-side' ($det.data.totalAmount -eq 1100 -and $det.data.finalAmount -eq [double](1100 * 0.9 * 1.06))
Check 'quote item rows' ($det.data.items.Count -eq 2)

# ---------------- 3. quote: submit -> BPMN task ----------------
Invoke-RestMethod -Uri "$BASE/quotes/$qid/submit" -Method Post -Headers $H | Out-Null
$det2 = Invoke-RestMethod -Uri "$BASE/quotes/$qid" -Headers $H
Check 'quote SUBMITTED' ($det2.data.status -eq 'SUBMITTED')

$tasks = Invoke-RestMethod -Uri "$BASE/workflow/tasks" -Headers $H
$hit = $tasks.data | Where-Object { $_.businessKey -eq "QUOTE:$qid" }
Check 'BPMN task visible to admin' ($hit -ne $null)

$defs = Invoke-RestMethod -Uri "$BASE/workflow/definitions" -Headers $H
$defHit = $defs.data | Where-Object { $_.key -eq 'quoteApproval' }
Check 'process definition deployed' ($defHit -ne $null -and $defHit.version -ge 1)

# XML viewer
$xml = Invoke-RestMethod -Uri "$BASE/workflow/definitions/$($defHit.id)/xml" -Headers $H
$xmlText = if ($xml -is [System.Xml.XmlDocument]) { $xml.OuterXml } else { "$xml" }
Check 'BPMN xml endpoint' ($xmlText -match 'quoteApproval')

# reject -> edit -> resubmit -> approve
Invoke-RestMethod -Uri "$BASE/quotes/$qid/reject" -Method Post -Headers $H -ContentType 'application/json' -Body (@{ reason = 'smoke reject' } | ConvertTo-Json) | Out-Null
$det3 = Invoke-RestMethod -Uri "$BASE/quotes/$qid" -Headers $H
Check 'quote REJECTED w/ reason' ($det3.data.status -eq 'REJECTED' -and $det3.data.rejectedReason -eq 'smoke reject')

$tasks2 = Invoke-RestMethod -Uri "$BASE/workflow/tasks" -Headers $H
$hit2 = $tasks2.data | Where-Object { $_.businessKey -eq "QUOTE:$qid" }
Check 'BPMN instance ended after reject' ($hit2 -eq $null)

Invoke-RestMethod -Uri "$BASE/quotes/$qid/submit" -Method Post -Headers $H | Out-Null
Invoke-RestMethod -Uri "$BASE/quotes/$qid/approve" -Method Post -Headers $H -ContentType 'application/json' -Body (@{ reason = 'ok' } | ConvertTo-Json) | Out-Null
$det4 = Invoke-RestMethod -Uri "$BASE/quotes/$qid" -Headers $H
Check 'quote APPROVED' ($det4.data.status -eq 'APPROVED' -and $det4.data.approvedAt -ne '')

# ---------------- 4. quote -> convert (with trade item line) ----------------
$its = Invoke-RestMethod -Uri "$BASE/trade/items?status=LISTED" -Headers $H
$item = $its.data | Select-Object -First 1
$body2 = @{
    title      = 'smoke-quote-b7'
    customerId = $cid
    discountRate = 1
    taxRate    = 0
    items      = @(
        @{ itemId = $item.id; name = $item.name; quantity = [double]($item.minQuantity); price = [double]$item.referencePrice }
    )
} | ConvertTo-Json -Depth 5
$qid2 = (Invoke-RestMethod -Uri "$BASE/quotes" -Method Post -Headers $H -ContentType 'application/json' -Body $body2).data
Invoke-RestMethod -Uri "$BASE/quotes/$qid2/submit" -Method Post -Headers $H | Out-Null
Invoke-RestMethod -Uri "$BASE/quotes/$qid2/approve" -Method Post -Headers $H -ContentType 'application/json' -Body (@{ reason = 'ok' } | ConvertTo-Json) | Out-Null
$conv = Invoke-RestMethod -Uri "$BASE/quotes/$qid2/convert?direction=BUY" -Method Post -Headers $H
Check 'quote convert creates order' ($conv.data.Count -ge 1)
$orderId = $conv.data[0]
$det5 = Invoke-RestMethod -Uri "$BASE/quotes/$qid2" -Headers $H
Check 'quote CONVERTED' ($det5.data.status -eq 'CONVERTED')

# cleanup converted order
Invoke-RestMethod -Uri "$BASE/orders/$orderId/cancel?reason=smoke-cleanup" -Method Post -Headers $H | Out-Null

# ---------------- 5. void ----------------
$qid3 = (Invoke-RestMethod -Uri "$BASE/quotes" -Method Post -Headers $H -ContentType 'application/json' -Body $body2).data
Invoke-RestMethod -Uri "$BASE/quotes/$qid3/submit" -Method Post -Headers $H | Out-Null
# PS 5.1: '?' right after $var is parsed into the variable name -> use concatenation
Invoke-RestMethod -Uri ("$BASE/quotes/$qid3" + "?reason=smoke-void") -Method Delete -Headers $H | Out-Null
$det6 = Invoke-RestMethod -Uri "$BASE/quotes/$qid3" -Headers $H
Check 'quote VOID + instance cancelled' ($det6.data.status -eq 'VOID')
$tasks3 = Invoke-RestMethod -Uri "$BASE/workflow/tasks" -Headers $H
$hit3 = $tasks3.data | Where-Object { $_.businessKey -eq "QUOTE:$qid3" }
Check 'void cancels BPMN task' ($hit3 -eq $null)

# ---------------- 6. contract lifecycle ----------------
$cbody = @{
    title     = 'smoke-contract-b7'
    customerId = $cid
    amount    = 8888
    startDate = '2026-09-01'
    endDate   = '2027-09-01'
    reminderDays = 30
} | ConvertTo-Json
$ctid = (Invoke-RestMethod -Uri "$BASE/contracts" -Method Post -Headers $H -ContentType 'application/json' -Body $cbody).data
Check 'contract create UNSIGNED' ($ctid -gt 0)

Invoke-RestMethod -Uri "$BASE/contracts/$ctid/sign" -Method Post -Headers $H | Out-Null
Invoke-RestMethod -Uri "$BASE/contracts/$ctid/execute" -Method Post -Headers $H | Out-Null
$cd = Invoke-RestMethod -Uri "$BASE/contracts/$ctid" -Headers $H
Check 'contract UNSIGNED-SIGNED-EXECUTING' ($cd.data.signStatus -eq 'EXECUTING' -and $cd.data.signedAt -ne '')
Check 'contract daysLeft present' ($cd.data.daysLeft -ne $null)

# expiring list (end date within 30d)
$cbody2 = @{
    title     = 'smoke-contract-expiring'
    customerId = $cid
    amount    = 100
    startDate = '2026-08-01'
    endDate   = (Get-Date).AddDays(10).ToString('yyyy-MM-dd')
} | ConvertTo-Json
$ctid2 = (Invoke-RestMethod -Uri "$BASE/contracts" -Method Post -Headers $H -ContentType 'application/json' -Body $cbody2).data
Invoke-RestMethod -Uri "$BASE/contracts/$ctid2/sign" -Method Post -Headers $H | Out-Null
$exp = Invoke-RestMethod -Uri "$BASE/contracts/expiring" -Headers $H
$expHit = $exp.data | Where-Object { $_.id -eq $ctid2 }
Check 'expiring warning list' ($expHit -ne $null -and $expHit.daysLeft -le 10)

Invoke-RestMethod -Uri "$BASE/contracts/$ctid/terminate" -Method Post -Headers $H -ContentType 'application/json' -Body (@{ reason = 'smoke end' } | ConvertTo-Json) | Out-Null
$cd2 = Invoke-RestMethod -Uri "$BASE/contracts/$ctid" -Headers $H
Check 'contract TERMINATED' ($cd2.data.signStatus -eq 'TERMINATED' -and $cd2.data.terminateReason -eq 'smoke end')

# page + filter
$pg = Invoke-RestMethod -Uri "$BASE/contracts?keyword=smoke-contract" -Headers $H
Check 'contract page filter' ($pg.data.total -ge 2)

# ---------------- 7. F3 timeliness ----------------
$ft = Invoke-RestMethod -Uri "$BASE/reports/followup-timeliness?days=7" -Headers $H
Check 'F3 timeliness kpi' ($ft.data.totalActive -ge 1 -and $ft.data.timelyRate -ge 0)
# PS: empty JSON array is falsy with -ne $null; check property presence instead
Check 'F3 overdue lists present' ($null -ne $ft.data.PSObject.Properties['overdueList'] -and $null -ne $ft.data.PSObject.Properties['overdueTodos'])

# ---------------- 8. permission check: sales cannot approve ----------------
# Provision test users if absent (org 1 / SALES=3 / MANAGER=2)
function EnsureUser($username, $roleId) {
    $ex = Invoke-RestMethod -Uri ($BASE + '/system/users?keyword=' + $username + '&pageNum=1&pageSize=10') -Headers $H
    if ($ex.data.list.Count -gt 0) { return }
    $ub = @{ orgId = 1; username = $username; password = 'Smoke123456'; realName = $username; roleIds = @($roleId) } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BASE/system/users" -Method Post -Headers $H -ContentType 'application/json' -Body $ub | Out-Null
}
EnsureUser 'b7sales' 3
EnsureUser 'b7manager' 2

$salesTok = Login 'b7sales' 'Smoke123456'
Check 'b7sales login' ($salesTok -ne $null)
if ($salesTok) {
    $SH = @{ Authorization = "Bearer $salesTok" }
    # sales creates own customer, then quote (SELF scope can only see own data); unique name per run
    $custName = 'b7-smoke-cust-' + (Get-Date -Format 'yyyyMMddHHmmss')
    $nc = Invoke-RestMethod -Uri "$BASE/customers" -Method Post -Headers $SH -ContentType 'application/json' -Body (@{ name = $custName } | ConvertTo-Json)
    $bodyS = @{ title = 'smoke-quote-sales'; customerId = $nc.data; items = @(@{ name = 'x'; quantity = 1; price = 1 }) } | ConvertTo-Json
    $q4 = (Invoke-RestMethod -Uri "$BASE/quotes" -Method Post -Headers $SH -ContentType 'application/json' -Body $bodyS).data
    Invoke-RestMethod -Uri "$BASE/quotes/$q4/submit" -Method Post -Headers $SH | Out-Null
    # 40300 returns HTTP 200 with business code in body; PS does not throw
    $denied = $false
    try {
        $r4 = Invoke-RestMethod -Uri "$BASE/quotes/$q4/approve" -Method Post -Headers $SH -ContentType 'application/json' -Body (@{ reason = 'x' } | ConvertTo-Json)
        if ($r4.code -eq 40300) { $denied = $true }
    } catch { $denied = $true }
    Check 'sales approve denied 40300' ($denied)
    $q4mid = Invoke-RestMethod -Uri "$BASE/quotes/$q4" -Headers $SH
    Check 'quote still SUBMITTED after denied' ($q4mid.data.status -eq 'SUBMITTED')
    # sales sees task center but no candidate task (SALES not in candidate groups)
    $st = Invoke-RestMethod -Uri "$BASE/workflow/tasks" -Headers $SH
    $sHit = $st.data | Where-Object { $_.businessKey -eq "QUOTE:$q4" }
    Check 'sales no candidate-group task' ($sHit -eq $null)
    # manager approves instead
    $mgrTok = Login 'b7manager' 'Smoke123456'
    Check 'b7manager login' ($mgrTok -ne $null)
    if ($mgrTok) {
        $MH = @{ Authorization = "Bearer $mgrTok" }
        $mt = Invoke-RestMethod -Uri "$BASE/workflow/tasks" -Headers $MH
        $mHit = $mt.data | Where-Object { $_.businessKey -eq "QUOTE:$q4" }
        Check 'manager sees candidate task' ($mHit -ne $null)
        Invoke-RestMethod -Uri "$BASE/quotes/$q4/approve" -Method Post -Headers $MH -ContentType 'application/json' -Body (@{ reason = 'mgr ok' } | ConvertTo-Json) | Out-Null
        $q4d = Invoke-RestMethod -Uri "$BASE/quotes/$q4" -Headers $MH
        Check 'manager approve via business api' ($q4d.data.status -eq 'APPROVED')
    }
}

Write-Output ''
Write-Output "SMOKE RESULT: $pass passed / $fail failed"
if ($fail -gt 0) { exit 1 }
