# 学习通助手（LSPosed 模块）

只对 `com.chaoxing.mobile`（学习通）生效的本地模块。安装包已按你提供的
`学习通_7.0.4.apk` 的包名做过限定，不会向系统设置、其他应用或学习通的数据目录写入内容。

## 已实现

- WebView 内自动播放、静音
- 播放速度 1.0x–4.0x（默认 2.0x）
- 视频结束后自动点“下一节 / 下一章 / 下一讲”
- 自动点“继续学习 / 继续观看 / 开始学习”
- 可选：自动处理弹题（默认关闭，选第一个选项并提交，可能答错）
- 可选：直接跳到视频结尾（默认关闭，容易被平台判定为未完成）
- 可选：自动关“知道了 / 关闭”提示框（默认关闭）
- 子框架注入：课程页把播放器放在 iframe 里时也能控制
- 只在学习通进程安装 hook；其他应用即使被 LSPosed 选中也不会执行

## 安装

1. 安装 `CxHelper.apk`。
2. 打开 LSPosed，启用“学习通助手”。
3. 作用域只勾选 **学习通**（`com.chaoxing.mobile`），不要勾系统框架或其他应用。
4. 强行停止学习通，再重新打开。
5. 桌面打开“学习通助手”可以改速度、静音、连播等选项；改完重启学习通生效。

## 使用边界

- 只控制 HTML5 `<video>` 播放器。如果某门课是原生播放器、直播、文档或考试页，模块不会替它完成。
- 倍速、跳结尾、自动答题都可能触发平台的风控或让任务点判为无效。遇到提示就把速度调回 1.0x，并关闭“跳到视频结尾”。
- 模块不修改学习通的登录、账号、成绩或服务器数据，只做页面内的播放/点击自动化。

## 文件

```
app/src/main/java/io/github/yg/cxhelper/HookEntry.java   入口与 WebView hook
app/src/main/java/io/github/yg/cxhelper/Config.java       参数读取
app/src/main/java/io/github/yg/cxhelper/SettingsActivity.java  设置页
app/src/main/assets/inject.js                            注入脚本
app/src/main/assets/xposed_init                          模块入口声明
app/src/main/res/values/arrays.xml                        LSPosed 作用域
build.sh                                                 本机构建脚本
```

## 重新构建

本机（Ubuntu arm64 / proot）已装好 JDK 17、`aapt`、`zipalign`，并把 Android 34 的
`android.jar`、`d8.jar`、`apksigner.jar` 放在 `/tmp/sdk/x`。执行：

```bash
bash build.sh
```

产物为 `CxHelper.apk`。如果换机器，改 `build.sh` 顶部的 `SDK`、`ANDROID_JAR`、
`D8_JAR`、`APKSIGNER_JAR` 路径即可。

## 备注

LSPosed 的“隐藏应用列表 / 隐蔽模块”按你本机环境自行决定是否开启；学习通带有
`RiskStub` 风控壳，是否检测注入取决于它的策略，模块本身不包含对抗检测的代码。
## 无效果时怎么排查（v1.1 诊断版）

1. 安装 `学习通助手-v1.1-诊断版.apk`（覆盖安装即可）。
2. LSPosed 里确认模块已启用、作用域只勾学习通，然后强行停止学习通再打开。
3. 打开学习通时观察有没有 Toast：`学习通助手已加载（诊断版）`。
   - 没有 Toast：模块没有被 LSPosed 加载，先检查 LSPosed 的启用状态、作用域和日志。
   - 有 Toast：模块已进入学习通进程，继续第 4 步。
4. 打开一门课的视频页，等 10 秒。页面右上角应出现 `助手:TOP`；如果播放器在 iframe 里，
   iframe 内还会出现 `助手:IFRAME`。
5. 把日志发回：`/sdcard/Android/data/com.chaoxing.mobile/files/cxhelper.log`
   （只记录进程、页面路径、WebView 类型和注入结果，不记录 query 参数）。

三种现象对应的问题：
- 有 Toast、没有 `助手:TOP`：页面不是系统 WebView，或脚本被 CSP/加载方式挡住。
- 只有 `助手:TOP`、视频仍不动：播放器在跨域 iframe 或原生播放器里，需要换 hook 点。
- `助手:TOP`/`IFRAME` 都有、视频仍不动：播放器不是 HTML5 `<video>`，或平台拦截了自动播放。

## v1.3 修复

v1.0–v1.2 在 LSPosed 的 zygote 阶段加载模块类时，静态字段
`new Handler(Looper.getMainLooper())` 先于 Android 主循环初始化，触发
`ExceptionInInitializerError`，所以模块完全没有进入学习通进程。

v1.3 已移除该静态 Handler，改为在 `WebView.post()` 的 UI 线程里延迟注入；
打开学习通应能看到 `学习通助手已加载 pid=...` 的 Toast。
