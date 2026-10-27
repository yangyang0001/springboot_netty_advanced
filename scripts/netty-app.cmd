@echo off
rem 启动桌面聊天 App (Swing + Netty TCP 客户端), 可多开几个窗口用不同用户名登录。
rem 用法: scripts\netty-app.cmd [userId] [host] [port], 默认 127.0.0.1 9100 (inaction_03 的 AppNettyServer)
rem       不带参数时弹出登录框; 例如: scripts\netty-app.cmd tom
setlocal
chcp 65001 >nul
cd /d "%~dp0.."
rem 没有编译产物时先编译; 已经编译过就直接启动, 避免多个窗口同时跑 mvn 互相干扰。
if not exist "target\lib" call "%~dp0netty-build.cmd" || exit /b 1
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "target\classes;target\lib\*" deepblue.inaction_03_app.ui.MineNettyApp %*
