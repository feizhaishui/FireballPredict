# FireballPredict（烈焰弹落点预测）

一个基于 Minecraft Forge 1.8.9 的客户端模组，用于预测烈焰弹（火球）的落点，
提供颜色提示、预计到达时间（ETA）与爆炸范围警告，支持按键开关与中英文界面。

## 功能特性

- 实时预测烈焰弹落点，并高亮标记目标方块
- 标记颜色随火球到落点的距离渐变：近红远绿
- 显示预计到达时间（ETA）
- 玩家处于爆炸范围（5×5×5）内时发出警告
- 按 `R` 键切换预测功能开关（可在游戏设置中修改按键）
- 内置简体中文（zh_CN）与英文（en_US）语言文件

## 环境要求

- **JDK 8**：Forge 1.8.9 / ForgeGradle 2.1 必须使用 JDK 8
- **Gradle**：无需单独安装，项目自带 `gradlew`（Windows）/ `gradlew`（Linux/macOS）

## 快速开始（Windows）

双击运行 `quickstart.bat` 即可：脚本会自动定位 JDK 8（优先读取本地
`gradle.properties` 中的 `org.gradle.java.home`，再依次检查 `JAVA_HOME`、
`PATH` 和常见安装目录），首次运行会自动执行 Forge 工作区配置，然后启动
Minecraft 客户端。

其他用法：

```bash
quickstart.bat setup   # 仅执行 Forge 工作区配置
quickstart.bat build   # 构建模组 jar
quickstart.bat check   # 检查 JDK 8 并显示版本信息
```

## 源码安装说明（Forge 模组开发）

本节内容整理自原 README.txt。

本代码遵循 Minecraft Forge 的安装方法：构建时会向原版 MCP 源码应用一些
小补丁，让模组能够访问开发所需的 Minecraft 数据和函数。需要注意，这些
补丁是针对"未重命名"的 MCP 源码（即 srgnames）构建的，因此不能直接
对照普通代码阅读。

### 独立源码安装步骤

1. 打开命令行，进入项目所在目录（即解压/克隆得到的文件夹）。
2. 执行工作区配置：

   Windows：`gradlew setupDecompWorkspace`
   Linux/Mac：`./gradlew setupDecompWorkspace`

3. 配置完成后，按使用的 IDE 选择下一步：

   - Eclipse：执行 `gradlew eclipse`（Linux/Mac 为 `./gradlew eclipse`）
   - IntelliJ IDEA：将 `build.gradle` 作为 Gradle 项目导入。
     （原说明还要求关闭 IDEA 后运行 `gradlew genIntellijRuns`，但本项目使用
     的 ForgeGradle 2.1 不提供该任务；直接以 Gradle 项目导入即可，详见下文
     "在 IntelliJ IDEA 中开发"。）

4. Eclipse 用户最后需将工作区切换到 `/eclipse/` 目录；IDEA 用户导入后
   会自动加载项目。

### 排错与重置

- 如果 IDE 中缺少库或遇到问题，执行 `gradlew --refresh-dependencies`
  刷新本地缓存。
- 执行 `gradlew clean` 可重置构建环境（不影响你的代码），然后重新执行
  上述安装步骤。

### 其他工作区类型

- `setupDevWorkspace`：应用补丁、反混淆并收集运行所需资源，但**不生成**
  可读的 Minecraft 源码。
- `setupCIWorkspace`：与 Dev 相同，但**不下载资源**，速度最快，适合构建服务器。

### 关于反编译源码

使用 Decomp 工作区时，Minecraft 源码**不会以可编辑方式**加入工作区，而是
被当作普通库处理。源码仅供查阅与研究，通常在 IDE 的 "referenced libraries"
（引用的库）中查看。

### Forge 源码安装

MinecraftForge 随本代码一起分发，并在 Forge 安装过程中自动完成安装，
无需额外操作。

### 参考资源

- LexManos 的安装视频：https://www.youtube.com/watch?v=8VEdtQLuLO0
- Forge 官方论坛（更多详情与更新）：
  http://www.minecraftforge.net/forum/index.php/topic,14048.0.html

## 在 IntelliJ IDEA 中开发

1. File → Open，选择本项目的 `build.gradle`，以 Gradle 项目方式导入。
2. File → Project Structure → SDKs：添加 JDK 8 并设为 Project SDK
   （`gradle.properties` 会保证 Gradle 本身也使用 JDK 8）。
3. 等待 Gradle 同步完成，即可正常编码；Minecraft / Forge 源码可在
   "referenced libraries" 中跳转查看。
4. 运行游戏：Gradle 面板执行 `runClient`，或直接运行 `quickstart.bat`。

## 打包

```bash
gradlew build
```

产物在 `build/libs/` 下（模组 jar：`fireball_predict-2.3.jar`）。

## 目录结构

```
src/main/java/com/naix/predict/
├── FireballPredict.java     # 主模组类：初始化、按键绑定、预测与警告逻辑
└── PredictionRenderer.java  # 客户端渲染：落点高亮与颜色渐变
src/main/resources/
├── mcmod.info               # 模组元数据
└── assets/fireball_predict/lang/
    ├── en_US.lang           # 英文文本
    └── zh_CN.lang           # 简体中文文本
```

## 常见问题

- **资源下载报 400**：Mojang 旧版资源 CDN 已下线 1.8.9 部分资源，运行游戏时
  贴图/音效可能缺失，不影响编译与开发。
- **mappings 警告**：`stable_20` 提示为 MC 1.8.8 设计，属正常现象，可忽略。
- **找不到 JDK 8**：设置 `JAVA_HOME`，或复制 `gradle.properties.example`
  为本地 `gradle.properties` 并填写自己的 JDK 8 路径（该文件已被
  .gitignore 排除，不会提交）。

## 许可

本项目基于 Minecraft Forge 构建，相关许可信息见仓库内的
`LICENSE-fml.txt` 与 `MinecraftForge-License.txt`。
