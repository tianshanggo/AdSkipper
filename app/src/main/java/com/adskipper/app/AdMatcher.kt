package com.adskipper.app

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * 识别「跳过广告」按钮。
 * 三条线索：控件文字、contentDescription、控件资源 id。
 * 纯判断逻辑，不依赖 Service，便于单独调整规则。
 */
object AdMatcher {

    /** 跳过按钮的文字一般很短；过长的文本多半是正文段落，直接排除 */
    private const val MAX_TEXT_LENGTH = 18

    /** 控件 id 中常见的跳过按钮命名（小写包含匹配） */
    private val ID_HINTS = listOf(
        "skip",
        "ad_close",
        "close_ad",
        "ad_skip",
        "splash_skip",
        "splash_close",
        "jump_over"
    )

    /** 明显不是广告跳过按钮的文字，命中直接排除，避免误点授权 / 支付类按钮 */
    private val NEGATIVE_WORDS = listOf(
        "同意", "授权", "登录", "注册", "支付", "付款", "密码",
        "验证码", "下一步", "立即购买", "开通会员", "允许"
    )

    fun isSkipText(text: String?, keywords: List<String>): Boolean {
        if (text == null) return false
        // 去掉空白（含全角空格），「跳过 5S」「5s 后跳过」等都能命中
        val t = text.trim().replace(Regex("[\\s\\u00A0]+"), "")
        if (t.isEmpty() || t.length > MAX_TEXT_LENGTH) return false
        if (NEGATIVE_WORDS.any { t.contains(it) }) return false
        return keywords.any { t.contains(it, ignoreCase = true) }
    }

    fun isSkipViewId(id: String?): Boolean {
        if (id.isNullOrEmpty()) return false
        val lower = id.lowercase(Locale.ROOT)
        return ID_HINTS.any { lower.contains(it) }
    }

    /**
     * 在当前窗口的控件树中查找第一个疑似跳过按钮。
     * 用栈做深度优先遍历，最多检查 400 个节点，防止异常界面导致卡顿。
     */
    fun findSkipNode(root: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (root == null) return null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < 400) {
            val node = stack.removeLast()
            visited++
            try {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()
                    val desc = node.contentDescription?.toString()
                    val id = node.viewIdResourceName
                    if (isSkipText(text, keywords) ||
                        isSkipText(desc, keywords) ||
                        isSkipViewId(id)
                    ) {
                        return node
                    }
                }
                for (i in node.childCount - 1 downTo 0) {
                    node.getChild(i)?.let { stack.add(it) }
                }
            } catch (e: Exception) {
                // 控件树可能在遍历过程中被回收，忽略该节点即可
            }
        }
        return null
    }
}
