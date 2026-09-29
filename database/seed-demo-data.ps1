# ============================================================
#  食之有理 FlavorLogic —— 演示数据初始化脚本
# ------------------------------------------------------------
#  前置条件：
#    1. MySQL 已建库建表（database/flavor_logic.sql）
#    2. Tomcat 已启动本应用（默认 http://127.0.0.1:8080/flavorlogic）
#    3. 已安装 mysql 客户端（用于把 admin 提升为管理员）
#
#  脚本作用（可重复执行，已存在的数据会跳过或复用）：
#    · 创建演示账号：admin / Admin@123（管理员）、demo / Demo@123（普通用户）
#    · 发布 6 篇研发知识文章（覆盖风味科学、原料知识、健康化改造、区域口味、研发案例）
#    · 创建 4 份示例配方，并各执行 1 次风味分析（分别命中不同规则）
#    · 为其中一次分析提交试产反馈
#
#  用法：
#    pwsh -File database\seed-demo-data.ps1
#    可选参数：-BaseUrl http://127.0.0.1:8080/flavorlogic -MysqlExe "D:\MySQL Server 5.7\bin\mysql.exe"
#              -DbPassword 你的root密码 -SkipAdminPromote
# ============================================================
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/flavorlogic',
    [string]$MysqlExe = 'D:\MySQL Server 5.7\bin\mysql.exe',
    [string]$DbUser = 'root',
    [string]$DbPassword = '',
    [string]$Database = 'flavor_logic',
    [switch]$SkipAdminPromote
)

$ErrorActionPreference = 'Stop'
$script:ok = 0
$script:skipped = 0

function Write-Step([string]$text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }
function Write-Ok([string]$text)   { $script:ok++; Write-Host "  [OK]   $text" -ForegroundColor Green }
function Write-Skip([string]$text) { $script:skipped++; Write-Host "  [SKIP] $text" -ForegroundColor Yellow }
function Write-Warn2([string]$text){ Write-Host "  [WARN] $text" -ForegroundColor Yellow }

function New-Session {
    return New-Object Microsoft.PowerShell.Commands.WebRequestSession
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body,
        $Session,
        [switch]$AllowFailure
    )
    $uri = "$BaseUrl$Path"
    $params = @{ Uri = $uri; Method = $Method; TimeoutSec = 60; WebSession = $Session; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
        $params.ContentType = 'application/json; charset=utf-8'
    }
    try {
        $raw = Invoke-WebRequest @params
        $text = [System.Text.Encoding]::UTF8.GetString($raw.RawContentStream.ToArray())
        return $text | ConvertFrom-Json
    } catch {
        if ($AllowFailure) {
            $response = $_.Exception.Response
            if ($response) {
                $reader = New-Object System.IO.StreamReader($response.GetResponseStream(), [System.Text.Encoding]::UTF8)
                return ($reader.ReadToEnd() | ConvertFrom-Json)
            }
            return [pscustomobject]@{ code = -1; message = $_.Exception.Message }
        }
        throw
    }
}

function Ensure-Account {
    param([string]$Username, [string]$Nickname, [string]$Password)
    $session = New-Session
    $register = Invoke-Api -Method POST -Path '/api/auth/register' -Session $session -AllowFailure -Body @{
        username = $Username; nickname = $Nickname; password = $Password; confirmPassword = $Password
    }
    if ($register.code -eq 0) {
        Write-Ok "注册账号 $Username（$Nickname）"
    } elseif ($register.message -match '已存在|已被注册') {
        Write-Skip "账号 $Username 已存在"
    } else {
        throw "账号 $Username 注册失败：$($register.message)"
    }
    # 注册接口只创建账号、不建立会话（与本应用的注册逻辑一致），因此这里显式登录
    $login = Invoke-Api -Method POST -Path '/api/auth/login' -Session $session -AllowFailure -Body @{
        username = $Username; password = $Password
    }
    if ($login.code -ne 0) {
        throw "账号 $Username 登录失败：$($login.message)"
    }
    return $session
}

