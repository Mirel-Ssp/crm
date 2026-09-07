# End-to-end smoke test: login -> customer CRUD -> contacts -> followups -> workbench summary
# Batch 2: dicts -> lead full flow (create/claim/assign/convert/invalidate) -> system read endpoints
# Batch 3: opp lifecycle (stage/win/lose/funnel) -> todo board -> reports + CSV -> merge/trace -> password
# Batch 4: trade item + rules -> order 6-state machine -> remit write-off (anti-over) -> notify feed -> workbench
# Backend returns HTTP 200 with body.code!=0 for business errors.
# Usage: powershell -ExecutionPolicy Bypass -File smoke.ps1  (backend on 8080, RESTART backend first)
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080/api'
$pass = 0; $fail = 0

function Assert($cond, $name) {
    if ($cond) { $script:pass++; Write-Host "PASS  $name" }
    else { $script:fail++; Write-Host "FAIL  $name" }
}
function PostJson($url, $headers, $obj) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json))
    Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes
}

# 1. login
$login = PostJson "$base/auth/login" @{} @{ username = 'admin'; password = 'admin123' }
$token = $login.data.accessToken
Assert (-not [string]::IsNullOrWhiteSpace($token)) 'auth/login returns accessToken'
$h = @{ Authorization = "Bearer $token" }

# 2. anonymous access blocked (HTTP 401 at security layer)
$blocked = $false
try { Invoke-RestMethod -Uri "$base/customers" -TimeoutSec 5 | Out-Null } catch { $blocked = $true }
Assert $blocked 'anonymous /customers rejected'

# 3. create customer (unique name)
$uname = "smoke-cust-" + (Get-Date -Format 'yyyyMMddHHmmssfff')
$created = PostJson "$base/customers" $h @{ name = $uname; level = 'VIP'; industry = 'IT'; remark = 'smoke' }
$cid = [long]$created.data
Assert ($created.code -eq 0 -and $cid -gt 0) "create customer id=$cid"

# 4. duplicate name rejected (code=40000)
$dup = PostJson "$base/customers" $h @{ name = $uname; level = 'A' }
Assert ($dup.code -eq 40000) 'duplicate customer code=40000'

# 5. list visible by keyword
$list = Invoke-RestMethod -Uri "$base/customers?keyword=$uname" -Headers $h
Assert ($list.code -eq 0 -and $list.data.total -ge 1) 'list by keyword visible'

# 6. update customer then verify detail
$ub = @{ name = $uname; level = 'IMPORTANT'; industry = 'IT'; remark = 'smoke-upd' } | ConvertTo-Json
$updBytes = [System.Text.Encoding]::UTF8.GetBytes($ub)
$upd = Invoke-RestMethod -Method Put -Uri "$base/customers/$cid" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $updBytes
$detail = Invoke-RestMethod -Uri "$base/customers/$cid" -Headers $h
Assert ($upd.code -eq 0 -and $detail.data.customer.level -eq 'IMPORTANT') 'update then detail level=IMPORTANT'

# 7. create contact
$contact = PostJson "$base/customers/$cid/contacts" $h @{ name = 'Zhang San'; position = 'PM'; phone = '13800000000'; isPrimary = $true }
Assert ($contact.code -eq 0 -and [long]$contact.data -gt 0) 'create contact'

# 8. contact visible in detail (re-fetch after creation)
$detail = Invoke-RestMethod -Uri "$base/customers/$cid" -Headers $h
Assert ($detail.data.contacts.Count -ge 1) 'contact visible in detail'

# 9. create followup (relType/relId, nextFollowupAt must be ISO format)
$fu = PostJson "$base/followups" $h @{ relType = 'CUSTOMER'; relId = $cid; content = 'first call'; status = 'TODO'; nextFollowupAt = '2026-09-10T10:00:00' }
Assert ($fu.code -eq 0 -and [long]$fu.data -gt 0) 'create followup'

# 10. followup list
$fus = Invoke-RestMethod -Uri "$base/followups?relType=CUSTOMER&relId=$cid" -Headers $h
Assert ($fus.code -eq 0 -and $fus.data.Count -ge 1) 'followup list visible'

# 11. workbench summary KPI
$kpi = Invoke-RestMethod -Uri "$base/workbench/summary" -Headers $h
Assert ($kpi.code -eq 0 -and $null -ne $kpi.data) 'workbench summary accessible'

