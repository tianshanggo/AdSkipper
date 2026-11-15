package com.adskipper.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 核心服务：监听窗口变化，在新窗口出现后的短时间内查找并点击「跳过」按钮。
 *
 * 设计上的几道防误触闸门：
 * 1. 只处理窗口状态变化 / 窗口内容变化两类事件；
 * 2. 内容变化事件只在「窗口切换后 8 秒」的观察期内处理，且最多每 250ms 扫一次；
 * 3. 同一 App 成功点击后 3 秒内不再重复点击；
 * 4. 文本必须短于 18 字且不含授权 / 支付类敏感词；
 * 5. 白名单与系统组件直接跳过。
 */
class SkipAdService : AccessibilityService() {

    companion object {
        private const val TAG = "AdSkipper"
        private const val SCAN_INTERVAL_MS = 250L
        private const val ACTIVE_WINDOW_MS = 8_000L
        private const val SAME_APP_COOLDOWN_MS = 3_000L

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

    private var lastWindowChangedAt = 0L
    private var lastScanAt = 0L
    private var lastSuccessPackage = ""
    private var lastSuccessAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
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
            // 新窗口出现 → 开启一段新的开屏广告观察期
            lastWindowChangedAt = now
        } else {
            // TYPE_WINDOW_CONTENT_CHANGED 极其密集，必须限流
            if (now - lastWindowChangedAt > ACTIVE_WINDOW_MS) return
            if (now - lastScanAt < SCAN_INTERVAL_MS) return
        }
        if (pkg == lastSuccessPackage && now - lastSuccessAt < SAME_APP_COOLDOWN_MS) return
        lastScanAt = now

        val root = rootInActiveWindow ?: return
        val node = AdMatcher.findSkipNode(root, AppConfig.keywords(this)) ?: return
        if (!clickNode(node)) return

        lastSuccessPackage = pkg
        lastSuccessAt = now
        AppConfig.addSkip(this)
        AppConfig.recordLastSkip(this, pkg)
        if (AppConfig.isShowToastEnabled(this)) {
            Toast.makeText(this, "已跳过开屏广告", Toast.LENGTH_SHORT).show()
        }
        Log.d(TAG, "已点击跳过按钮：$pkg")
    }

    /** 点击优先级：控件自身可点 → 向上找可点父控件 → 坐标手势兜底 */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }

        if (!AppConfig.isGestureFallbackEnabled(this)) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty || rect.width() < 4 || rect.height() < 4) return false
        return tap(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    /** 有些跳过按钮是自定义绘制的 View，不可点击也没有 id，只能按坐标轻点 */
    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "无障碍服务已销毁")
    }
}
