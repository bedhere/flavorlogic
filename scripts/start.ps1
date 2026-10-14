# ============================================================
#  食之有理 FlavorLogic —— 一键启动脚本
# ------------------------------------------------------------
#  作用：
#    1. （可选）重新编译打包：-Build
#    2. 把 backend/target/flavorlogic 部署到本项目自带的 CATALINA_BASE
#    3. 启动 Tomcat 并输出访问地址
#
#  用法：
#    powershell -ExecutionPolicy Bypass -File scripts/start.ps1
#    powershell -ExecutionPolicy Bypass -File scripts/start.ps1 -Build -TomcatHome "D:\Tomcat\apache-tomcat-9.0.121"
#
#  说明：脚本使用项目内的 .runtime\tomcat 作为 CATALINA_BASE，
#        与系统里其他 Tomcat 实例互不影响；HTTP 端口取该目录 conf/server.xml（默认 8080）。
# ============================================================
[CmdletBinding()]
param(
    [string]$TomcatHome = '',
    [string]$JavaHome = '',
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($TomcatHome)) {
    if ($env:FLAVORLOGIC_TOMCAT_HOME) { $TomcatHome = $env:FLAVORLOGIC_TOMCAT_HOME }
    elseif ($env:CATALINA_HOME) { $TomcatHome = $env:CATALINA_HOME }
    else {
        $TomcatHome = @('E:\apache-tomcat-9.0.113', 'D:\Tomcat\apache-tomcat-9.0.121') |
            Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\startup.bat') } | Select-Object -First 1
    }
}
if ([string]::IsNullOrWhiteSpace($TomcatHome)) { throw '请用 -TomcatHome 指定 Tomcat 9 目录' }
$projectRoot = Split-Path $PSScriptRoot -Parent
$catalinaBase = Join-Path $projectRoot '.runtime\tomcat'
$webappName = 'flavorlogic'
$port = 8080

function Write-Info($text) { Write-Host $text -ForegroundColor Cyan }

if (-not (Test-Path (Join-Path $TomcatHome 'bin\startup.bat'))) {
    throw "未找到 Tomcat：$TomcatHome（可用 -TomcatHome 指定实际路径）"
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    if ($env:JAVA_HOME) { $JavaHome = $env:JAVA_HOME } else { throw '未检测到 JAVA_HOME，请用 -JavaHome 指定 JDK 目录' }
}

$env:JAVA_HOME = $JavaHome
$env:Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path

# ---------- 1. 可选构建 ----------
if ($Build) {
    Write-Info '[1/4] 使用 Maven 重新打包…'
    $mvn = (Get-Command mvn.cmd -ErrorAction Stop).Source
    & $mvn -B -f (Join-Path $projectRoot 'pom.xml') clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败，请检查控制台输出' }
} else {
    Write-Info '[1/4] 跳过构建（如需重新编译请加 -Build）'
}

$source = Join-Path $projectRoot 'backend\target\flavorlogic'
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
# 删除部署副本前验证目标严格位于本项目运行目录内。
$resolvedTarget = [System.IO.Path]::GetFullPath($target)
$expectedTarget = [System.IO.Path]::GetFullPath((Join-Path $projectRoot '.runtime\tomcat\webapps\flavorlogic'))
if (-not $resolvedTarget.Equals($expectedTarget, [System.StringComparison]::OrdinalIgnoreCase)) { throw '部署目标路径校验失败' }
if (Test-Path -LiteralPath $resolvedTarget) { Remove-Item -LiteralPath $resolvedTarget -Recurse -Force }
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
# 直接启动 Tomcat Bootstrap，避免 .bat 文件关联或带空格/中文路径使后台启动挂起。
$javaArguments = @(
    '-Dfile.encoding=UTF-8',
    '-Djava.net.preferIPv4Stack=true',
    ('-Djava.util.logging.config.file="' + (Join-Path $catalinaBase 'conf\logging.properties') + '"'),
    '-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager',
    ('-Dcatalina.home="' + $TomcatHome + '"'),
    ('-Dcatalina.base="' + $catalinaBase + '"'),
    ('-Djava.io.tmpdir="' + (Join-Path $catalinaBase 'temp') + '"'),
    '--add-opens=java.base/java.lang=ALL-UNNAMED',
    '--add-opens=java.base/java.io=ALL-UNNAMED',
    '--add-opens=java.base/java.util=ALL-UNNAMED',
    '-classpath',
    ('"' + (Join-Path $TomcatHome 'bin\bootstrap.jar') + ';' + (Join-Path $TomcatHome 'bin\tomcat-juli.jar') + '"'),
    'org.apache.catalina.startup.Bootstrap',
    'start'
)
$serverProcess = Start-Process -FilePath (Join-Path $JavaHome 'bin\java.exe') -ArgumentList $javaArguments `
    -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $catalinaBase 'logs\startup.stdout.log') `
    -RedirectStandardError (Join-Path $catalinaBase 'logs\startup.stderr.log')
$serverProcess.Id | Set-Content -LiteralPath (Join-Path $catalinaBase 'flavorlogic.pid')
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
Write-Host "停止服务：powershell -File scripts/stop.ps1"
