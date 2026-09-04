# AutoDark

通过 MQTT 远程控制 Android 手机屏幕（黑屏 / 最低亮度）的工具，常用于钉钉等打卡场景：手机常亮挂机、收到远程指令后调整屏幕，并监听打卡通知、通过邮件提醒结果。

> **本项目基于 [DailyTask](https://github.com/AndroidCoderPeng/DailyTask) 的 1.5.6 分支修改而来，重点学习并验证 MQTT over TLS 与 mTLS（双向认证）流程。**

## 版本

v3.0.0

[查看完整版本日志](CHANGELOG.md)

## 功能

- MQTT 远程控制屏幕亮度（最低亮度 / 防误触黑屏）
- 三种 MQTT 连接方式：无加密 / 单向 TLS / 双向 TLS（mTLS）
- 通知监听（钉钉等）并发送打卡结果邮件
- 前台服务保活、悬浮窗、低电量提醒

## 构建环境

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 11（推荐）或 17 | Android Gradle Plugin 7.4.x 要求 JDK 11+ |
| Gradle | 7.6.6 | 使用官方 distributionUrl，由 wrapper 自动下载 |
| Android Gradle Plugin | 7.4.2 | |
| Kotlin | 1.8.0 | |
| compileSdk | 33 | |
| targetSdk | 33 | |
| minSdk | 24（Android 7.0） | |

构建命令：

```bash
./gradlew assembleDebug     # 调试包
./gradlew assembleRelease   # 发布包（需配置签名，见下）
```

发布签名：在项目根目录创建 `keystore.properties`（该文件已加入 `.gitignore`，不会提交）：

```properties
KEYSTORE_FILE=你的签名文件.jks
KEYSTORE_PASSWORD=你的密码
KEY_ALIAS=你的别名
KEY_PASSWORD=你的密码
```

## 运行环境

- Android 7.0（API 24）及以上
- 所需权限：悬浮窗、通知使用权、电池优化白名单、自启动

## 三种 MQTT 连接方式

| 模式 | 端口 | 依赖 | 风险 |
|---|---|---|---|
| 无加密（默认） | 1883 | 云服务器 + MQTT broker | 账号密码明文传输（不安全） |
| 单向 TLS | 8884 | + 域名 + 公网证书 | 校验服务器身份（较安全） |
| 双向 TLS（mTLS） | 8883 | + 域名 + 自建 CA + 客户端证书 | 双向校验（最安全） |

端口默认值可在 `app/src/main/java/com/autodark/utils/TlsConfig.kt` 中调整。

## 使用方法

1. 首次启动进入设置页，按提示授权（悬浮窗、通知监听、电池优化白名单、自启动）。
2. 点击「服务器地址」填写 broker 地址（IP 或域名）。
3. 点击「连接方式」选择加密方式（默认无加密）。
4. 按需配置「接收邮箱」「发送邮箱」「MQTT账号」。
5. 手机连接充电器并保持屏幕常亮。

## 致谢

本项目基于 [DailyTask](https://github.com/AndroidCoderPeng/DailyTask) 的 **1.5.6 分支**修改而来，原始版权归原作者所有。项目重点参考并验证了其 **MQTT over TLS 与 mTLS（双向认证）** 流程实现。
