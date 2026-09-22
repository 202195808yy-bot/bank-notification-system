# 前端部署脚本

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "银行通知系统 - 前端部署" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查Node.js
Write-Host "检查Node.js..." -ForegroundColor Yellow
try {
    $nodeVersion = node --version
    Write-Host "✓ Node.js版本: $nodeVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ Node.js未安装" -ForegroundColor Red
    exit 1
}

# 检查前端目录
$frontendPath = "..\bank-notification-web"
if (-not (Test-Path $frontendPath)) {
    Write-Host "✗ 前端目录不存在: $frontendPath" -ForegroundColor Red
    exit 1
}

Set-Location $frontendPath

# 检查是否需要构建
$buildExists = Test-Path "build"
$srcModified = $false

if ($buildExists) {
    $buildTime = (Get-Item "build\index.html").LastWriteTime
    $srcFiles = Get-ChildItem -Path "src" -Recurse -File
    $latestSrc = $srcFiles | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    
    if ($latestSrc -and $latestSrc.LastWriteTime -gt $buildTime) {
        Write-Host "检测到源码已更新，需要重新构建..." -ForegroundColor Yellow
        $srcModified = $true
    }
} else {
    Write-Host "首次构建..." -ForegroundColor Yellow
    $srcModified = $true
}

# 构建前端
if ($srcModified) {
    Write-Host ""
    Write-Host "正在构建前端..." -ForegroundColor Yellow
    npm run build
    if ($LASTEXITCODE -ne 0) {
        Write-Host "✗ 构建失败" -ForegroundColor Red
        exit 1
    }
    Write-Host "✓ 构建成功" -ForegroundColor Green
}

# 停止Docker前端（如果正在运行）
Write-Host ""
Write-Host "停止Docker中的旧前端容器..." -ForegroundColor Yellow
docker stop bank-frontend 2>$null
docker rm bank-frontend 2>$null

# 检查端口3000是否可用
$port3000 = Get-NetTCPConnection -LocalPort 3000 -ErrorAction SilentlyContinue
if ($port3000) {
    Write-Host "端口3000被占用，尝试释放..." -ForegroundColor Yellow
    $process = Get-Process -Id $port3000[0].OwningProcess -ErrorAction SilentlyContinue
    if ($process) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }
}

# 构建Docker镜像
Write-Host ""
Write-Host "构建Docker镜像..." -ForegroundColor Yellow
docker build -t bank-frontend:latest .
if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Docker镜像构建失败" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Docker镜像构建成功" -ForegroundColor Green

# 启动Docker容器
Write-Host ""
Write-Host "启动Docker容器..." -ForegroundColor Yellow
docker run -d --name bank-frontend -p 3000:80 --network bank-network bank-frontend:latest
if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Docker容器启动失败" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Docker容器启动成功" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "前端服务已启动" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "访问地址: http://localhost:3000" -ForegroundColor Green
Write-Host "Bank Event Sender: http://localhost:3000/admin/events" -ForegroundColor Green
Write-Host ""
Write-Host "容器名称: bank-frontend" -ForegroundColor Yellow
Write-Host "查看日志: docker logs bank-frontend" -ForegroundColor Yellow
Write-Host "停止服务: docker stop bank-frontend" -ForegroundColor Yellow
Write-Host ""