# 12. delete customer (soft delete) -> detail code=40400
$del = Invoke-RestMethod -Method Delete -Uri "$base/customers/$cid" -Headers $h
$after = Invoke-RestMethod -Uri "$base/customers/$cid" -Headers $h
Assert ($del.code -eq 0 -and $after.code -eq 40400) 'detail 40400 after delete'

# 13. dict list (login required, SYS-DV-05 seed)
$dicts = Invoke-RestMethod -Uri "$base/dicts" -Headers $h
Assert ($dicts.code -eq 0 -and $dicts.data.Count -ge 1) 'dict list seeded'

# 14. lead create into public pool (unique phone: 139 + full timestamp)
$sfx = (Get-Date -Format 'yyyyMMddHHmmssfff')
$lead = PostJson "$base/leads" $h @{ companyName = "smoke-lead-$sfx"; contactName = 'LS'; contactPhone = "139$sfx"; source = 'WEBSITE' }
$lid = [long]$lead.data
Assert ($lead.code -eq 0 -and $lid -gt 0) "create lead id=$lid"

# 15. duplicate phone rejected (code=40000)
$dupPhone = "139$sfx"
$dupLead = PostJson "$base/leads" $h @{ companyName = 'smoke-dup'; contactName = 'LS'; contactPhone = $dupPhone }
Assert ($dupLead.code -eq 40000) 'duplicate lead phone code=40000'

# 16. public pool list contains the lead
$pool = Invoke-RestMethod -Uri "$base/leads?pool=public&keyword=smoke-lead-$sfx" -Headers $h
Assert ($pool.code -eq 0 -and $pool.data.total -ge 1) 'lead visible in public pool'

# 17. claim lead -> status CLAIMED
$claim = Invoke-RestMethod -Method Post -Uri "$base/leads/$lid/claim" -Headers $h
$leadDetail = Invoke-RestMethod -Uri "$base/leads/$lid" -Headers $h
Assert ($claim.code -eq 0 -and $leadDetail.data.lead.status -eq 'CLAIMED') 'claim lead -> CLAIMED'

# 18. assignable users (data-scope aware)
$assignables = Invoke-RestMethod -Uri "$base/leads/assignable" -Headers $h
Assert ($assignables.code -eq 0 -and $assignables.data.Count -ge 1) 'assignable users listed'

# 19. assign a fresh PENDING lead to first assignable user -> ASSIGNED
$lead3 = PostJson "$base/leads" $h @{ companyName = "smoke-lead3-$sfx"; contactName = 'LS'; contactPhone = "137$sfx" }
$lid3 = [long]$lead3.data
$targetUid = [long]$assignables.data[0].id
$assignBody = @{ userId = $targetUid } | ConvertTo-Json
$assignBytes = [System.Text.Encoding]::UTF8.GetBytes($assignBody)
$assign = Invoke-RestMethod -Method Post -Uri "$base/leads/$lid3/assign" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $assignBytes
$lead3Detail = Invoke-RestMethod -Uri "$base/leads/$lid3" -Headers $h
Assert ($assign.code -eq 0 -and $lead3Detail.data.lead.status -eq 'ASSIGNED') 'assign lead -> ASSIGNED'

# 20. convert lead -> customer created and reachable
$convert = Invoke-RestMethod -Method Post -Uri "$base/leads/$lid/convert" -Headers $h
$newCid = [long]$convert.data
$custDetail = Invoke-RestMethod -Uri "$base/customers/$newCid" -Headers $h
Assert ($convert.code -eq 0 -and $newCid -gt 0 -and $custDetail.data.customer.name -eq "smoke-lead-$sfx") 'convert lead -> customer created'

# 21. CONVERTED lead cannot be deleted (code=40000)
$delLead = Invoke-RestMethod -Method Delete -Uri "$base/leads/$lid" -Headers $h
Assert ($delLead.code -eq 40000) 'CONVERTED lead delete rejected'

# 22. invalidate a fresh lead -> INVALID
$lead2 = PostJson "$base/leads" $h @{ companyName = "smoke-lead2-$sfx"; contactName = 'LS'; contactPhone = "138$sfx" }
$lid2 = [long]$lead2.data
$inv = Invoke-RestMethod -Method Post -Uri "$base/leads/$lid2/invalidate" -Headers $h
$lead2Detail = Invoke-RestMethod -Uri "$base/leads/$lid2" -Headers $h
Assert ($inv.code -eq 0 -and $lead2Detail.data.lead.status -eq 'INVALID') 'invalidate lead -> INVALID'

