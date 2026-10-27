# Hdtv

Android TV 应用，顶部按分类（group-title）展示海报卡片，直播和点播内容按分类自动合并显示。

## 内容源

- 直播：`iptv12k` M3U 源
- 点播：`bit.ly/jaren22`

两个源在 `PlaylistParser.kt` 里拉取并解析，按 M3U 里的 `group-title` 分组；如果两个源里出现同名分类，会自动合并显示在同一行里。

## 在 GitHub 上构建 APK（不用装 Android Studio）

1. 把这个项目的所有文件推送到你的仓库 `alexong983-png/New-hdtv`（直接覆盖/上传即可）。
2. 打开仓库的 **Actions** 标签页，能看到 `Build APK` 这个工作流。
3. 如果没有自动触发，点击 **Run workflow** 手动触发一次。
4. 等几分钟构建完成后，在该次运行结果下方的 **Artifacts** 里下载 `Hdtv-debug-apk`，解压后就是 `app-debug.apk`。
5. 把 APK 传到电视盒上安装即可。

## 后续可以改的地方

- 应用图标/开屏 banner 目前是占位的蓝色方块（`app_icon.xml` / `app_banner.xml`），可以换成你之前给的 logo 图。
- 如果点播源 `bit.ly/jaren22` 实际返回的不是标准 M3U 格式（比如是网页或 JSON），解析逻辑需要相应调整，告诉我实际返回内容我再改。
- 目前播放失败/源连不上只会弹一个 Toast 提示，没有重试机制，后面可以加。
