@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM  project_lumen Android 签名配置脚本 (Windows CMD)
REM  生成 keystore 并自动配置 GitHub Secrets
REM ============================================================

echo.
echo ========================================
echo   project_lumen Android 签名配置
echo ========================================
echo.

REM --- 检查前置依赖 ---
echo [1/6] 检查依赖工具...

where keytool >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 keytool，请安装 JDK 并添加到 PATH
    echo        下载地址: https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)
echo   ✓ keytool 已安装

where gh >nul 2>&1
if %errorlevel% neq 0 (
    echo [警告] 在 PATH 中未找到 gh 命令
    echo.
    echo 正在检查常见安装位置...
    
    REM 检查常见安装路径
    set "GH_PATH="
    if exist "D:\Program Files\GitHub CLI\gh.exe" set "GH_PATH=D:\Program Files\GitHub CLI\gh.exe"
    if exist "D:\Program Files (x86)\GitHub CLI\gh.exe" set "GH_PATH=D:\Program Files (x86)\GitHub CLI\gh.exe"
    if exist "%LOCALAPPDATA%\Programs\GitHub CLI\gh.exe" set "GH_PATH=%LOCALAPPDATA%\Programs\GitHub CLI\gh.exe"
    if exist "%ProgramFiles%\gh\gh.exe" set "GH_PATH=%ProgramFiles%\gh\gh.exe"
    
    if "!GH_PATH!"=="" (
        echo [错误] 未找到 GitHub CLI 安装
        echo.
        echo 请执行以下操作之一:
        echo   1. 下载并安装: https://cli.github.com/
        echo   2. 使用 winget 安装: winget install --id GitHub.cli
        echo   3. 使用 scoop 安装: scoop install gh
        echo   4. 安装后重启终端或添加到 PATH
        echo.
        pause
        exit /b 1
    )
    
    echo   ✓ 找到 GitHub CLI: !GH_PATH!
    echo   提示: 建议将 GitHub CLI 添加到 PATH 环境变量
    set "gh=!GH_PATH!"
) else (
    echo   ✓ GitHub CLI 已安装
    set "gh=gh"
)

where certutil >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 certutil（Windows 自带工具）
    pause
    exit /b 1
)
echo   ✓ certutil 可用

where powershell >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 PowerShell
    pause
    exit /b 1
)
echo   ✓ PowerShell 可用

echo.
echo [2/6] 检查 GitHub 登录状态...

REM 检查是否使用环境变量 GH_TOKEN
if defined GH_TOKEN (
    echo   ✓ 检测到 GH_TOKEN 环境变量
    "%gh%" auth status >nul 2>&1
    if %errorlevel% neq 0 (
        echo [错误] GH_TOKEN 无效或已过期
        echo.
        echo 请执行以下操作之一:
        echo   1. 清除环境变量后重新登录:
        echo      set GH_TOKEN=
        echo      gh auth login
        echo.
        echo   2. 更新 GH_TOKEN 为有效的 Personal Access Token
        echo      创建地址: https://github.com/settings/tokens
        echo.
        pause
        exit /b 1
    )
    echo   ✓ 已通过 GH_TOKEN 认证
) else (
    "%gh%" auth status >nul 2>&1
    if %errorlevel% neq 0 (
        echo [错误] 未登录 GitHub CLI
        echo.
        echo 请先运行以下命令登录:
        echo   "%gh%" auth login
        echo.
        echo 然后选择:
        echo   1. GitHub.com
        echo   2. HTTPS 协议
        echo   3. Login with a web browser
        echo.
        pause
        exit /b 1
    )
    echo   ✓ 已登录 GitHub
)

echo.
echo [3/6] 检测 GitHub 仓库...

REM 尝试多种方式检测仓库
set "REPO="

REM 方法1: 使用 gh repo view
for /f "tokens=*" %%r in ('"%gh%" repo view --json nameWithOwner -q .nameWithOwner 2^>nul') do set "REPO=%%r"