# 23. system read endpoints (SYS-DV-01)
$orgTree = Invoke-RestMethod -Uri "$base/system/orgs/tree" -Headers $h
Assert ($orgTree.code -eq 0) 'org tree accessible'
$users = Invoke-RestMethod -Uri "$base/system/users?pageNum=1&pageSize=5" -Headers $h
Assert ($users.code -eq 0 -and $users.data.total -ge 1) 'user page accessible'
$roles = Invoke-RestMethod -Uri "$base/system/roles" -Headers $h
Assert ($roles.code -eq 0 -and $roles.data.Count -ge 1) 'role list accessible'

# 24. lead detail: CONVERTED lead keeps convertedCustomerId (re-fetch after convert)
$leadDetail = Invoke-RestMethod -Uri "$base/leads/$lid" -Headers $h
Assert ([long]$leadDetail.data.lead.convertedCustomerId -eq $newCid) 'lead convertedCustomerId backfilled'

# ==================== Batch 3: OPP / FUP / RPT / CUS / AUTH ====================

# 25. opportunity create (CRM-O1)
$opp = PostJson "$base/opps" $h @{ customerId = $newCid; name = "smoke-opp-$sfx"; amount = 100000; currency = 'CNY'; expectedDate = '2026-12-31' }
$oppId = [long]$opp.data
Assert ($opp.code -eq 0 -and $oppId -gt 0) "create opportunity id=$oppId"

# 26. stage forward 1->3 then back 3->2 (trace kept, CRM-O2)
$st1 = PostJson "$base/opps/$oppId/stage" $h @{ toStage = 3; reason = 'smoke forward' }
$st2 = PostJson "$base/opps/$oppId/stage" $h @{ toStage = 2 }
Assert ($st1.code -eq 0 -and $st2.code -eq 0) 'stage forward and backward'

# 27. invalid stage rejected (9 out of range, 6 must use win) -> 40000
$bad1 = PostJson "$base/opps/$oppId/stage" $h @{ toStage = 9 }
$bad2 = PostJson "$base/opps/$oppId/stage" $h @{ toStage = 6 }
Assert ($bad1.code -eq 40000 -and $bad2.code -eq 40000) 'invalid stage code=40000'

# 28. win -> opp WON + customer lifecycle WON (CRM-O4 + CRM-C4 link)
$win = PostJson "$base/opps/$oppId/win" $h @{ reason = 'smoke annual contract' }
$custAfter = Invoke-RestMethod -Uri "$base/customers/$newCid" -Headers $h
Assert ($win.code -eq 0 -and $custAfter.data.customer.lifecycleStatus -eq 'WON') 'win links customer lifecycle WON'

# 29. funnel: stage 6 count >= 1 with winRate 100 (CRM-O3/O5)
$funnel = Invoke-RestMethod -Uri "$base/opps/funnel" -Headers $h
$stage6 = $funnel.data | Where-Object { $_.stage -eq 6 }
Assert ($funnel.code -eq 0 -and $null -ne $stage6 -and $stage6.count -ge 1 -and $stage6.winRate -eq 100) 'funnel stage6 count/winRate'

# 30. terminal opp delete rejected (keep statistics) -> 40000
$delOpp = Invoke-RestMethod -Method Delete -Uri "$base/opps/$oppId" -Headers $h
Assert ($delOpp.code -eq 40000) 'terminal opp delete rejected'

# 31. lose without reason -> 40000; with reason PRICE -> LOST (CRM-O4)
$opp2 = PostJson "$base/opps" $h @{ customerId = $newCid; name = "smoke-opp-lose-$sfx"; amount = 5000 }
$opp2Id = [long]$opp2.data
$loseEmpty = PostJson "$base/opps/$opp2Id/lose" $h @{ reason = '' }
$lose = PostJson "$base/opps/$opp2Id/lose" $h @{ reason = 'PRICE' }
Assert ($loseEmpty.code -eq 40000 -and $lose.code -eq 0) 'lose reason required then lost'

