@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo.
echo  履途 ResumeTrail
echo  ----------------------------------------
echo.

where java >nul 2>nul || (echo [错误] 未找到 Java，请安装 JDK 21 或更高版本。 & pause & exit /b 1)
where node >nul 2>nul || (echo [错误] 未找到 Node.js，请安装 Node.js 20 或更高版本。 & pause & exit /b 1)
where npm >nul 2>nul || (echo [错误] 未找到 npm。 & pause & exit /b 1)
where mvn >nul 2>nul || (echo [错误] 未找到 Maven，请安装 Maven 3.9 或更高版本。 & pause & exit /b 1)

for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "(Get-Content -Raw config.json ^| ConvertFrom-Json).server.port"`) do set "PORT=%%p"
if "%PORT%"=="" set "PORT=18080"
set "URL=http://127.0.0.1:%PORT%"

REM 如果服务已经在运行，直接打开浏览器。
powershell -NoProfile -Command "try{$r=Invoke-WebRequest -UseBasicParsing -Uri '%URL%/api/health' -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0}}catch{}; exit 1" >nul 2>nul
if not errorlevel 1 (
  echo [信息] 服务已在运行：%URL%
  start "" "%URL%"
  exit /b 0
)

if not exist "frontend\node_modules" (
  echo [1/3] 安装前端依赖...
  pushd frontend
  call npm install --no-audit --no-fund
  if errorlevel 1 (popd & echo [错误] 前端依赖安装失败。 & pause & exit /b 1)
  popd
)

echo [2/3] 构建前端...
pushd frontend
call npm run build
if errorlevel 1 (popd & echo [错误] 前端构建失败。 & pause & exit /b 1)
popd

echo [3/3] 打包服务端...
call mvn -q -DskipTests package
if errorlevel 1 (echo [错误] 服务端打包失败。 & pause & exit /b 1)
REM 如果 IDE 的 Java 扩展同时在编译，模块 jar 可能为空；检测后重试一次。
jar tf jobtrack-common	arget\jobtrack-common-1.0.0.jar 2>nul | findstr /C:".class" >nul
if errorlevel 1 (
  echo [提示] 检测到模块编译竞争，正在重新打包...
  timeout /t 3 >nul
  call mvn -q -DskipTests package
  if errorlevel 1 (echo [错误] 服务端打包失败。 & pause & exit /b 1)
)

if not exist "log" mkdir log
echo [启动] 正在启动服务端...
start "ResumeTrail" cmd /c "java -jar jobtrack-web\target\jobtrack.jar >> log\console.log 2>&1"

set "READY=0"
for /l %%i in (1,1,90) do (
  if "!READY!"=="0" (
    powershell -NoProfile -Command "try{$r=Invoke-WebRequest -UseBasicParsing -Uri '%URL%/api/health' -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0}}catch{}; exit 1" >nul 2>nul
    if not errorlevel 1 (
      set "READY=1"
    ) else (
      timeout /t 1 >nul
    )
  )
)

if "!READY!"=="1" (
  echo [完成] 服务已启动：%URL%
  start "" "%URL%"
) else (
  echo [警告] 服务启动超时，请查看 log\console.log。
  echo         稍后可以手动打开：%URL%
)

echo.
echo 默认账号：admin / 123456
echo 配置文件：config.json
echo 日志目录：log\
echo.
endlocal
