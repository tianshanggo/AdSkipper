# AdSkipper 出包清单（照抄即可）

> 目标：把本目录推到 GitHub，由仓库自带的 workflow 在云端编出 `app-debug.apk`。
> **本机不需要安装 JDK 17 / Android SDK 35 / Gradle 8.9**——现有那套旧环境既不用补，也不用碰。
> 当前状态：`.github/workflows/build.yml` 已配好，并在首次云端构建失败后修复（移除了 `setup-android@v3`）；源码完整；全工程约 60 KB。
> **如果你已经推送过一次并看到构建失败，直接跳到第 7 节。**
> 预计耗时：首次约 5–10 分钟（云端要下载 platform 35 / build-tools 35 / Gradle 8.9 及 AGP、Kotlin 插件）。

---

## 0. 前置确认（已排查，不用重复做）

| 检查项 | 结果 |
| --- | --- |
| `.github/workflows/build.yml` 是否存在 | 存在，配置完整（JDK 17 → SDK 35 → Gradle 8.9 → `gradle assembleDebug` → 上传产物） |
| `.gitignore` 会不会漏掉 workflow | 不会。它只忽略 `.gradle/`、`build/`、`local.properties`、`gradle-wrapper.jar` 等，`.github` 不受影响 |
| 工程能否被 AGP 8.7.3 接受 | `namespace` 已声明；Manifest 中 activity 与 service 均有 `android:exported`，无此项报错风险 |
| 本地 `gradlew.bat` 缺失是否影响云端 | 不影响。workflow 特意用全局 `gradle` 而非 wrapper，绕开了缺失的 jar 和脚本 |
| 为什么不用 `setup-android@v3` | 该 action 内部会执行 `sdkmanager tools`，旧版 `tools` 包已从 Google 仓库下架，在新版 runner 上必报 `Failed to find package 'tools'` 并 exit 1。runner 预装的 SDK 已够用，见第 7 节 |
| 触发条件 | `push` 到 main / master 自动触发，或在 Actions 里手动 `Run workflow`；纯 `.md` 变更被 `paths-ignore` 忽略 |

---

## 1. 在 GitHub 建一个空仓库

> 已经建好仓库的跳过这一节。

1. 打开 https://github.com/new ，仓库名建议 `AdSkipper`。
2. **不要勾选** Add a README / .gitignore / license——勾了会让首次 push 被拒。
3. 可见性按需选（代码本身无敏感信息，但建议先私有）。
4. 记下地址：`https://github.com/<你的用户名>/AdSkipper.git`

---

## 2. 确认本机 Git 可用（PowerShell）

```powershell
git --version
```

- 能输出版本号 → 跳到步骤 3。
- 提示无法识别 → 安装：`winget install --id Git.Git -e --source winget`，或到 https://git-scm.com/download/win 下载安装，装完**重开 PowerShell**。

首次使用 Git 需要身份（换成你自己的，邮箱建议与 GitHub 账号一致）：

```powershell
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"
```

推送认证：GitHub 已不支持账号密码。Git for Windows 自带凭据管理器，首次 push 会弹浏览器让你登录，走这条最省事；若已装 GitHub CLI，也可先执行 `gh auth login`。

---

## 3. 初始化并推送（逐行执行）

> 已经推过一次的跳过这一节，直接看第 7 节。

```powershell
cd 'C:\Users\admin\Desktop\AdSkipper\AdSkipper'
git init -b main
git add -A
git status
git commit -m "Initial commit: AdSkipper source"
git remote add origin https://github.com/<你的用户名>/AdSkipper.git
git push -u origin main
```

**执行 `git status` 时务必确认这一行在列表里：**

```
.github/workflows/build.yml
```

它是最容易漏、也最致命的一步——漏了 workflow 就不会上传，推上去什么都不会发生。也可以用这条命令复核：

```powershell
git ls-files | Select-String '\.github'
```

备注：
- `git init -b main` 需要 Git 2.28+。老版本改用 `git init` 后再 `git branch -M main`。
- 如果不想把这份清单推到仓库，push 前删掉 `BUILD_STEPS.md` 即可；它也不会触发构建（workflow 忽略了 md 变更）。

---

## 4. 取 APK

1. push 成功后，打开仓库 → **Actions**，应能看到「构建 APK」正在运行。
2. 等状态变绿，进入该次运行 → 页面底部 **Artifacts** → 下载 `AdSkipper-debug`（zip，保留 30 天）。
3. 解压得到 `app-debug.apk`。debug 包自带签名，可直接安装。
4. 若 Actions 里没出现运行记录：先按步骤 3 复核 workflow 是否已提交；确认无误后到 Actions → 「构建 APK」→ **Run workflow** 手动跑一次。

---

## 5. 装到手机（可选）

方式 A：把 `app-debug.apk` 传到手机，允许「安装未知来源应用」后点开安装。