# 32. loss stats contains PRICE (source for CSV export)
$loss = Invoke-RestMethod -Uri "$base/opps/loss-stats" -Headers $h
$priceRow = $loss.data | Where-Object { $_.reason -eq 'PRICE' }
Assert ($loss.code -eq 0 -and $null -ne $priceRow -and $priceRow.count -ge 1) 'loss stats PRICE visible'

# 33. todo list contains the TODO followup from step 9, reschedule then done (CRM-F2)
$fuId = [long]$fu.data
$todo = Invoke-RestMethod -Uri "$base/followups/todo" -Headers $h
$todoRow = $todo.data | Where-Object { [long]$_.id -eq $fuId }
Assert ($todo.code -eq 0 -and $null -ne $todoRow) 'todo list contains followup'
$resch = PostJson "$base/followups/$fuId/reschedule" $h @{ nextFollowupAt = '2026-09-20T10:00:00' }
Assert ($resch.code -eq 0) 'todo reschedule'

# 34. done todo -> removed from todo list
$done = Invoke-RestMethod -Method Post -Uri "$base/followups/$fuId/done" -Headers $h
$todo2 = Invoke-RestMethod -Uri "$base/followups/todo" -Headers $h
$stillThere = $todo2.data | Where-Object { [long]$_.id -eq $fuId }
Assert ($done.code -eq 0 -and $null -eq $stillThere) 'todo done removes from list'

# 35. three reports accessible (RPT-DV-01)
$rCust = Invoke-RestMethod -Uri "$base/reports/customer" -Headers $h
$rLead = Invoke-RestMethod -Uri "$base/reports/lead" -Headers $h
$rOpp = Invoke-RestMethod -Uri "$base/reports/opportunity" -Headers $h
Assert ($rCust.code -eq 0 -and $rLead.code -eq 0 -and $rOpp.code -eq 0) 'three reports accessible'

# 36. CSV export: utf-8 BOM + text/csv (RPT-DV-02)
$csv = Invoke-WebRequest -Uri "$base/reports/customer/export" -Headers $h -UseBasicParsing
$bomOk = ($csv.Content.Length -gt 0 -and [int][char]$csv.Content.Substring(0, 1) -eq 65279)
Assert ($csv.StatusCode -eq 200 -and $csv.Headers['Content-Type'] -like 'text/csv*' -and $bomOk) 'csv export with BOM'

# 37. duplicate check by exact name hits the converted customer (CRM-C5)
$dupCheck = Invoke-RestMethod -Uri "$base/customers/check-duplicate?name=smoke-lead-$sfx" -Headers $h
$hitRow = $dupCheck.data | Where-Object { $_.matchType -eq 'NAME' }
Assert ($dupCheck.code -eq 0 -and $null -ne $hitRow) 'check-duplicate hits by name'

# 38. merge: source merged into target, source detail 40400 (CRM-C5)
$mTarget = PostJson "$base/customers" $h @{ name = "smoke-merge-target-$sfx" }
$mSource = PostJson "$base/customers" $h @{ name = "smoke-merge-source-$sfx" }
$mTid = [long]$mTarget.data
$mSid = [long]$mSource.data
$merge = PostJson "$base/customers/$mTid/merge" $h @{ sourceId = $mSid }
$mSrcDetail = Invoke-RestMethod -Uri "$base/customers/$mSid" -Headers $h
Assert ($merge.code -eq 0 -and $mSrcDetail.code -eq 40400) 'merge removes source customer'

# 39. timeline on merge target contains TRACE event (CRM-C6)
$tl = Invoke-RestMethod -Uri "$base/customers/$mTid/timeline" -Headers $h
$traceRow = $tl.data | Where-Object { $_.type -eq 'TRACE' }
Assert ($tl.code -eq 0 -and $tl.data.Count -ge 1 -and $null -ne $traceRow) 'timeline contains trace'

# 40. change password with wrong old password -> 40000 (idempotent, no real change)
$wrongPwd = PostJson "$base/auth/password" $h @{ oldPassword = 'wrong-old-pass'; newPassword = 'new-pass-2026' }
Assert ($wrongPwd.code -eq 40000) 'change password wrong old rejected'

# PostJson variant that keeps array bodies (write-off takes a JSON array)
function PostJsonArray($url, $headers, $arr) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $arr -Depth 5))
    Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes
}

# ==================== Batch 4: TRD / ORD / REM / RTP / WSP ====================

