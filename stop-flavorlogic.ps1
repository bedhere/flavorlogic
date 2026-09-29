# ============================================================
#  食之有理 FlavorLogic —— 停止脚本
# ------------------------------------------------------------
#  用法：powershell -ExecutionPolicy Bypass -File stop-flavorlogic.ps1
# ============================================================
[CmdletBinding()]
param(
    [string]$TomcatHome = 'D:\Tomcat\apache-tomcat-9.0.121'
)

$ErrorActionPreference = 'Continue'
$catalinaBase = Join-Path $PSScriptRoot 'tomcat-runtime'

if (-not (Test-Path (Join-Path $TomcatHome 'bin\shutdown.bat'))) {
    throw "未找到 Tomcat：$TomcatHome"
}
if (-not (Test-Path (Join-Path $catalinaBase 'conf\server.xml'))) {
    Write-Host '未发现项目运行目录，说明应用未通过本脚本启动过。' -ForegroundColor Yellow
    return
}

$env:CATALINA_HOME = $TomcatHome
$env:CATALINA_BASE = $catalinaBase

Write-Host '正在停止 Tomcat…' -ForegroundColor Cyan
& (Join-Path $TomcatHome 'bin\shutdown.bat') 2>$null | Out-Null
Start-Sleep -Seconds 5

$port = 8080
$match = [regex]::Match((Get-Content (Join-Path $catalinaBase 'conf\server.xml') -Raw), '<Connector port="(\d+)"[^>]*protocol="HTTP')
if ($match.Success) { $port = [int]$match.Groups[1].Value }

$listening = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
if ($listening) {
    Write-Host "端口 $port 仍在监听，Tomcat 可能仍未退出。" -ForegroundColor Yellow
} else {
    Write-Host '已停止。' -ForegroundColor Green
}
