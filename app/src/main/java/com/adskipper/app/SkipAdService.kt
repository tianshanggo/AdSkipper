package com.adskipper.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 核心服务：监听窗口变化，查找并点击「跳过」按钮。
 *
 * 针对「有时漏跳」做的三处关键优化：
 * 1. 扫描所有窗口（而不只是 rootInActiveWindow）——开屏广告常是独立 Dialog / Popup 窗口；
 * 2. 无障碍配置加入 flagIncludeNotImportantViews——大量广告按钮被标记为「无障碍不重要」，
 *    默认根本不会出现在控件树里；
 * 3. 事件触发后追加一串定时重试扫描（0/120/280/…/8000ms）——覆盖按钮延迟出现、
 *    content-changed 事件风暴结束后才渲染的广告。
 *
 * 防误触闸门：白名单排除、同 App 短冷却、短文本 + 敏感词过滤、可见性与边界检查。
 */
class SkipAdService : AccessibilityService() {

    companion object {
        private const val TAG = "AdSkipper"

        /** 内容变化事件的扫描限流，避免事件风暴 */
        private const val SCAN_INTERVAL_MS = 200L

        /** 观察窗口：普通模式 9 秒，兼容模式 12 秒 */
        private const val ACTIVE_WINDOW_MS = 9_000L
        private const val ACTIVE_WINDOW_AGGRESSIVE_MS = 12_000L

        /** 同 App 成功点击后的冷却，避免连点 */
        private const val SAME_APP_COOLDOWN_MS = 1_200L

        /** 定时重试序列：相对会话起点的时间点（毫秒） */
        private val RETRY_DELAYS = longArrayOf(
            0L, 120L, 280L, 500L, 800L, 1_200L, 1_700L,
            2_300L, 3_000L, 4_000L, 5_200L, 6_500L, 8_000L
        )

        /** 读取系统设置，判断本服务是否已在「无障碍」中被开启 */
        fun isAccessibilityEnabled(context: Context): Boolean {
            val expected = ComponentName(context, SkipAdService::class.java)
            val raw = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(raw)
            while (splitter.hasNext()) {
                val component = ComponentName.unflattenFromString(splitter.next())
                if (component == expected) return true
            }
            return false
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastWindowChangedAt = 0L
    private var lastScanAt = 0L
    private var lastRetryStartAt = 0L
    private var lastSuccessPackage = ""
    private var lastSuccessAt = 0L

    private var retryPkg = ""
    private var retryStartedAt = 0L
    private var retryIndex = -1

    /** 定时重试：从会话起点按 RETRY_DELAYS 逐次扫描 */
    private val retryRunnable = object : Runnable {
        override fun run() {
            if (retryIndex < 0 || retryIndex >= RETRY_DELAYS.size) return
            val pkg = retryPkg
            val activeWindow = if (AppConfig.isAggressiveEnabled(this@SkipAdService)) {
                ACTIVE_WINDOW_AGGRESSIVE_MS
            } else {
                ACTIVE_WINDOW_MS
            }
            if (pkg.isNotEmpty() &&
                SystemClock.elapsedRealtime() - retryStartedAt <= activeWindow
            ) {
                val hit = performScan(pkg, "定时重试 ${retryIndex + 1}")
                if (hit) {
                    cancelRetry()
                    return
                }
            }
            retryIndex++
            if (retryIndex < RETRY_DELAYS.size &&
                SystemClock.elapsedRealtime() - retryStartedAt <= activeWindow
            ) {
                val delay = RETRY_DELAYS[retryIndex] - RETRY_DELAYS[retryIndex - 1]
                handler.postDelayed(this, delay)
            } else {
                cancelRetry()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppConfig.appendDiag(this, "无障碍服务已连接")
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AppConfig.isEnabled(this)) return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg.isBlank() || AppConfig.isExcluded(this, pkg)) return

        val now = SystemClock.elapsedRealtime()

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // 新窗口 → 开启一轮观察，并用定时重试兜底延迟出现的按钮
            lastWindowChangedAt = now
            startRetry(pkg, now)
            return
        }

        val activeWindow = if (AppConfig.isAggressiveEnabled(this)) {
            ACTIVE_WINDOW_AGGRESSIVE_MS
        } else {
            ACTIVE_WINDOW_MS
        }

        if (now - lastWindowChangedAt > activeWindow) {
            // 有些广告在 Activity 内部切换，不产生窗口状态变化事件，只发内容变化
            lastWindowChangedAt = now
            startRetry(pkg, now)
            return
        }

        if (now - lastScanAt < SCAN_INTERVAL_MS) return
        lastScanAt = now
        val hit = performScan(pkg, "内容变化")
        if (hit) cancelRetry()

        // 若上一轮重试已结束但画面仍在变化，防抖后补一轮
        if (retryIndex < 0 && now - lastRetryStartAt > 1_500L) {
            startRetry(pkg, now)
        }
    }

    private fun startRetry(pkg: String, now: Long) {
        handler.removeCallbacks(retryRunnable)
        retryPkg = pkg
        retryStartedAt = now
        lastRetryStartAt = now
        retryIndex = 0
        AppConfig.appendDiag(this, "检测到窗口：$pkg")
        handler.postDelayed(retryRunnable, RETRY_DELAYS[0])
    }

    private fun cancelRetry() {
        handler.removeCallbacks(retryRunnable)
        retryIndex = -1
    }

    /** 扫描所有可见窗口的根控件，逐个查找跳过按钮 */
    private fun collectRoots(): List<AccessibilityNodeInfo> {
        val roots = ArrayList<AccessibilityNodeInfo>(4)
        rootInActiveWindow?.let { roots.add(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val windows = windows
            if (!windows.isNullOrEmpty()) {
                for (window in windows) {
                    val root = window.root ?: continue
                    if (roots.any { it == root }) continue
                    roots.add(root)
                }
            }
        }
        return roots
    }

    /** 返回是否成功跳过 */
    private fun performScan(pkg: String, reason: String): Boolean {
        if (!AppConfig.isEnabled(this)) return false
        if (AppConfig.isExcluded(this, pkg)) return false

        val now = SystemClock.elapsedRealtime()
        if (pkg == lastSuccessPackage && now - lastSuccessAt < SAME_APP_COOLDOWN_MS) return false

        val aggressive = AppConfig.isAggressiveEnabled(this)
        val keywords = AppConfig.keywords(this)
        val roots = collectRoots()

        for (root in roots) {
            val node = AdMatcher.findSkipNode(root, keywords, aggressive) ?: continue
            val desc = AdMatcher.describe(node)
            if (clickNode(node, aggressive)) {
                lastSuccessPackage = pkg
                lastSuccessAt = now
                AppConfig.addSkip(this)
                AppConfig.recordLastSkip(this, pkg)
                AppConfig.appendDiag(this, "已跳过 $pkg｜$reason｜$desc")
                if (AppConfig.isShowToastEnabled(this)) {
                    Toast.makeText(this, "已跳过开屏广告", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "已点击跳过按钮：$pkg，$reason，$desc")
                return true
            }
            AppConfig.appendDiag(this, "点击失败 $pkg｜$reason｜$desc")
        }
        return false
    }

    /** 点击优先级：控件自身 → 可点击父控件 → 坐标手势兜底 */
    private fun clickNode(node: AccessibilityNodeInfo, aggressive: Boolean): Boolean {
        if (tryClick(node)) return true

        var current = node.parent
        var depth = 0
        val maxDepth = if (aggressive) 8 else 5
        while (current != null && depth < maxDepth) {
            if (current.isClickable && tryClick(current)) return true
            current = current.parent
            depth++
        }

        if (!AppConfig.isGestureFallbackEnabled(this)) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty || rect.width() < 3 || rect.height() < 3) return false
        return tap(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    private fun tryClick(node: AccessibilityNodeInfo): Boolean = try {
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    } catch (e: Exception) {
        false
    }

    /** 自定义绘制的按钮无法直接点击时，按坐标轻点；微微移动轨迹提升部分 ROM 的识别率 */
    private fun tap(x: Float, y: Float): Boolean = try {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 0.1f, y + 0.1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 80L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, handler)
    } catch (e: Exception) {
        false
    }

    override fun onInterrupt() {
        cancelRetry()
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        cancelRetry()
        super.onDestroy()
        Log.d(TAG, "无障碍服务已销毁")
    }
}