# 41. trade item create -> DRAFT (TB-1)
$itemCode = "smoke-item-$sfx"
$item = PostJson "$base/trade/items" $h @{ code = $itemCode; name = "SmokeItem-$sfx"; category = 'POINT'; market = 'PRIMARY'; referencePrice = 100; riskLevel = 'LOW'; feeRate = 0; minQuantity = 1; remark = 'smoke' }
$itemId = [long]$item.data
Assert ($item.code -eq 0 -and $itemId -gt 0) "trade item created id=$itemId"

# 42. price change keeps trace (TB-2): 100 -> 120
$pc = PostJson "$base/trade/items/$itemId/price" $h @{ newPrice = 120; reason = 'smoke repricing' }
$logs = Invoke-RestMethod -Uri "$base/trade/items/$itemId/price-logs" -Headers $h
Assert ($pc.code -eq 0 -and $logs.data.Count -ge 1 -and [double]$logs.data[0].newPrice -eq 120) 'price change + trace'

# 43. list item (DRAFT -> LISTED) then filter by status
$listUp = Invoke-RestMethod -Method Post -Uri "$base/trade/items/$itemId/list" -Headers $h
$listed = Invoke-RestMethod -Uri "$base/trade/items?status=LISTED&keyword=$itemCode" -Headers $h
Assert ($listUp.code -eq 0 -and $listed.data.Count -eq 1) 'item listed and filterable'

# 44. trade rules seeded (TB-3): APPROVAL_THRESHOLD / MAX_ORDER_AMOUNT
$rules = Invoke-RestMethod -Uri "$base/trade/items/rules" -Headers $h
Assert ($rules.code -eq 0 -and $rules.data.Count -ge 2) 'trade rules seeded'

# 45. order below threshold auto-CONFIRMED, server-side pricing (OD-1)
$o1 = PostJson "$base/trade/orders" $h @{ customerId = $newCid; itemId = $itemId; direction = 'BUY'; quantity = 10; price = 120 }
$o1Id = [long]$o1.data
$o1d = Invoke-RestMethod -Uri "$base/trade/orders/$o1Id" -Headers $h
Assert ($o1.code -eq 0 -and $o1d.data.status -eq 'CONFIRMED' -and [double]$o1d.data.totalAmount -eq 1200) 'order auto-confirmed totalAmount=1200'

# 46. order above threshold goes PENDING_CONFIRM then approve (OD-4)
$o2 = PostJson "$base/trade/orders" $h @{ customerId = $newCid; itemId = $itemId; direction = 'BUY'; quantity = 2000; price = 120 }
$o2Id = [long]$o2.data
$o2d = Invoke-RestMethod -Uri "$base/trade/orders/$o2Id" -Headers $h
Assert ($o2.code -eq 0 -and $o2d.data.status -eq 'PENDING_CONFIRM') 'order above threshold pending'
$ap = PostJson "$base/trade/orders/$o2Id/approve" $h @{}
$o2d = Invoke-RestMethod -Uri "$base/trade/orders/$o2Id" -Headers $h
if ($ap.code -ne 0 -or $o2d.data.status -ne 'CONFIRMED') { Write-Host "DBG approve code=$($ap.code) msg=$($ap.message) status=$($o2d.data.status)" }
Assert ($ap.code -eq 0 -and $o2d.data.status -eq 'CONFIRMED') 'order approved -> CONFIRMED'

# 47. order reject requires reason, -> CANCELLED (OD-4)
$o4 = PostJson "$base/trade/orders" $h @{ customerId = $newCid; itemId = $itemId; direction = 'SELL'; quantity = 5; price = 120 }
$o4Id = [long]$o4.data
$rj = PostJson "$base/trade/orders/$o4Id/reject" $h @{ reason = 'smoke reject' }
$o4d = Invoke-RestMethod -Uri "$base/trade/orders/$o4Id" -Headers $h
Assert ($rj.code -eq 0 -and $o4d.data.status -eq 'CANCELLED') 'order rejected -> CANCELLED'

