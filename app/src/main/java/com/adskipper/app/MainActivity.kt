package com.adskipper.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.text.method.ScrollingMovementMethod
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusDot: View
    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var btnOpenSettings: Button
    private lateinit var switchEnabled: Switch
    private lateinit var switchAggressive: Switch
    private lateinit var switchGesture: Switch
    private lateinit var switchToast: Switch
    private lateinit var etKeywords: EditText
    private lateinit var etPackage: EditText
    private lateinit var llWhitelist: LinearLayout
    private lateinit var tvTotal: TextView
    private lateinit var tvToday: TextView
    private lateinit var tvLastSkip: TextView
    private lateinit var tvDiag: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        loadConfig()
        bindActions()
        renderWhitelist()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshStats()
        refreshDiag()
    }

    private fun bindViews() {
        statusDot = findViewById(R.id.statusDot)
        tvStatus = findViewById(R.id.tvStatus)
        tvStatusDesc = findViewById(R.id.tvStatusDesc)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        switchEnabled = findViewById(R.id.switchEnabled)
        switchAggressive = findViewById(R.id.switchAggressive)
        switchGesture = findViewById(R.id.switchGesture)
        switchToast = findViewById(R.id.switchToast)
        etKeywords = findViewById(R.id.etKeywords)
        etPackage = findViewById(R.id.etPackage)
        llWhitelist = findViewById(R.id.llWhitelist)
        tvTotal = findViewById(R.id.tvTotal)
        tvToday = findViewById(R.id.tvToday)
        tvLastSkip = findViewById(R.id.tvLastSkip)
        tvDiag = findViewById(R.id.tvDiag)
        tvDiag.movementMethod = ScrollingMovementMethod()
    }

    /** 先赋值再注册监听，避免初始化时把默认值重复写回 */
    private fun loadConfig() {
        switchEnabled.isChecked = AppConfig.isEnabled(this)
        switchAggressive.isChecked = AppConfig.isAggressiveEnabled(this)
        switchGesture.isChecked = AppConfig.isGestureFallbackEnabled(this)
        switchToast.isChecked = AppConfig.isShowToastEnabled(this)
        etKeywords.setText(AppConfig.keywordsText(this))
    }

    private fun bindActions() {
        switchEnabled.setOnCheckedChangeListener { _, checked ->
            AppConfig.setEnabled(this, checked)
            refreshStatus()
        }
        switchAggressive.setOnCheckedChangeListener { _, checked ->
            AppConfig.setAggressiveEnabled(this, checked)
        }
        switchGesture.setOnCheckedChangeListener { _, checked ->
            AppConfig.setGestureFallbackEnabled(this, checked)
        }
        switchToast.setOnCheckedChangeListener { _, checked ->
            AppConfig.setShowToastEnabled(this, checked)
        }

        btnOpenSettings.setOnClickListener { openAccessibilitySettings() }

        findViewById<Button>(R.id.btnSaveKeywords).setOnClickListener {
            val count = AppConfig.saveKeywords(this, etKeywords.text.toString())
            etKeywords.setText(AppConfig.keywordsText(this))
            toast("已保存 $count 条关键词，立即生效")
        }
        findViewById<Button>(R.id.btnResetKeywords).setOnClickListener {
            AppConfig.resetKeywords(this)
            etKeywords.setText(AppConfig.keywordsText(this))
            toast("已恢复默认关键词")
        }

        findViewById<Button>(R.id.btnAddPackage).setOnClickListener {
            val pkg = etPackage.text.toString().trim()
            if (pkg.isEmpty()) {
                toast("请先填写应用包名")
            } else if (AppConfig.addToWhitelist(this, pkg)) {
                etPackage.setText("")
                renderWhitelist()
                toast("已加入白名单")
            } else {
                toast("该包名已在白名单中")
            }
        }

        findViewById<Button>(R.id.btnResetStats).setOnClickListener {
            AppConfig.resetStats(this)
            refreshStats()
            toast("统计已清零")
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } else {
                toast("当前系统无需该设置")
            }
        }
        findViewById<Button>(R.id.btnAppDetail).setOnClickListener {
            safeStart(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }

        findViewById<Button>(R.id.btnClearDiag).setOnClickListener {
            AppConfig.clearDiag(this)
            refreshDiag()
            toast("日志已清空")
        }
    }

    private fun refreshStatus() {
        val serviceOn = SkipAdService.isAccessibilityEnabled(this)
        val enabled = AppConfig.isEnabled(this)
        when {
            !serviceOn -> {
                statusDot.setBackgroundResource(R.drawable.dot_off)
                tvStatus.text = "无障碍服务未开启"
                tvStatus.setTextColor(getColor(R.color.red))
                tvStatusDesc.text = "点击下方按钮，在系统设置里找到「开屏跳过」并开启"
            }
            !enabled -> {
                statusDot.setBackgroundResource(R.drawable.dot_off)
                tvStatus.text = "已暂停"
                tvStatus.setTextColor(getColor(R.color.red))
                tvStatusDesc.text = "服务已开启，但「启用自动跳过」开关处于关闭状态"
            }
            else -> {
                statusDot.setBackgroundResource(R.drawable.dot_on)
                tvStatus.text = "运行中"
                tvStatus.setTextColor(getColor(R.color.green))
                tvStatusDesc.text = "服务已开启，正在自动跳过开屏广告"
            }
        }
        btnOpenSettings.text = if (serviceOn) "前往无障碍设置（可关闭服务）" else "开启无障碍服务"
    }

    private fun refreshStats() {
        tvTotal.text = AppConfig.totalCount(this).toString()
        tvToday.text = AppConfig.todayCount(this).toString()
        tvLastSkip.text = AppConfig.lastSkipInfo(this)
    }

    private fun refreshDiag() {
        val text = AppConfig.diagText(this)
        tvDiag.text = if (text.isEmpty()) "暂无日志，打开带开屏广告的 App 后会自动记录。" else text
    }

    private fun renderWhitelist() {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        llWhitelist.removeAllViews()
        val list = AppConfig.userWhitelist(this)
        if (list.isEmpty()) {
            llWhitelist.addView(TextView(this).apply {
                text = "暂无自定义白名单（已内置排除本应用与系统组件）"
                textSize = 12f
                setTextColor(Color.parseColor("#6B7280"))
            })
            return
        }

        for (pkg in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }
            row.addView(TextView(this).apply {
                text = pkg
                textSize = 13f
                setTextColor(Color.parseColor("#1F2937"))
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            row.addView(Button(this).apply {
                text = "移除"
                textSize = 12f
                setAllCaps(false)
                setMinWidth(0)
                setMinimumWidth(0)
                setPadding(dp(12), dp(2), dp(12), dp(2))
                setBackgroundResource(R.drawable.bg_button_secondary)
                setTextColor(Color.parseColor("#2563EB"))
                setOnClickListener {
                    AppConfig.removeFromWhitelist(this@MainActivity, pkg)
                    renderWhitelist()
                }
            })
            llWhitelist.addView(row)
        }
    }

    private fun openAccessibilitySettings() {
        toast("请在系统设置中找到「开屏跳过」并开启")
        safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("当前系统没有该设置页面，请手动前往系统设置")
        } catch (e: Exception) {
            toast("无法打开设置页面")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