方式 B：用本机已有的 adb（SDK 里的 `platform-tools` 是可用的）：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "C:\Users\admin\Downloads\app-debug.apk"
```

装完记得按 README 第三节开启无障碍服务；Android 13 以上若开关是灰的，需要先在「应用信息 → 允许受限设置」里放行。

---

## 6. 报错对照

| 现象 | 处理 |
| --- | --- |
| `git` 无法识别 | 见步骤 2，装 Git for Windows 并重开终端 |
| `src refspec main does not match any` | 没有成功 commit，或分支不叫 main：`git branch -M main` 后重推 |
| `! [rejected] main -> main (fetch first)` | 建仓库时勾了 README。执行 `git pull --rebase origin main` 再 push，或删掉重建空仓库 |
| 认证失败 / `Repository not found` | 检查仓库地址与权限；GitHub 不接受密码，改用浏览器凭据登录或 Personal Access Token |
| push 成功但 Actions 无运行 | `.github` 没提交，见步骤 3 的复核命令；或只改了 md 被 `paths-ignore` 忽略，手动 Run workflow |
| 红在「安装 Android SDK」且日志有 `Failed to find package 'tools'` | `setup-android@v3` 的已知问题，workflow 里已移除该 action。**Re-run 没用**（会用旧 commit 的 workflow），必须重新 commit + push，见第 7 节 |
| `sdkmanager` 下载组件超时 / Computing updates 卡住 | 云端偶发网络问题，Re-run job 重试一次 |
| 红在 `gradle assembleDebug` | 展开日志看首条 `FAILURE` / `What went wrong`，通常是拉 AGP、Kotlin 插件超时，重跑可解；仍失败就把日志贴出来排查 |
| 本地执行 `gradlew.bat` 报 `GradleWrapperMain` | 正常现象，工程本来就没有 wrapper jar。**不要在本地尝试构建**，交给云端 |

---

## 7. 已经推过一次：应用这次修复

`build.yml` 已在本机修好。改动是：**删掉 `android-actions/setup-android@v3`，改用 runner 预装的 SDK 与 `sdkmanager`**（并显式设置 `ANDROID_HOME`）。ubuntu-latest 镜像本来就带 Android SDK，`tools` 包的下架只影响那个多余的 action。

注意 **Actions 里的 Re-run job 会沿用那次提交里的旧 workflow 文件，读不到修复**，所以必须重新提交并推送：

```powershell
cd 'C:\Users\admin\Desktop\AdSkipper\AdSkipper'
git add .github/workflows/build.yml
git commit -m "CI: 移除 setup-android@v3，改用 runner 预装的 sdkmanager"
git push
```

如果当时是网页上传文件、本地没有 `.git`：直接在 GitHub 仓库页打开 `.github/workflows/build.yml`，点铅笔编辑，把本机这份文件的内容整段粘进去提交即可。

因为 `build.yml` 不是 md 文件，这次 push 会自动触发新的构建；也可以到 Actions 手动 Run workflow。等约 5–10 分钟，在 Artifacts 下载 `AdSkipper-debug`。

---

## 8. 装到手机后：提示「由受限设置控制」/「系统已拒绝授予权限」

这不是 App 的问题，是 **Android 13+ 的「受限设置（Restricted Settings）」**：系统不允许手动安装（旁加载）的 APK 直接获得无障碍服务等敏感权限。弹窗里没有解，别在这里找开关；也**不要反复重装 APK**，重装会重置授权状态、让限制重新生效。

### 方法 A：系统里放行（先试，最省事）

1. 设置 → 应用管理 / 应用 → 找到「开屏跳过」
2. 右上角「⋮」→「允许受限设置」（部分 ROM 写作「允许受限制的设置」）
3. 回到 设置 → 无障碍 → 已安装的应用 / 已下载的服务 → 打开「开屏跳过」

找不到入口时，在设置顶部搜索框直接搜「受限设置」；部分 ROM 放在 隐私保护 → 特殊权限设置 → 无障碍。不同品牌（HyperOS / ColorOS / OriginOS / HarmonyOS）路径差异较大。

### 方法 B：用本机 adb 解除限制（最可靠）

本机已有 adb。手机开启 USB 调试并连电脑后执行：

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb shell appops set com.adskipper.app ACCESS_RESTRICTED_SETTINGS allow
```

执行完回到系统设置开启无障碍服务。若报 `Unknown operation string`，说明 ROM 改过这套机制，退回方法 A。

小米 / HyperOS 注意：改这类设置通常还需要在开发者选项里额外打开「USB 调试（安全设置）」（需登录账号、插 SIM 卡），否则 adb 会因权限不足失败。

### 方法 C：adb 直接开启服务（可选，会覆盖已有服务）

```powershell
& $adb shell settings get secure enabled_accessibility_services
& $adb shell settings put secure enabled_accessibility_services com.adskipper.app/.SkipAdService
& $adb shell settings put secure accessibility_enabled 1
```

`enabled_accessibility_services` 是冒号分隔的列表，直接 put 会**覆盖其他已开启的无障碍服务**。先按第一行读出原值，把 `com.adskipper.app/.SkipAdService` 用冒号拼到后面再写回，更安全。

服务配置本身没问题：`accessibility_service_config.xml` 已声明 `canRetrieveWindowContent="true"`、`canPerformGestures="true"` 与 `flagRetrieveInteractiveWindows`，授权一放行即可正常读控件树与执行点击。

---

## 一句话总结

本机那套 JDK 12 + SDK 28/29 的环境与工程要求的 JDK 17 + compileSdk 35 差着代际，补齐不划算；推一次代码让 GitHub Actions 出包，是这条路里成本最低的做法。云端唯一踩到的坑（`setup-android@v3` 依赖已下架的 `tools` 包）已经修掉，重新推一次即可。