REM 方法2: 如果方法1失败，从 git remote 解析
if "%REPO%"=="" (
    echo   方法1失败，尝试从 git remote 解析...
    for /f "tokens=*" %%u in ('git config --get remote.origin.url 2^>nul') do set "REMOTE_URL=%%u"
    
    if not "!REMOTE_URL!"=="" (
        REM 解析 https://github.com/owner/repo.git 或 git@github.com:owner/repo.git
        echo !REMOTE_URL! | findstr /C:"github.com" >nul
        if !errorlevel! equ 0 (
            REM 提取 owner/repo
            set "TEMP_URL=!REMOTE_URL!"
            set "TEMP_URL=!TEMP_URL:https://github.com/=!"
            set "TEMP_URL=!TEMP_URL:git@github.com:=!"
            set "TEMP_URL=!TEMP_URL:.git=!"
            set "REPO=!TEMP_URL!"
        )
    )
)

if "%REPO%"=="" (
    echo [错误] 无法检测到 GitHub 仓库
    echo.
    echo 请确保:
    echo   1. 当前目录是 git 仓库
    echo   2. 仓库已关联 GitHub remote
    echo   3. 已推送到 GitHub
    echo.
    echo 调试信息:
    git remote -v
    echo.
    pause
    exit /b 1
)
echo   ✓ 检测到仓库: %REPO%

echo.
echo [4/6] 配置签名参数...
echo.

REM --- 配置变量 ---
set "KEYSTORE_FILE=project_lumen-release.jks"
set "KEY_ALIAS=project_lumen"
set "STORE_PASSWORD="
set "KEY_PASSWORD="
set "DNAME=CN=project_lumen, OU=Dev, O=project_lumen, L=Unknown, ST=Unknown, C=US"

REM --- 输入密码 ---
echo 请输入 keystore 密码（至少 6 位）:
set /p STORE_PASSWORD="> "
if "%STORE_PASSWORD%"=="" (
    echo [错误] 密码不能为空
    pause
    exit /b 1
)

echo.
echo 请输入 key 密码（直接回车则使用与 keystore 相同的密码）:
set /p KEY_PASSWORD="> "
if "%KEY_PASSWORD%"=="" set "KEY_PASSWORD=%STORE_PASSWORD%"

echo.
echo [5/6] 生成 keystore 文件...

if exist "%KEYSTORE_FILE%" (
    echo [警告] %KEYSTORE_FILE% 已存在，备份为 %KEYSTORE_FILE%.bak
    copy /y "%KEYSTORE_FILE%" "%KEYSTORE_FILE%.bak" >nul
)

keytool -genkeypair ^
    -v ^
    -keystore "%KEYSTORE_FILE%" ^
    -alias %KEY_ALIAS% ^
    -keyalg RSA ^
    -keysize 2048 ^
    -validity 10000 ^
    -storepass "%STORE_PASSWORD%" ^
    -keypass "%KEY_PASSWORD%" ^
    -dname "%DNAME%"

if %errorlevel% neq 0 (
    echo [错误] 生成 keystore 失败
    pause
    exit /b 1
)

echo   ✓ Keystore 生成成功: %KEYSTORE_FILE%

echo.
echo   正在编码为 Base64...
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false); $OutputEncoding=[Console]::OutputEncoding; [Convert]::ToBase64String([IO.File]::ReadAllBytes('%KEYSTORE_FILE%'))" > keystore_base64.txt
if %errorlevel% neq 0 (
    echo [错误] Base64 编码失败
    pause
    exit /b 1
)

REM 读取 Base64 内容（去除换行符）
set "KEYSTORE_BASE64="
for /f "usebackq delims=" %%b in ("keystore_base64.txt") do (
    set "KEYSTORE_BASE64=!KEYSTORE_BASE64!%%b"
)

if "%KEYSTORE_BASE64%"=="" (
    echo [错误] Base64 内容为空
    pause
    exit /b 1
)

