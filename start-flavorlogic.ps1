# ============================================================
#  食之有理 FlavorLogic —— 一键启动脚本
# ------------------------------------------------------------
#  作用：
#    1. （可选）重新编译打包：-Build
#    2. 把 target/flavorlogic 部署到本项目自带的 CATALINA_BASE
#    3. 启动 Tomcat 并输出访问地址
#
#  用法：
#    powershell -ExecutionPolicy Bypass -File start-flavorlogic.ps1
#    powershell -ExecutionPolicy Bypass -File start-flavorlogic.ps1 -Build -TomcatHome "D:\Tomcat\apache-tomcat-9.0.121"
#
#  说明：脚本使用项目内的 tomcat-runtime 作为 CATALINA_BASE，
#        与系统里其他 Tomcat 实例互不影响；HTTP 端口取该目录 conf/server.xml（默认 8080）。
# ============================================================
[CmdletBinding()]
param(
    [string]$TomcatHome = 'D:\Tomcat\apache-tomcat-9.0.121',
    [string]$JavaHome = '',
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$catalinaBase = Join-Path $projectRoot 'tomcat-runtime'
$webappName = 'flavorlogic'
$port = 8080

function Write-Info($text) { Write-Host $text -ForegroundColor Cyan }

if (-not (Test-Path (Join-Path $TomcatHome 'bin\startup.bat'))) {
    throw "未找到 Tomcat：$TomcatHome（可用 -TomcatHome 指定实际路径）"
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    if ($env:JAVA_HOME) { $JavaHome = $env:JAVA_HOME } else { throw '未检测到 JAVA_HOME，请用 -JavaHome 指定 JDK 目录' }
}

# ---------- 1. 可选构建 ----------
if ($Build) {
    Write-Info '[1/4] 使用 Maven 重新打包…'
    $mvn = 'D:\apache-maven-3.8.1\bin\mvn.cmd'
    if (-not (Test-Path $mvn)) { $mvn = 'mvn' }
    & $mvn -B -f (Join-Path $projectRoot 'pom.xml') clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败，请检查控制台输出' }
} else {
    Write-Info '[1/4] 跳过构建（如需重新编译请加 -Build）'
}

$source = Join-Path $projectRoot 'target\flavorlogic'
if (-not (Test-Path (Join-Path $source 'WEB-INF\web.xml'))) {
    throw "未找到已构建的应用：$source，请先执行 mvn package 或使用 -Build 参数"
}

# ---------- 2. 准备 CATALINA_BASE ----------
Write-Info '[2/4] 准备运行目录…'
foreach ($dir in @('conf', 'logs', 'temp', 'work', 'webapps')) {
    New-Item -ItemType Directory -Path (Join-Path $catalinaBase $dir) -Force | Out-Null
}
if (-not (Test-Path (Join-Path $catalinaBase 'conf\server.xml'))) {
    Copy-Item (Join-Path $TomcatHome 'conf') -Destination $catalinaBase -Recurse -Force
    Write-Info "      已从 $TomcatHome\conf 初始化运行配置"
}
$serverXml = Join-Path $catalinaBase 'conf\server.xml'
$match = [regex]::Match((Get-Content $serverXml -Raw), '<Connector port="(\d+)"[^>]*protocol="HTTP')
if ($match.Success) { $port = [int]$match.Groups[1].Value }

# ---------- 3. 部署 ----------
Write-Info '[3/4] 部署应用…'
$target = Join-Path $catalinaBase "webapps\$webappName"
if (Test-Path $target) { Remove-Item $target -Recurse -Force }
Copy-Item $source $target -Recurse -Force
Write-Info "      已部署到 $target"

# ---------- 4. 启动 ----------
Write-Info '[4/4] 启动 Tomcat…'
$env:CATALINA_HOME = $TomcatHome
$env:CATALINA_BASE = $catalinaBase
$env:JAVA_HOME = $JavaHome
$env:CATALINA_OPTS = '-Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true'

$existing = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "端口 $port 已被占用，先尝试停止旧实例…" -ForegroundColor Yellow
    & (Join-Path $TomcatHome 'bin\shutdown.bat') 2>$null | Out-Null
    Start-Sleep -Seconds 3
}
Start-Process -FilePath (Join-Path $TomcatHome 'bin\startup.bat') -WorkingDirectory (Join-Path $TomcatHome 'bin') -WindowStyle Hidden
Start-Sleep -Seconds 8

$url = "http://localhost:$port/$webappName/"
try {
    $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 15
    Write-Host "`n启动成功：$url （HTTP $($response.StatusCode)）" -ForegroundColor Green
} catch {
    Write-Host "`nTomcat 已启动，但首页暂未响应：$url" -ForegroundColor Yellow
    Write-Host "请查看日志：$catalinaBase\logs\catalina.*.log" -ForegroundColor Yellow
}
Write-Host "演示账号：admin / Admin@123（管理员）、demo / Demo@123（普通用户）"
Write-Host "停止服务：powershell -File stop-flavorlogic.ps1"
