# Rift Timer

英雄联盟召唤师技能计时器，支持 Windows、Android 和网页。三个客户端可连接同一个对局服务，按对局码共享阵容、急速和技能截止时间。

## 下载 2.2.0

- [Windows x64 压缩包](downloads/RiftTimer-Windows-x64.zip)。完整解压，运行 RiftTimer.exe。
- [Android APK](downloads/RiftTimer-Android.apk)。支持 Android 8.0 及以上。
- 网页可直接从 website 部署。麦克风需要 HTTPS 或 localhost。

Windows 悬浮窗中左击技能开始计时，右击扣秒或重置，点击位置名配置急速。右上角锁定后，点击同一把锁即可解锁。主窗口「说明」列出各项操作。

## 自建多人对局

1. 安装 Python 3，在可写的数据目录运行 `python /path/to/website/service/rooms.py`。服务监听 127.0.0.1:19437，SQLite 数据保存在工作目录，也可以用 ROOM_DATABASE 指定。
2. 使用 HTTPS 将 website 的网页与 assets 部署到 `/rift-timer/`，并把同源 POST 路径 `/rift-timer/api` 原样反向代理到服务。仅部署静态网页即可使用单人计时。
3. 在客户端的「对局码」中填写自己的完整 HTTPS API 地址，然后输入 6 至 32 位数字创建或加入对局。公开安装包需要自行配置服务地址。

本地预览可运行 `node website/server.cjs` 后访问 http://localhost:4317。客户端构建方法见 [native/README.md](native/README.md)。