# 48. remit register -> confirm (RM-1/RM-2)
$remitNo1 = "smoke-r1-$sfx"
$r1 = PostJson "$base/trade/remit" $h @{ remitNo = $remitNo1; customerId = $newCid; amount = 700; currency = 'CNY'; remittedAt = '2026-09-05T10:00:00'; remark = 'smoke' }
$r1Id = [long]$r1.data
$r1d = Invoke-RestMethod -Uri "$base/trade/remit/$r1Id" -Headers $h
Assert ($r1.code -eq 0 -and $r1d.data.status -eq 'PENDING_CONFIRM') 'remit registered pending'
$cf = Invoke-RestMethod -Method Post -Uri "$base/trade/remit/$r1Id/confirm" -Headers $h
Assert ($cf.code -eq 0) 'remit confirmed'

# 49. write-off 700 -> order PARTIAL_DEALT, remit fully WRITTEN_OFF (RM-4)
$wo1 = PostJsonArray "$base/trade/remit/$r1Id/write-off" $h @(@{ orderId = $o1Id; amount = 700 })
$o1d = Invoke-RestMethod -Uri "$base/trade/orders/$o1Id" -Headers $h
$r1d = Invoke-RestMethod -Uri "$base/trade/remit/$r1Id" -Headers $h
Assert ($wo1.code -eq 0 -and $o1d.data.status -eq 'PARTIAL_DEALT' -and [double]$o1d.data.paidAmount -eq 700 -and $r1d.data.status -eq 'WRITTEN_OFF') 'write-off advances order + remit'

# 50. over write-off on order rejected: 700 + 600 > 1200 -> 30001 (RM-4 double guard)
$r2 = PostJson "$base/trade/remit" $h @{ remitNo = "smoke-r2-$sfx"; customerId = $newCid; amount = 600; currency = 'CNY'; remittedAt = '2026-09-05T10:30:00' }
$r2Id = [long]$r2.data
Invoke-RestMethod -Method Post -Uri "$base/trade/remit/$r2Id/confirm" -Headers $h | Out-Null
$woOver = PostJsonArray "$base/trade/remit/$r2Id/write-off" $h @(@{ orderId = $o1Id; amount = 600 })
Assert ($woOver.code -eq 30001) "order over write-off rejected code=$($woOver.code)"

# 51. second write-off 500 -> order FULL_DEALT, remit PARTIALLY_WRITTEN_OFF (state machine push)
$wo2 = PostJsonArray "$base/trade/remit/$r2Id/write-off" $h @(@{ orderId = $o1Id; amount = 500 })
$o1d = Invoke-RestMethod -Uri "$base/trade/orders/$o1Id" -Headers $h
$r2d = Invoke-RestMethod -Uri "$base/trade/remit/$r2Id" -Headers $h
Assert ($wo2.code -eq 0 -and $o1d.data.status -eq 'FULL_DEALT' -and $r2d.data.status -eq 'PARTIALLY_WRITTEN_OFF') 'order FULL_DEALT after full write-off'

# 52. terminal order cannot be written off again -> 20001/40000
$woTerm = PostJsonArray "$base/trade/remit/$r2Id/write-off" $h @(@{ orderId = $o1Id; amount = 50 })
Assert ($woTerm.code -ne 0) "terminal order write-off rejected code=$($woTerm.code)"

# 53. owner cancels own PENDING order (OD-1 above threshold keeps pending; OD-5 self-cancel)
$o3 = PostJson "$base/trade/orders" $h @{ customerId = $newCid; itemId = $itemId; direction = 'BUY'; quantity = 2000; price = 120 }
$o3Id = [long]$o3.data
$cc = PostJson "$base/trade/orders/$o3Id/cancel" $h @{ reason = 'smoke cancel' }
$o3d = Invoke-RestMethod -Uri "$base/trade/orders/$o3Id" -Headers $h
Assert ($cc.code -eq 0 -and $o3d.data.status -eq 'CANCELLED') 'order cancelled'

# 54. notify feed + unread count (RTP-DV-01)
$unread = Invoke-RestMethod -Uri "$base/trade/notify/unread-count" -Headers $h
$feed = Invoke-RestMethod -Uri "$base/trade/notify/feed?pageNum=1&pageSize=10" -Headers $h
Assert ($unread.code -eq 0 -and $feed.code -eq 0 -and $feed.data.total -ge 1) "feed has events (unread=$($unread.data))"

# 55. read-all resets unread to 0
$ra = Invoke-RestMethod -Method Post -Uri "$base/trade/notify/read-all" -Headers $h
$unread2 = Invoke-RestMethod -Uri "$base/trade/notify/unread-count" -Headers $h
Assert ($ra.code -eq 0 -and [long]$unread2.data -eq 0) 'read-all resets unread'