echo   ✓ Base64 编码完成 (长度: !KEYSTORE_BASE64:~0,50!...)

echo.
echo [6/6] 准备设置 GitHub Secrets...
echo.
echo ========================================
echo   即将推送以下变量到远程仓库
echo ========================================
echo.
echo 仓库: %REPO%
echo.
echo 变量列表:
echo   1. KEYSTORE_BASE64
echo      长度: 
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false); $OutputEncoding=[Console]::OutputEncoding; Write-Output ('      ' + '%KEYSTORE_BASE64%'.Length + ' 字符')"
echo      预览: %KEYSTORE_BASE64:~0,60%...
echo.
echo   2. KEYSTORE_PASSWORD
echo      值: %STORE_PASSWORD%
echo.
echo   3. KEY_ALIAS
echo      值: %KEY_ALIAS%
echo.
echo   4. KEY_PASSWORD
echo      值: %KEY_PASSWORD%
echo.
echo ========================================
echo.
echo [警告] 这些敏感信息将被推送到 GitHub Secrets
echo        请确认以上信息正确无误
echo.
set /p CONFIRM="确认推送? (输入 YES 继续): "

if /i not "%CONFIRM%"=="YES" (
    echo.
    echo [已取消] 未推送任何变量
    echo.
    pause
    exit /b 0
)

echo.
echo 开始推送变量...
echo.

echo   设置 KEYSTORE_BASE64...
type keystore_base64.txt | "%gh%" secret set KEYSTORE_BASE64 --repo %REPO%
if %errorlevel% neq 0 (
    echo [错误] 设置 KEYSTORE_BASE64 失败
    pause
    exit /b 1
)
echo   ✓ KEYSTORE_BASE64

echo   设置 KEYSTORE_PASSWORD...
echo %STORE_PASSWORD% | "%gh%" secret set KEYSTORE_PASSWORD --repo %REPO%
if %errorlevel% neq 0 (
    echo [错误] 设置 KEYSTORE_PASSWORD 失败
    pause
    exit /b 1
)
echo   ✓ KEYSTORE_PASSWORD

echo   设置 KEY_ALIAS...
echo %KEY_ALIAS% | "%gh%" secret set KEY_ALIAS --repo %REPO%
if %errorlevel% neq 0 (
    echo [错误] 设置 KEY_ALIAS 失败
    pause
    exit /b 1
)
echo   ✓ KEY_ALIAS

echo   设置 KEY_PASSWORD...
echo %KEY_PASSWORD% | "%gh%" secret set KEY_PASSWORD --repo %REPO%
if %errorlevel% neq 0 (
    echo [错误] 设置 KEY_PASSWORD 失败
    pause
    exit /b 1
)
echo   ✓ KEY_PASSWORD

echo.
echo ========================================
echo   配置完成！
echo ========================================
echo.
echo Keystore 文件: %KEYSTORE_FILE%
echo Key 别名     : %KEY_ALIAS%
echo.
echo 已在 %REPO% 设置以下 GitHub Secrets:
echo   ✓ KEYSTORE_BASE64
echo   ✓ KEYSTORE_PASSWORD
echo   ✓ KEY_ALIAS
echo   ✓ KEY_PASSWORD
echo.
echo [重要提示]
echo   1. 请妥善保管 %KEYSTORE_FILE% 文件并备份
echo   2. 该文件已在 .gitignore 中，不会被提交到 git
echo   3. 现在可以运行 GitHub Actions 构建签名的 APK/AAB
echo   4. 临时文件 keystore_base64.txt 已生成，可以手动删除
echo.

REM 清理临时文件（可选）
if exist keystore_base64.txt (
    echo 是否删除临时 Base64 文件? (Y/N)
    set /p DELETE_TEMP="> "
    if /i "!DELETE_TEMP!"=="Y" (
        del keystore_base64.txt
        echo   ✓ 已删除 keystore_base64.txt
    )
)

echo.
pause

endlocal
