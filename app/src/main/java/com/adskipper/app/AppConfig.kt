package com.adskipper.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 配置与统计仓库。
 * 所有数据只写在本机 SharedPreferences，应用没有 INTERNET 权限，也不会上报任何内容。
 */
object AppConfig {

    private const val PREF_NAME = "ad_skipper_config"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_GESTURE_FALLBACK = "gesture_fallback"
    private const val KEY_SHOW_TOAST = "show_toast"
    private const val KEY_KEYWORDS = "keywords"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_STAT_TOTAL = "stat_total"
    private const val KEY_STAT_TODAY = "stat_today"
    private const val KEY_STAT_DATE = "stat_date"
    private const val KEY_LAST_PKG = "last_pkg"
    private const val KEY_LAST_TIME = "last_time"

    /** 默认关键词：覆盖中文「跳过 / 跳过广告 5s」与英文 skip 的常见写法 */
    val DEFAULT_KEYWORDS: List<String> = listOf(
        "跳过广告",
        "跳过",
        "点击跳过",
        "跳過廣告",
        "跳過",
        "关闭广告",
        "关闭该广告",
        "Skip Ad",
        "skip",
        "Close Ad"
    )

    /** 内置排除：自身、系统界面、系统设置等，避免在这些界面产生误触 */
    private val BUILT_IN_EXCLUDED: Set<String> = setOf(
        "com.adskipper.app",
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.vending",
        "com.google.android.gms"
    )

    private fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_ENABLED, true)
    fun setEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_ENABLED, v).apply()

    fun isGestureFallbackEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_GESTURE_FALLBACK, true)
    fun setGestureFallbackEnabled(c: Context, v: Boolean) =
        prefs(c).edit().putBoolean(KEY_GESTURE_FALLBACK, v).apply()

    fun isShowToastEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_SHOW_TOAST, false)
    fun setShowToastEnabled(c: Context, v: Boolean) =
        prefs(c).edit().putBoolean(KEY_SHOW_TOAST, v).apply()

    fun keywords(c: Context): List<String> {
        val raw = prefs(c).getString(KEY_KEYWORDS, null) ?: return DEFAULT_KEYWORDS
        return parseLines(raw)
    }

    fun keywordsText(c: Context): String = keywords(c).joinToString("\n")

    /** 保存关键词，返回实际生效的条数 */
    fun saveKeywords(c: Context, text: String): Int {
        val list = parseLines(text)
        prefs(c).edit().putString(KEY_KEYWORDS, list.joinToString("\n")).apply()
        return list.size
    }

    fun resetKeywords(c: Context) = prefs(c).edit().remove(KEY_KEYWORDS).apply()

    fun userWhitelist(c: Context): List<String> =
        parseLines(prefs(c).getString(KEY_WHITELIST, "") ?: "")

    fun addToWhitelist(c: Context, pkg: String): Boolean {
        val p = pkg.trim()
        if (p.isEmpty()) return false
        val list = userWhitelist(c).toMutableList()
        if (list.contains(p)) return false
        list.add(p)
        saveWhitelist(c, list)
        return true
    }

    fun removeFromWhitelist(c: Context, pkg: String) =
        saveWhitelist(c, userWhitelist(c).filter { it != pkg })

    private fun saveWhitelist(c: Context, list: List<String>) =
        prefs(c).edit().putString(KEY_WHITELIST, list.joinToString("\n")).apply()

    /** 每次事件都调用，内部只读一次 SharedPreferences，开销可忽略 */
    fun isExcluded(c: Context, pkg: String): Boolean =
        BUILT_IN_EXCLUDED.contains(pkg) || userWhitelist(c).contains(pkg)

    fun totalCount(c: Context): Int = prefs(c).getInt(KEY_STAT_TOTAL, 0)

    fun todayCount(c: Context): Int {
        val p = prefs(c)
        return if (p.getString(KEY_STAT_DATE, "") == today()) p.getInt(KEY_STAT_TODAY, 0) else 0
    }

    fun addSkip(c: Context) {
        val p = prefs(c)
        val date = today()
        val sameDay = p.getString(KEY_STAT_DATE, "") == date
        val todayCount = if (sameDay) p.getInt(KEY_STAT_TODAY, 0) else 0
        p.edit()
            .putString(KEY_STAT_DATE, date)
            .putInt(KEY_STAT_TODAY, todayCount + 1)
            .putInt(KEY_STAT_TOTAL, p.getInt(KEY_STAT_TOTAL, 0) + 1)
            .apply()
    }

    /** 记录最近一次成功跳过，方便在设置页确认规则是否在目标 App 上生效 */
    fun recordLastSkip(c: Context, pkg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        prefs(c).edit()
            .putString(KEY_LAST_PKG, pkg)
            .putString(KEY_LAST_TIME, time)
            .apply()
    }

    fun lastSkipInfo(c: Context): String {
        val pkg = prefs(c).getString(KEY_LAST_PKG, "") ?: ""
        if (pkg.isEmpty()) return "暂无记录"
        val time = prefs(c).getString(KEY_LAST_TIME, "") ?: ""
        return "$pkg  ·  $time"
    }

    fun resetStats(c: Context) {
        prefs(c).edit()
            .putInt(KEY_STAT_TOTAL, 0)
            .putInt(KEY_STAT_TODAY, 0)
            .putString(KEY_STAT_DATE, today())
            .remove(KEY_LAST_PKG)
            .remove(KEY_LAST_TIME)
            .apply()
    }

    /** 支持换行、英文逗号、中文逗号三种分隔方式 */
    private fun parseLines(raw: String): List<String> =
        raw.split('\n', ',', '，')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