function New-Recipe {
    param($Session, [string]$Name, [string]$ProductType, [string]$ProcessNote, [array]$Items)
    $existing = Invoke-Api -Method GET -Path '/api/recipes?page=1&pageSize=50' -Session $Session
    foreach ($item in $existing.data.list) {
        if ($item.name -eq $Name) {
            Write-Skip "配方「$Name」已存在（编号 $($item.id)）"
            return $item.id
        }
    }
    $result = Invoke-Api -Method POST -Path '/api/recipes' -Session $Session -Body @{
        name = $Name; productType = $ProductType; processNote = $ProcessNote; remark = '演示数据'; items = $Items
    }
    if ($result.code -ne 0) { throw "创建配方「$Name」失败：$($result.message)" }
    Write-Ok "创建配方「$Name」（编号 $($result.data.id)，$($Items.Count) 项配料）"
    return $result.data.id
}

function New-Analysis {
    param($Session, [string]$TaskName, [int]$RecipeId, [string]$GoalType, [int]$RegionProfileId, [array]$TargetItems, $Constraints)
    # 幂等：同名任务已存在则跳过，避免重复执行脚本产生重复记录
    $history = Invoke-Api -Method GET -Path ("/api/analysis/history?page=1&pageSize=50&keyword=" + [uri]::EscapeDataString($TaskName)) -Session $Session
    foreach ($item in $history.data.list) {
        if ($item.taskName -eq $TaskName) {
            Write-Skip "分析「$TaskName」已存在（任务 #$($item.id)）"
            return $item.id
        }
    }
    $body = @{ taskName = $TaskName; recipeId = $RecipeId; goalType = $GoalType; targetItems = $TargetItems; constraints = $Constraints }
    if ($RegionProfileId -gt 0) { $body.regionProfileId = $RegionProfileId }
    $result = Invoke-Api -Method POST -Path '/api/analysis' -Session $Session -Body $body -AllowFailure
    if ($result.code -ne 0) {
        Write-Warn2 "分析「$TaskName」失败：$($result.message)"
        return $null
    }
    $suggestions = @($result.data.result.suggestions)
    Write-Ok ("分析「{0}」→ 任务 #{1}，状态 {2}，建议 {3} 条：{4}" -f $TaskName, $result.data.id, $result.data.status,
        $suggestions.Count, (($suggestions | ForEach-Object { $_.ruleName }) -join ' / '))
    return $result.data.id
}

function New-Article {
    param($Session, [string]$CategoryName, [string]$Title, [string]$Summary, [string]$Content,
          [string]$SourceName, [string]$SourceUrl)
    $categories = Invoke-Api -Method GET -Path '/api/admin/articles/categories' -Session $Session
    $categoryId = $null
    foreach ($category in $categories.data) { if ($category.name -eq $CategoryName) { $categoryId = $category.id } }
    $body = @{
        title = $Title; summary = $Summary; content = $Content; status = 1
        categoryId = $categoryId; sourceName = $SourceName; sourceUrl = $SourceUrl
    }
    $result = Invoke-Api -Method POST -Path '/api/admin/articles' -Session $Session -Body $body -AllowFailure
    if ($result.code -ne 0) {
        if ($result.message -match '已存在|重复') { Write-Skip "文章「$Title」已存在，跳过"; return }
        throw "发布文章「$Title」失败：$($result.message)"
    }
    Write-Ok "发布文章「$Title」（编号 $($result.data.id)）"
}

# ------------------------------------------------------------
Write-Host "食之有理 FlavorLogic 演示数据初始化" -ForegroundColor White
Write-Host "目标应用：$BaseUrl"

Write-Step '1/5 检查应用连通性'
$ping = Invoke-Api -Method GET -Path '/api/articles?page=1&pageSize=1' -Session (New-Session) -AllowFailure
if ($ping.code -ne 0) { throw "应用未就绪（$BaseUrl）：$($ping.message)。请先启动 Tomcat。" }
Write-Ok '应用接口可访问，数据库连接正常'

Write-Step '2/5 创建演示账号'
$adminSession = Ensure-Account -Username 'admin' -Nickname '系统管理员' -Password 'Admin@123'
$demoSession = Ensure-Account -Username 'demo' -Nickname '研发小食' -Password 'Demo@123'

