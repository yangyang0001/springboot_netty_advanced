@echo off
rem 编译项目并把运行时依赖复制到 target\lib, 供 netty-server.cmd / netty-client.cmd 使用。
setlocal
cd /d "%~dp0.."
echo [build] mvn compile + copy dependencies ...
call mvn -q compile dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib
if errorlevel 1 (
    echo [build] failed
    exit /b 1
)
echo [build] done