# 56. trade workbench aggregate (WSP-DV-01)
$wb = Invoke-RestMethod -Uri "$base/trade/workbench" -Headers $h
Assert ($wb.code -eq 0 -and $null -ne $wb.data.scope -and $null -ne $wb.data.cards) 'workbench aggregate'

# 57. customer trade panel (WSP-DV-02)
$cp = Invoke-RestMethod -Uri "$base/trade/workbench/customer/$newCid" -Headers $h
Assert ($cp.code -eq 0 -and $null -ne $cp.data) 'customer trade panel'

# ==================== Batch 5: SVC / VA / STAT / WS ====================

# 58. ticket create -> OPEN (CRM-S1)
$tk = PostJson "$base/tickets" $h @{ customerId = $newCid; type = 'CONSULT'; priority = 'HIGH'; title = "smoke-ticket-$sfx"; content = 'smoke ticket body' }
$tkId = [long]$tk.data
$tkd = Invoke-RestMethod -Uri "$base/tickets/$tkId" -Headers $h
Assert ($tk.code -eq 0 -and $tkd.data.status -eq 'OPEN') 'ticket created OPEN'

# 59. ticket assign -> assigneeName backfilled (CRM-S1)
$ta = PostJson "$base/tickets/$tkId/assign" $h @{ assigneeId = 1 }
$tkd = Invoke-RestMethod -Uri "$base/tickets/$tkId" -Headers $h
Assert ($ta.code -eq 0 -and $tkd.data.assigneeId -eq 1) 'ticket assigned'

# 60. OPEN -> PROCESSING (CRM-S1)
$tp = Invoke-RestMethod -Method Post -Uri "$base/tickets/$tkId/process" -Headers $h
$tkd = Invoke-RestMethod -Uri "$base/tickets/$tkId" -Headers $h
Assert ($tp.code -eq 0 -and $tkd.data.status -eq 'PROCESSING') 'ticket processing'

# 61. PROCESSING -> RESOLVED with remark (CRM-S1)
$tr = PostJson "$base/tickets/$tkId/resolve" $h @{ remark = 'smoke resolved' }
$tkd = Invoke-RestMethod -Uri "$base/tickets/$tkId" -Headers $h
Assert ($tr.code -eq 0 -and $tkd.data.status -eq 'RESOLVED') 'ticket resolved'

# 62. illegal transit: RESOLVED cannot process -> rejected (state machine)
$tpBad = Invoke-RestMethod -Method Post -Uri "$base/tickets/$tkId/process" -Headers $h
Assert ($tpBad.code -ne 0) "illegal transit rejected code=$($tpBad.code)"

# 63. visit: content + satisfaction=5 -> score kept (CRM-S2)
$tv = PostJson "$base/tickets/$tkId/visit" $h @{ content = 'smoke visit'; satisfaction = 5 }
$tkd = Invoke-RestMethod -Uri "$base/tickets/$tkId" -Headers $h
Assert ($tv.code -eq 0 -and [int]$tkd.data.satisfaction -eq 5) 'visit satisfaction=5'

# 64. satisfaction summary accessible (CRM-S2)
$ss = Invoke-RestMethod -Uri "$base/tickets/satisfaction/summary" -Headers $h
Assert ($ss.code -eq 0 -and [long]$ss.data.ratedCount -ge 1) "satisfaction summary rated=$($ss.data.ratedCount)"

# 65. RESOLVED -> CLOSED terminal (CRM-S1)
$tcl = Invoke-RestMethod -Method Post -Uri "$base/tickets/$tkId/close" -Headers $h
$tkd = Invoke-RestMethod -Uri "$base/tickets/$tkId" -Headers $h
Assert ($tcl.code -eq 0 -and $tkd.data.status -eq 'CLOSED') 'ticket closed terminal'

# 66. CLOSED terminal cannot process again (CRM-S1)
$tpTerm = Invoke-RestMethod -Method Post -Uri "$base/tickets/$tkId/process" -Headers $h
Assert ($tpTerm.code -ne 0) "terminal ticket rejected code=$($tpTerm.code)"

