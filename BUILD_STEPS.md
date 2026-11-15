# AdSkipper 出包清单（照抄即可）

> 目标：把本目录推到 GitHub，由仓库自带的 workflow 在云端编出 `app-debug.apk`。
> **本机不需要安装 JDK 17 / Android SDK 35 / Gradle 8.9**——现有那套旧环境既不用补，也不用碰。
> 当前状态：目录不是 git 仓库、未关联远程；`.github/workflows/build.yml` 已配好且可用；源码完整；全工程约 60 KB。
> 预计耗时：首次约 5–10 分钟（云端要下载 platform 35 / build-tools 35 / Gradle 8.9 及 AGP、Kotlin 插件）。

---

## 0. 前置确认（已排查，不用重复做）

| 检查项 | 结果 |
| --- | --- |
| `.github/workflows/build.yml` 是否存在 | 存在，配置完整（JDK 17 → SDK 35 → Gradle 8.9 → `gradle assembleDebug` → 上传产物） |
| `.gitignore` 会不会漏掉 workflow | 不会。它只忽略 `.gradle/`、`build/`、`local.properties`、`gradle-wrapper.jar` 等，`.github` 不受影响 |
| 工程能否被 AGP 8.7.3 接受 | `namespace` 已声明；Manifest 中 activity 与 service 均有 `android:exported`，无此项报错风险 |
| 本地 `gradlew.bat` 缺失是否影响云端 | 不影响。workflow 特意用全局 `gradle` 而非 wrapper，绕开了缺失的 jar 和脚本 |
| 触发条件 | `push` 到 main / master 自动触发，或在 Actions 里手动 `Run workflow`；纯 `.md` 变更被 `paths-ignore` 忽略 |

---

## 1. 在 GitHub 建一个空仓库

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
| 红在 `sdkmanager` 或下载超时 | 云端偶发网络问题，直接 Re-run job 重试一次 |
| 红在 `gradle assembleDebug` | 展开日志看首条 `FAILURE` / `What went wrong`，通常是拉 AGP、Kotlin 插件超时，重跑可解；仍失败就把日志贴出来排查 |
| 本地执行 `gradlew.bat` 报 `GradleWrapperMain` | 正常现象，工程本来就没有 wrapper jar。**不要在本地尝试构建**，交给云端 |

---

## 一句话总结

本机那套 JDK 12 + SDK 28/29 的环境与工程要求的 JDK 17 + compileSdk 35 差着代际，补齐不划算；推一次代码让 GitHub Actions 出包，是这条路里成本最低、最不容易出错的做法。
