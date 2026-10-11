param(
    [switch]$PromptForPassword,
    [string]$Barcode = '3017620422003'
)

$ErrorActionPreference = 'Stop'

$dbUser = 'root'
$passwordPtr = [IntPtr]::Zero
$dbPassword = $null
$configPath = Join-Path $PSScriptRoot '..\src\main\resources\db.properties'
if (-not $PromptForPassword -and (Test-Path -LiteralPath $configPath)) {
    $line = Get-Content -LiteralPath $configPath -Encoding UTF8 | Where-Object { $_ -match '^\s*db\.password=' } | Select-Object -First 1
    if ($line) { $dbPassword = $line.Substring('db.password='.Length).TrimEnd() }
}
if ([string]::IsNullOrWhiteSpace($dbPassword)) {
    $securePassword = Read-Host '请输入 MySQL root 密码（不会写入文件）' -AsSecureString
    $passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $dbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
}
$dbName = 'flavor_logic'
if ([string]::IsNullOrWhiteSpace($dbPassword)) { throw '没有输入数据库密码' }

$env:MYSQL_PWD = $dbPassword
$sqlPath = $null
try {
    $mysqlArgs = @('--batch', '--skip-column-names', '-h', '127.0.0.1', '-P', '3306', '-u', $dbUser,
        '--default-character-set=utf8mb4', $dbName)
    $siteId = ((& mysql @mysqlArgs -e "SELECT id FROM source_site WHERE site_name='Open Food Facts' AND status=1 AND crawl_allowed=1 LIMIT 1;") | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($siteId)) {
        throw '找不到已启用的 Open Food Facts 来源站点'
    }

    if ($Barcode -notmatch '^\d{8,14}$') { throw '条码必须是 8 到 14 位数字' }
    $url = "https://world.openfoodfacts.org/api/v2/product/$Barcode.json"
    $existing = ((& mysql @mysqlArgs -e ("SELECT id, review_status FROM source_document WHERE url='" + $url + "' LIMIT 1;") ) | Out-String).Trim()
    if (-not [string]::IsNullOrWhiteSpace($existing)) {
        Write-Output "EXISTING_DOCUMENT=$existing"
        exit 0
    }

    $response = Invoke-WebRequest -Uri $url -Headers @{ 'User-Agent' = 'FlavorLogicResearchBot/1.0' } -TimeoutSec 30 -UseBasicParsing
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "公开来源返回 HTTP $($response.StatusCode)"
    }
    $content = $response.Content
    if ([string]::IsNullOrWhiteSpace($content)) {
        throw '来源正文为空'
    }
    $obj = $content | ConvertFrom-Json -AsHashtable
    if ([int]$obj['status'] -ne 1) {
        throw "来源未返回有效产品，status=$($obj['status'])"
    }

    $product = $obj['product']
    $productName = if ([string]::IsNullOrWhiteSpace([string]$product['product_name'])) { '未命名产品' } else { [string]$product['product_name'] }
    $title = "Open Food Facts 产品 $($obj['code'])：$productName"
    $summary = "公开 API 产品记录；条码 $($obj['code'])；品牌 $($product['brands'])；当前仅进入待审核队列。"
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $hashBytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($title + "`n" + $content))
    $hash = -join ($hashBytes | ForEach-Object { $_.ToString('x2') })

    function Hex([string]$value) {
        if ($null -eq $value) { return 'NULL' }
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($value)
        return '0x' + (-join ($bytes | ForEach-Object { $_.ToString('x2') }))
    }

    $sqlPath = Join-Path $env:TEMP ('flavorlogic_off_import_' + [guid]::NewGuid().ToString('N') + '.sql')
    $sql = @"
SET NAMES utf8mb4;
USE ``$dbName``;
START TRANSACTION;
INSERT INTO crawl_job (site_id, seed_url, status, started_at, finished_at, fetched_count, success_count, failed_count, error_message)
VALUES ($siteId, $(Hex $url), 'COMPLETED', NOW(), NOW(), 1, 1, 0, NULL);
SET `@job_id = LAST_INSERT_ID();
INSERT INTO source_document
(site_id, crawl_job_id, url, title, summary, content_text, content_hash, source_type, license_type, evidence_level, fetched_at, review_status)
VALUES
($siteId, `@job_id, $(Hex $url), $(Hex $title), $(Hex $summary), $(Hex $content), $(Hex $hash), 'DATASET', $(Hex 'ODbL；详见 Open Food Facts 使用条款'), 'UNVERIFIED', NOW(), 'REVIEW_PENDING');
COMMIT;
"@
    [System.IO.File]::WriteAllText($sqlPath, $sql, (New-Object System.Text.UTF8Encoding($false)))
    & mysql @mysqlArgs -e ("source " + $sqlPath)
    if ($LASTEXITCODE -ne 0) {
        throw "写入 MySQL 失败，退出码 $LASTEXITCODE"
    }

    $check = ((& mysql @mysqlArgs -e ("SELECT d.id, d.url, d.review_status, d.content_hash, j.status AS crawl_status FROM source_document d LEFT JOIN crawl_job j ON j.id=d.crawl_job_id WHERE d.url='" + $url + "';") ) | Out-String).Trim()
    Write-Output "SITE_ID=$siteId"
    Write-Output "PRODUCT=$productName"
    Write-Output "CONTENT_BYTES=$($response.RawContentLength)"
    Write-Output "CONTENT_HASH=$hash"
    Write-Output $check
}
finally {
    if ($passwordPtr -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
    }
    $dbPassword = $null
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    if ($sqlPath) { Remove-Item -LiteralPath $sqlPath -Force -ErrorAction SilentlyContinue }
}
