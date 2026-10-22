# EMCL — Eternal Minecraft Launcher

> 当所有启动器失效时，我将作为备选——游戏不该停止运营，游戏是人类精神上的支撑柱。

EMCL 是 fork 自 [HMCL（Hello Minecraft! Launcher）](https://github.com/HMCL-dev/HMCL) 的开源 Minecraft 启动器分支，使用同一套 **Java 17 + JavaFX + Gradle** 工程，默认简体中文、绿色主题，并保留 HMCL 的下载、加载器安装与微软正版登录能力。

- **版权**：© 2026 ETC Team
- **上游署名**：`huanghongxun` / HMCL upstream（保留原作者英文署名）
- **协议**：[GPL-3.0](LICENSE)（继承上游，全部源码开源，不得改为 MIT 或闭源）
- **作者**：龙腾os（ETC Team）

## 下载

发布在本仓库的 `continuous` Release，持续更新：

| 产物 | 说明 |
| --- | --- |
| `EMCL.jar` | 跨平台可执行 fat jar（含全部依赖）。需要 Java 17+，`java -jar EMCL.jar` 运行，Linux / macOS / Windows 通用 |
| `EMCL-windows-portable.zip` | Windows 便携版，内含 `jpackage` 生成的 `EMCL.exe` 与自带精简 JRE，双击即用，**无需安装 Java**，体积约 80–120 MB |
| `EMCLJavaSetup.exe`（可选） | 只有 `EMCL.jar` 而没有 Java 时使用：下载并静默安装 Temurin JRE 17，完成后自删 |

官网：<https://etqwfd.github.io/EMCL/> · 使用手册：<https://etqwfd.github.io/EMCL/docs.html>

## 特性

- **真实可用的版本下载**：Mojang 官方源 + BMCLAPI 镜像双源，piston-meta / piston-data / launcher.mojang / launchermeta 自动追加镜像域名，Mod 走 mcimirror；多镜像、多线程、断点续传、失败源自动禁用 / 切换，对 403/429 容错；正式 / 预览 / 远古 / 愚人节版本分组。
- **微软正版登录**：保留 HMCL 完整 OAuth 流程，授权显示名为 EMCL，离线与正版入口并存。客户端 ID 通过环境变量 `EMCL_MS_CLIENT_ID`（CI 用 `secrets.CLIENT_ID`）或设置项注入，不硬编码他人凭据。
- **公平启动保护（静态前置检查）**：启动前检查实例 / 全局 `mods`、`coremods`、版本 Classpath 中的作弊客户端与作弊 Mod（文件名 / Mod 元数据 / 内部包名特征），拦截 `-javaagent`、`-agentpath`、`-agentlib`、关闭校验等 JVM 注入参数，并检查 `JAVA_TOOL_OPTIONS`、`_JAVA_OPTIONS`、`JDK_JAVA_OPTIONS`。命中即不启动，原因写入 `EMCL/FairPlayBlock.txt`。诚实边界：不承诺拦截驱动级、系统级或未知混淆注入。
- **自动更新**：启动后台比对本仓库 `main` 最新提交 sha 与本地注入 hash，三态：立即更新（jar 版下载替换重启自删；EXE 版提示重新下载便携包整体替换）、忽略此版本、以后再说。
- **公告系统**：启动拉取官网 `announce.json`，启用且未读时弹一次。
- **绿色主题 + EMCL 图标**，关于页含「赞助 ETC」（跳转 B 站）与「EMCL by ETC Team」开发者卡片，保留 `huanghongxun` 上游署名与镜像源鸣谢。

## 目录约定

- 启动器自身配置目录统一为 **`EMCL`**（如 `~/.local/share/EMCL`，便携目录下 `.EMCL`，含 `logs` / `config` / `assets-cache` 等），不会在用户目录生成 HMCL 文件夹。
- 游戏侧 `.minecraft` 以及实例内 `hmcl.json` 等整合包元数据路径保持 HMCL 上游约定，以兼容现有整合包。
- Java 包名 `org.jackhuang.hmcl.*` 保持不变（兼容整合包元数据）。

## 从源码构建

需要 JDK 17（含 `jpackage`）。

```bash
# 跨平台 fat jar
./gradlew :HMCL:shadowJar
# 产物：HMCL/build/libs/HMCL-<version>.jar，重命名为 EMCL.jar 发行
java -jar EMCL.jar
```

Windows 便携版（app-image，非安装器）：

```bat
jpackage --type app-image --name EMCL --dest build\image --input build\app ^
  --main-jar EMCL.jar --main-class org.jackhuang.hmcl.Main ^
  --icon EMCL.ico --java-options "-Demcl.packaged=true" [更多 --add-opens ...]
```

CI（GitHub Actions，Windows runner）会自动：shadowJar → `EMCL.jar`、jpackage → `EMCL-windows-portable.zip`、编译可选 `EMCLJavaSetup.exe`，发布 / 更新 `continuous` Release，并部署 GitHub Pages。

### 关于原生 exe 的诚实说明

JavaFX 程序无法用 GraalVM native-image 稳定编译为原生单文件。**`EMCL.exe` 是 `jpackage` 打包、自带精简 JRE 的 app-image**（约 80–120 MB），不是把 jar 改名伪装的单文件 exe，也不是原生 Linux ELF。Linux / macOS 用户直接 `java -jar EMCL.jar` 即可，这是受支持方式，不需要 Wine。

## 责任与运营声明

EMCL **不运营 Minecraft，也不运营任何多人服务器**。未成年人充值、服务器封禁、道具纠纷、支付退款、服务器管理规则等事务，请联系对应服主 / 服务器管理组 / 支付渠道 / Microsoft·Mojang 官方支持，均不属于启动器可处理范围。

本软件按 GPL-3.0 开源，按「现状」提供；部分代码属于原作者 `huanghongxun`，作者与开发者不对使用后果承担法律责任，ETC Team 亦不承担责任。

- 反馈：<mailto:2416444244@qq.com>
- QQ 群：见官网「加入 QQ 群」
- Issues：<https://github.com/ETQWFD/EMCL/issues>
