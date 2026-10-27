@echo off
rem 启动服务端 (HTTP 8080, TCP 9000), 启动前会先编译。
rem 用法: scripts\netty-server.cmd [--server.port=18080 --netty.server.port=19000]
setlocal
chcp 65001 >nul
cd /d "%~dp0.."
call "%~dp0netty-build.cmd" || exit /b 1
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "target\classes;target\lib\*" deepblue.NettyAdvancedApplication %*