# 67. VA weights readable, five dims sum to 1 (VA-1)
$w = Invoke-RestMethod -Uri "$base/value/weights" -Headers $h
$wsum = [double]$w.data.WEIGHT_FREQ + [double]$w.data.WEIGHT_AMOUNT + [double]$w.data.WEIGHT_ACTIVE + [double]$w.data.WEIGHT_REMIT + [double]$w.data.WEIGHT_FOLLOWUP
Assert ($w.code -eq 0 -and [math]::Abs($wsum - 1) -lt 0.0001) 'VA weights sum=1'

# 68. weight update with sum!=1 rejected (VA-1 configurable guard)
$wBadBytes = [System.Text.Encoding]::UTF8.GetBytes((@{ WEIGHT_FREQ = 0.9 } | ConvertTo-Json))
$wBad = Invoke-RestMethod -Method Put -Uri "$base/value/weights" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $wBadBytes
Assert ($wBad.code -ne 0) "invalid weights rejected code=$($wBad.code)"

# 69. VA recalc all -> count >= 1 (VA-1 daily 02:00 + manual)
$vrecalc = Invoke-RestMethod -Method Post -Uri "$base/value/recalc" -Headers $h
Assert ($vrecalc.code -eq 0 -and [long]$vrecalc.data -ge 1) "VA recalc count=$($vrecalc.data)"

# 70. VA scores page + distribution four tiers (VA-2)
$vs = Invoke-RestMethod -Uri "$base/value/scores?pageNum=1&pageSize=5" -Headers $h
$vd = Invoke-RestMethod -Uri "$base/value/distribution" -Headers $h
Assert ($vs.code -eq 0 -and $vs.data.total -ge 1) "VA scores total=$($vs.data.total)"
Assert ($vd.code -eq 0 -and $vd.data.Count -ge 1) 'VA distribution tiers'

# 71. VA trend 30 days for recalculated customer (VA-4)
$vt = Invoke-RestMethod -Uri "$base/value/scores/$newCid/trend?days=30" -Headers $h
Assert ($vt.code -eq 0) 'VA trend accessible'

# 72. VA silent list accessible (VA-5)
$vsl = Invoke-RestMethod -Uri "$base/value/silent" -Headers $h
Assert ($vsl.code -eq 0) 'VA silent list accessible'

# 73. STAT rebuild -> rows >= 1 (ST-1 daily 02:30 + manual)
$srb = Invoke-RestMethod -Method Post -Uri "$base/stat/rebuild" -Headers $h
Assert ($srb.code -eq 0 -and [long]$srb.data -ge 1) "STAT rebuild rows=$($srb.data)"

# 74. STAT summary dim=MONTH with rows + chain ratio fields (ST-1)
$ssum = Invoke-RestMethod -Uri "$base/stat/summary?dim=MONTH" -Headers $h
Assert ($ssum.code -eq 0 -and $ssum.data.rows.Count -ge 1 -and $null -ne $ssum.data.totalAmount) 'STAT summary MONTH rows'

# 75. STAT invalid dim rejected (ST-1 guard)
$sbad = Invoke-RestMethod -Uri "$base/stat/summary?dim=WEEK" -Headers $h
Assert ($sbad.code -ne 0) "invalid dim rejected code=$($sbad.code)"

# 76. STAT owner rank + item rank arrays (ST-4/ST-5)
$srank = Invoke-RestMethod -Uri "$base/stat/rank?dim=MONTH" -Headers $h
$sitems = Invoke-RestMethod -Uri "$base/stat/items?dim=MONTH" -Headers $h
Assert ($srank.code -eq 0 -and $sitems.code -eq 0) 'STAT rank/items accessible'

# 77. STAT detail page + CSV export with BOM (ST-3)
$sdet = Invoke-RestMethod -Uri "$base/stat/detail?pageNum=1&pageSize=5" -Headers $h
$scsv = Invoke-WebRequest -Uri "$base/stat/detail/export" -Headers $h -UseBasicParsing
$scsvBom = ($scsv.Content.Length -gt 0 -and [int][char]$scsv.Content.Substring(0, 1) -eq 65279)
Assert ($sdet.code -eq 0 -and $scsv.StatusCode -eq 200 -and $scsv.Headers['Content-Type'] -like 'text/csv*' -and $scsvBom) 'STAT detail + CSV BOM'

Write-Host ''
Write-Host "SMOKE RESULT: $pass passed / $fail failed"
if ($fail -gt 0) { exit 1 }
