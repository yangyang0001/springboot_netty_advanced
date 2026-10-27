@echo off
rem 启动一个聊天客户端, 每个终端启动一个, 用户名不同即可互相发 JSON。
rem 用法: scripts\netty-client.cmd <userId> [port]
rem 例如: 终端 A: scripts\netty-client.cmd tom
rem       终端 B: scripts\netty-client.cmd jerry
setlocal
chcp 65001 >nul
cd /d "%~dp0.."
if "%~1"=="" (
    echo usage: scripts\netty-client.cmd ^<userId^> [port]
    exit /b 1
)
rem 没有编译产物时先编译; 已经编译过就直接启动, 避免多个终端同时跑 mvn 互相干扰。
if not exist "target\lib" call "%~dp0netty-build.cmd" || exit /b 1
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "target\classes;target\lib\*" deepblue.inaction_02_springboot.client.MineNettyClient %*