Write-Step '3/5 将 admin 提升为管理员'
if ($SkipAdminPromote) {
    Write-Skip '已指定 -SkipAdminPromote'
} elseif (-not (Test-Path $MysqlExe)) {
    Write-Warn2 "未找到 mysql 客户端（$MysqlExe），请手动执行：UPDATE sys_user SET role='ADMIN' WHERE username='admin';"
} else {
    $sql = "USE $Database; UPDATE sys_user SET role='ADMIN' WHERE username='admin' AND role<>'ADMIN'; SELECT username, role FROM sys_user;"
    # mysql 客户端会把“命令行携带密码”的告警写到 stderr，这里临时放宽错误策略，避免被当作致命错误
    $previousPolicy = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & $MysqlExe -u $DbUser "--password=$DbPassword" --default-character-set=utf8mb4 -e $sql 2>&1 |
        Where-Object { "$_" -notmatch '^mysql: \[Warning\]' }
    $ErrorActionPreference = $previousPolicy
    Write-Ok "已同步角色：`n$($output -join "`n")"
    $adminSession = New-Session
    Invoke-Api -Method POST -Path '/api/auth/login' -Session $adminSession -Body @{ username = 'admin'; password = 'Admin@123' } | Out-Null
}

Write-Step '4/5 发布研发知识文章'
New-Article -Session $adminSession -CategoryName '风味科学' -SourceName '食之有理研发手册' -SourceUrl 'https://example.com/flavor/8d-vector' `
    -Title '八维风味向量：把“好吃”拆成可计算的数字' `
    -Summary '甜、咸、酸、苦、鲜、辣、麻、脂香八个维度如何取值、如何加权，以及为什么必须做归一化。' `
    -Content @'
<h2>为什么是八个维度</h2>
<p>舌头能直接说出的味道只有甜、咸、酸、苦、鲜五种，而决定“这碗汤厚不厚”“这口辣上不上头”的，往往是说不清的那几维：辣、麻、脂香。</p>
<h2>取值口径</h2>
<ul>
  <li>每个食材在每个维度上取 0-100 的相对评分，表示“该食材在这个维度上的强度”；</li>
  <li>配方维度得分 = Σ（食材用量占比 × 该食材的维度属性值），是一致化后的加权平均；</li>
  <li>必须按配方总用量归一化，否则“配方做大一倍”会让所有维度凭空翻倍。</li>
</ul>
<blockquote>演示数据说明：本平台的属性值用于验证业务与技术闭环，不等同于实验室检测结果。</blockquote>
'@
New-Article -Session $adminSession -CategoryName '原料知识' -SourceName '食之有理研发手册' -SourceUrl 'https://example.com/ingredient/umami-paths' `
    -Title '酵母抽提物与味精：两条鲜味路径的差异' `
    -Summary '同样补鲜，为什么控脂配方更倾向酵母抽提物？从鲜味强度、后味与标签友好度三个角度看。' `
    -Content @'
<h2>两条路径</h2>
<p>味精提供的是直接、干净的鲜味；酵母抽提物除鲜味外还带有核苷酸与氨基酸带来的醇厚感，在减油减盐的配方里更容易补上“厚度”。</p>
<h2>替换时的注意点</h2>
<ul>
  <li>酵母抽提物用量通常在 0.05%-0.30%（占配方总量），过量会带来发酵气味；</li>
  <li>与香菇粉复配可以拉长鲜味尾巴；</li>
  <li>补鲜无法替代脂香，控脂场景应同时考虑低用量增香或工艺增香。</li>
</ul>
'@
New-Article -Session $adminSession -CategoryName '健康化改造' -SourceName '食之有理研发手册' -SourceUrl 'https://example.com/health/sugar-reduction' `
    -Title '减糖 30% 之后，甜味为什么“塌”了' `
    -Summary '甜味下降不只是数值变小，酸甜平衡与后味风险才是研发真正要处理的问题。' `
    -Content @'
<h2>三个连锁反应</h2>
<ol>
  <li>甜度下降后酸味相对突出，整体轮廓向“尖”偏移；</li>
  <li>代糖存在后味：赤藓糖醇的凉感、甜菊糖苷的金属与后苦、三氯蔗糖的苦尾；</li>
  <li>减糖会同时削弱体感厚度，需要靠增稠或微量盐来补。</li>
</ol>
<h2>可执行的思路</h2>
<p>复配代糖、微调酸度、少量食盐增甜，再用分析结果验证“甜度回到目标区间、苦味没有明显上升”。</p>
'@
New-Article -Session $adminSession -CategoryName '健康化改造' -SourceName '食之有理研发手册' -SourceUrl 'https://example.com/health/fat-reduction' `
    -Title '控脂改造：脂香下降不等于只能把油加回去' `
    -Summary '油脂减少会同时带走脂香与载香能力，补偿方向应优先低用量增香与工艺增香。' `
    -Content @'
<h2>脂香为什么掉得最快</h2>
<p>油脂不只提供口感，还承担香气溶剂的作用。用量下降后，脂溶性香气物质的释放也随之减弱，因此脂香维度往往最先报警。</p>
<h2>不建议的做法</h2>
<p>控脂目标下直接把原油脂用量加回去，等于放弃研发目标。</p>
<h2>建议方向</h2>
<ul>
  <li>低用量增香：鸡油、香辛料精油等少量高效原料；</li>
  <li>工艺增香：先炒后煮、分段投料，提高香气物质的保留率；</li>
  <li>结构补偿：用胶体或淀粉体系补回厚度感。</li>
</ul>
'@
New-Article -Session $adminSession -CategoryName '区域口味' -SourceName '食之有理研发手册' -SourceUrl 'https://example.com/region/taste-profile' `
    -Title '一版打全国为什么难：西南、华东、华南的口味差异' `
    -Summary '同一份基准配方，在不同区域需要调整的维度并不相同，用权重描述偏好比“多放点辣”更可执行。' `
    -Content @'
<h2>用权重描述偏好</h2>
<p>平台为每个区域维护八个维度的偏好权重：1.000 表示与全国基准一致，大于 1 表示偏好更强。</p>
<table>
  <thead><tr><th>区域</th><th>更偏好</th><th>相对收敛</th></tr></thead>
  <tbody>
    <tr><td>西南地区</td><td>辣 1.20、麻 1.25</td><td>苦 0.95</td></tr>
    <tr><td>华东地区</td><td>甜 1.15</td><td>辣 0.80、麻 0.80</td></tr>
    <tr><td>华南地区</td><td>鲜 1.15</td><td>辣 0.75、脂香 0.85</td></tr>
  </tbody>
</table>
<p>区域适配的正确用法是“一个基准配方 + 三套微调参数”，而不是重做三份配方。</p>
'@
New-Article -Session $adminSession -CategoryName '研发案例' -SourceName '食之有理研发手册' -SourceUrl 'https://example.com/case/beef-to-chicken' `
    -Title '研发案例：牛肉换鸡胸肉，一次可解释的配方推演' `
    -Summary '主料替换造成脂香与鲜味同时下降时，系统如何给出候选补偿方向与参考幅度。' `
    -Content @'
<h2>背景</h2>
<p>牛肉成本上涨，研发希望用鸡胸肉替代主料并保持“肉香与鲜味”。</p>
<h2>推演结果</h2>
<p>替换后脂香与鲜味明显下降，系统命中“鲜味明显下降补偿”规则，给出酵母抽提物、香菇粉等候选方向与 0.05%-0.30% 的参考幅度，并提示“补鲜不能替代脂香”。</p>
<h2>落地</h2>
<p>按建议调整第 2 版配方后重新分析，确认鲜味回到基准区间、脂香不再报警，再进入试产与感官评价。</p>
'@

Write-Step '5/5 创建示例配方与分析记录'
$recipeA = New-Recipe -Session $demoSession -Name '低脂红烧肉（控脂基线）' -ProductType '预制菜' -ProcessNote '焯水后焖煮 40 分钟，收汁前撇去浮油' -Items @(
    @{ ingredientId = 13; ingredientName = '猪肉';   amount = 100; unit = 'g' },
    @{ ingredientId = 9;  ingredientName = '菜籽油'; amount = 8;   unit = 'ml' },
    @{ ingredientId = 4;  ingredientName = '酱油';   amount = 3;   unit = 'ml' },
    @{ ingredientId = 1;  ingredientName = '白砂糖'; amount = 3;   unit = 'g' },
    @{ ingredientId = 2;  ingredientName = '食盐';   amount = 1;   unit = 'g' },
    @{ ingredientId = 5;  ingredientName = '味精';   amount = 0.5; unit = 'g' }
)
New-Analysis -Session $demoSession -RecipeId $recipeA -TaskName '控脂 25%：减油与主料替换后的脂香补偿' -GoalType 'REDUCE_FAT' -RegionProfileId 0 -Constraints @{
    healthGoal = 'LOW_FAT'; costLimit = 3.5; allergens = @('花生'); note = '目标：猪肉减至 70g、油脂减至 2ml，并维持整体风味'
} -TargetItems @(
    @{ ingredientId = 13; ingredientName = '猪肉';       amount = 70;  unit = 'g' },
    @{ ingredientId = 11; ingredientName = '鸡胸肉';     amount = 30;  unit = 'g' },
    @{ ingredientId = 9;  ingredientName = '菜籽油';     amount = 2;   unit = 'ml' },
    @{ ingredientId = 4;  ingredientName = '酱油';       amount = 3;   unit = 'ml' },
    @{ ingredientId = 1;  ingredientName = '白砂糖';     amount = 2;   unit = 'g' },
    @{ ingredientId = 2;  ingredientName = '食盐';       amount = 1;   unit = 'g' },
    @{ ingredientId = 5;  ingredientName = '味精';       amount = 0.5; unit = 'g' },
    @{ ingredientId = 6;  ingredientName = '酵母抽提物'; amount = 0.3; unit = 'g' }
) | Out-Null

$recipeB = New-Recipe -Session $demoSession -Name '0 糖茶饮基底' -ProductType '现制饮品' -ProcessNote '糖浆单独复配后与茶汤按 1:9 混合' -Items @(
    @{ ingredientId = 1;  ingredientName = '白砂糖';   amount = 80;  unit = 'g' },
    @{ ingredientId = 16; ingredientName = '柠檬汁';   amount = 20;  unit = 'ml' },
    @{ ingredientId = 2;  ingredientName = '食盐';     amount = 0.5; unit = 'g' },
    @{ ingredientId = 3;  ingredientName = '白醋';     amount = 5;   unit = 'ml' }
)
New-Analysis -Session $demoSession -RecipeId $recipeB -TaskName '减糖 100%：代糖复配与后味风险' -GoalType 'REDUCE_SUGAR' -RegionProfileId 0 -Constraints @{
    healthGoal = 'LOW_SUGAR'; note = '目标：完全去蔗糖，用赤藓糖醇与甜菊糖苷复配'
} -TargetItems @(
    @{ ingredientId = 14; ingredientName = '赤藓糖醇'; amount = 55;  unit = 'g' },
    @{ ingredientId = 15; ingredientName = '甜菊糖苷'; amount = 0.3; unit = 'g' },
    @{ ingredientId = 16; ingredientName = '柠檬汁';   amount = 20;  unit = 'ml' },
    @{ ingredientId = 2;  ingredientName = '食盐';     amount = 0.5; unit = 'g' },
    @{ ingredientId = 3;  ingredientName = '白醋';     amount = 5;   unit = 'ml' }
) | Out-Null

$recipeC = New-Recipe -Session $demoSession -Name '香辣牛肉干（改版前）' -ProductType '休闲肉制品' -ProcessNote '腌制 12 小时后 70℃ 热风干燥' -Items @(
    @{ ingredientId = 12; ingredientName = '牛肉';   amount = 100; unit = 'g' },
    @{ ingredientId = 2;  ingredientName = '食盐';   amount = 1.5; unit = 'g' },
    @{ ingredientId = 7;  ingredientName = '辣椒粉'; amount = 2;   unit = 'g' },
    @{ ingredientId = 8;  ingredientName = '花椒粉'; amount = 1;   unit = 'g' },
    @{ ingredientId = 9;  ingredientName = '菜籽油'; amount = 5;   unit = 'ml' },
    @{ ingredientId = 1;  ingredientName = '白砂糖'; amount = 2;   unit = 'g' },
    @{ ingredientId = 4;  ingredientName = '酱油';   amount = 3;   unit = 'ml' }
)
$taskC = New-Analysis -Session $demoSession -RecipeId $recipeC -TaskName '原料替换：牛肉换鸡胸肉的损失评估' -GoalType 'REPLACE' -RegionProfileId 0 -Constraints @{
    healthGoal = 'LOW_FAT'; note = '目标：主料替换后仍保持肉香与鲜味'
} -TargetItems @(
    @{ ingredientId = 11; ingredientName = '鸡胸肉'; amount = 100; unit = 'g' },
    @{ ingredientId = 2;  ingredientName = '食盐';   amount = 1.5; unit = 'g' },
    @{ ingredientId = 7;  ingredientName = '辣椒粉'; amount = 2;   unit = 'g' },
    @{ ingredientId = 8;  ingredientName = '花椒粉'; amount = 1;   unit = 'g' },
    @{ ingredientId = 9;  ingredientName = '菜籽油'; amount = 5;   unit = 'ml' },
    @{ ingredientId = 1;  ingredientName = '白砂糖'; amount = 2;   unit = 'g' },
    @{ ingredientId = 4;  ingredientName = '酱油';   amount = 3;   unit = 'ml' }
)

$recipeD = New-Recipe -Session $demoSession -Name '水煮鱼底料（全国版）' -ProductType '复合调味料' -ProcessNote '香料低温浸提后与油脂混合' -Items @(
    @{ ingredientId = 13; ingredientName = '猪肉';   amount = 100; unit = 'g' },
    @{ ingredientId = 7;  ingredientName = '辣椒粉'; amount = 3;   unit = 'g' },
    @{ ingredientId = 8;  ingredientName = '花椒粉'; amount = 1.5; unit = 'g' },
    @{ ingredientId = 9;  ingredientName = '菜籽油'; amount = 8;   unit = 'ml' },
    @{ ingredientId = 2;  ingredientName = '食盐';   amount = 2;   unit = 'g' },
    @{ ingredientId = 5;  ingredientName = '味精';   amount = 0.6; unit = 'g' }
)
New-Analysis -Session $demoSession -RecipeId $recipeD -TaskName '区域适配：西南版麻辣微调' -GoalType 'REGION_ADAPT' -RegionProfileId 2 -Constraints @{
    healthGoal = 'NONE'; note = '目标：按西南地区偏好加强麻辣'
} -TargetItems @(
    @{ ingredientId = 13; ingredientName = '猪肉';   amount = 100; unit = 'g' },
    @{ ingredientId = 7;  ingredientName = '辣椒粉'; amount = 3.6; unit = 'g' },
    @{ ingredientId = 8;  ingredientName = '花椒粉'; amount = 2.2; unit = 'g' },
    @{ ingredientId = 9;  ingredientName = '菜籽油'; amount = 8;   unit = 'ml' },
    @{ ingredientId = 2;  ingredientName = '食盐';   amount = 2;   unit = 'g' },
    @{ ingredientId = 5;  ingredientName = '味精';   amount = 0.6; unit = 'g' }
) | Out-Null

if ($taskC) {
    $feedback = Invoke-Api -Method POST -Path "/api/analysis/$taskC/feedback" -Session $demoSession -AllowFailure -Body @{
        helpful = 1; rating = 5; trialResult = 'PASSED'
        comment = '按建议补了 0.2% 酵母抽提物，第 2 版内部试吃一次通过，脂香仍略弱但可接受。'
    }
    if ($feedback.code -eq 0) { Write-Ok "已为任务 #$taskC 提交试产反馈" } else { Write-Warn2 "反馈提交失败：$($feedback.message)" }
}

Write-Host "`n----------------------------------------" -ForegroundColor White
Write-Host ("演示数据初始化完成：成功 {0} 项，跳过 {1} 项" -f $script:ok, $script:skipped) -ForegroundColor Green
Write-Host '演示账号：admin / Admin@123（管理员）、demo / Demo@123（普通用户）' -ForegroundColor White
Write-Host "访问地址：$BaseUrl/" -ForegroundColor White
