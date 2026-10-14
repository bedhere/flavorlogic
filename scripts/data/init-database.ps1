[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$DatabaseName = 'flavor_logic',
    [string]$MySqlCommand = 'mysql',
    [switch]$PromptForPassword
)

# 新环境初始化：先建表，再写基础数据；已有数据库升级请按 database/README.md 执行。
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$configPath = Join-Path $projectRoot 'backend\src\main\resources\db.properties'
$settings = @{}
if (Test-Path -LiteralPath $configPath) {
    foreach ($line in Get-Content -LiteralPath $configPath -Encoding UTF8) {
        if ($line -match '^\s*(db\.[\w]+)=(.*)$') { $settings[$Matches[1]] = $Matches[2] }
    }
}
$databaseUser = if ($env:FLAVORLOGIC_DB_USER) { $env:FLAVORLOGIC_DB_USER } elseif ($settings['db.user']) { $settings['db.user'] } else { 'root' }
$databaseUrl = if ($env:FLAVORLOGIC_DB_URL) { $env:FLAVORLOGIC_DB_URL } else { $settings['db.url'] }
$databaseHost = '127.0.0.1'
$databasePort = '3306'
if ($databaseUrl -match '^jdbc:mysql://([^:/]+)(?::(\d+))?/') {
    $databaseHost = $Matches[1]
    if ($Matches[2]) { $databasePort = $Matches[2] }
}
$databasePassword = if ($env:FLAVORLOGIC_DB_PASSWORD) { $env:FLAVORLOGIC_DB_PASSWORD } else { $settings['db.password'] }
$passwordPtr = [IntPtr]::Zero
$previousPassword = $env:MYSQL_PWD
$previousEncoding = $OutputEncoding
try {
    if ($PromptForPassword -or [string]::IsNullOrEmpty($databasePassword)) {
        $securePassword = Read-Host '请输入 MySQL 密码' -AsSecureString
        $passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        $databasePassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
    }
    $env:MYSQL_PWD = $databasePassword
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $mysqlArgs = @('-h', $databaseHost, '-P', $databasePort, '-u', $databaseUser, '--default-character-set=utf8mb4')
    foreach ($relative in @('database\schema\flavor_logic.sql', 'database\seeds\base_data.sql')) {
        $sql = Get-Content -LiteralPath (Join-Path $projectRoot $relative) -Raw -Encoding UTF8
        $sql = $sql.Replace('`flavor_logic`', ('`' + $DatabaseName + '`'))
        $sql | & $MySqlCommand @mysqlArgs
        if ($LASTEXITCODE -ne 0) { throw "数据库初始化失败：$relative" }
        Write-Host "已执行：$relative"
    }
    Write-Host "数据库初始化完成：$DatabaseName"
} finally {
    $env:MYSQL_PWD = $previousPassword
    $OutputEncoding = $previousEncoding
    if ($passwordPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr) }
    $databasePassword = $null
}
