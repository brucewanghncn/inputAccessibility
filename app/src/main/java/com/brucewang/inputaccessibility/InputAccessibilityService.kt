package com.brucewang.inputaccessibility

import android.accessibilityservice.AccessibilityService
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class InputAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "InputAccessibilityService"
    }

    private var currentFocusedEditText: AccessibilityNodeInfo? = null
    private var isInputActivityShown = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Log.d(TAG, "onAccessibilityEvent: eventType=${event.eventType}, packageName=${event.packageName}, className=${event.className}, eventTypeName=${AccessibilityEvent.eventTypeToString(event.eventType)}")
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                handleViewFocused(event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                checkCurrentFocus(event)
            }
        }
    }

    // 判断event是否来自display[0]
    private fun isEventFromDisplayZero(event: AccessibilityEvent): Boolean {
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays

        val eventDisplayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            event.displayId
        } else {
            -1
        }
        // Log.d(TAG, "eventDisplayId=$eventDisplayId, available displays=${displays.size}")
        return eventDisplayId == displays[0].displayId
    }

    private fun handleViewFocused(event: AccessibilityEvent) {
        // 获取焦点的视图
        val source = event.source
        if (source == null) {
            return
        }

        // 只处理来自display[0]的事件
        if (isEventFromDisplayZero(event)) {
            // 检查是否是EditText
            if (isEditText(source)) {
                Log.d(TAG, "handleViewFocused: Detected EditText")
                // 确保不是我们自己的InputActivity中的EditText
                if (!isFromOurApp(source)) {
                    Log.d(TAG, "handleViewFocused: className=${source.className}, isEditable=${source.isEditable}, isFocusable=${source.isFocusable}")

                    // 即使是同一个EditText，如果InputActivity已关闭，也应该重新显示
                    if (!isInputActivityShown) {
                        currentFocusedEditText = source
                        showInputActivityForEditText(source)
                    }
                }
            } else {
                Log.d(TAG, "handleViewFocused: Focused view is not EditText")
                // 如果焦点移出EditText，且不是移到我们的应用
                if (!isFromOurApp(source)) {
                    hideInputActivity()
                }
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()

        // 如果窗口切换到我们的InputActivity，标记为已显示
        if (packageName == this.packageName) {
            isInputActivityShown = true
        } else if (isInputActivityShown) {
            // 如果从我们的应用切换出去，重置状态
            isInputActivityShown = false
        }
    }

    private fun checkCurrentFocus(event: AccessibilityEvent) {
        // 如果InputActivity正在显示，不进行检查
        if (isInputActivityShown) {
            return
        }

        // 只处理来自display[0]的事件
        if (isEventFromDisplayZero(event)) {

            // 延迟检查，确保界面已更新
            rootInActiveWindow?.let { root ->

                // 特殊处理，过滤packageName: com.ayaneo.gamewindow
                val packageName = root.packageName?.toString()
                if (packageName == "com.ayaneo.gamewindow") {
                    // Log.d(TAG, "checkCurrentFocus: Ignoring event from com.ayaneo.gamewindow")
                    return
                }

                val focusedNode = findFocusedEditText(root)
                if (focusedNode != null && !isFromOurApp(focusedNode)) {
                    // 如果发现有EditText获得焦点，且InputActivity未显示，则显示
                    if (!isInputActivityShown) {
                        currentFocusedEditText = focusedNode
                        showInputActivityForEditText(focusedNode)
                    }
                } else if (focusedNode == null && currentFocusedEditText != null && !isInputActivityShown) {
                    // EditText失去焦点
                    Log.d(TAG, "packageName: " + root.packageName)
                    Log.d(TAG, "checkCurrentFocus: No focused EditText found, hiding InputActivity")
                    hideInputActivity()
                }
            }
        }
    }

    private fun findFocusedEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isFocused && isEditText(root)) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findFocusedEditText(child)
            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun isEditText(node: AccessibilityNodeInfo): Boolean {
        // 检查类名是否为EditText或其子类
        val className = node.className?.toString() ?: ""

        return className.contains("EditText") ||
               (node.isEditable && node.isFocusable)
    }

    private fun isFromOurApp(node: AccessibilityNodeInfo): Boolean {
        // 检查节点是否来自我们的应用
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            val packageName = current.packageName?.toString()
            if (packageName == this.packageName) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun showInputActivityForEditText(editTextNode: AccessibilityNodeInfo) {
        try {
            // 获取EditText的相关信息
            val hint = editTextNode.hintText?.toString()
            val text = editTextNode.text?.toString()
            val inputType = editTextNode.inputType

            // 如果文本等于hint且没有选择范围，清空文本以便用户输入
            var textToSend = text
            if (editTextNode.textSelectionStart == -1 && editTextNode.textSelectionEnd == -1 && text == hint) {
                textToSend = ""
            }
            // 根据hint和文本内容推断IME选项
            val imeOptions = inferImeOptions(hint, text, editTextNode.contentDescription?.toString())

            Log.d(TAG, "Showing InputActivity for EditText: hint=$hint, text=$text, inputType=$inputType, imeOptions=$imeOptions")

            // 启动InputActivity
            InputActivity.start(
                context = this,
                editTextNode = editTextNode,
                hint = hint,
                text = textToSend,
                inputType = if (inputType != 0) inputType else android.text.InputType.TYPE_CLASS_TEXT,
                imeOptions = imeOptions
            )

            isInputActivityShown = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show InputActivity", e)
        }
    }

    private fun inferImeOptions(hint: String?, text: String?, contentDesc: String?): Int {
        val hintLower = hint?.lowercase() ?: ""
        val textLower = text?.lowercase() ?: ""
        val descLower = contentDesc?.lowercase() ?: ""

        return when {
            // 搜索框
            hintLower.contains("搜索") || hintLower.contains("search") ||
            textLower.contains("搜索") || textLower.contains("search") ||
            descLower.contains("搜索") || descLower.contains("search") -> {
                android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            }
            // 发送/提交
            hintLower.contains("发送") || hintLower.contains("send") ||
            textLower.contains("发送") || textLower.contains("send") ||
            hintLower.contains("提交") || hintLower.contains("submit") ||
            textLower.contains("提交") || textLower.contains("submit")
                 -> {
                android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            }
            // 完成
            hintLower.contains("完成") || hintLower.contains("done") ||
            textLower.contains("完成") || textLower.contains("done") -> {
                android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            }
            // 下一个
            hintLower.contains("下一个") || hintLower.contains("next") ||
            textLower.contains("下一个") || textLower.contains("next") -> {
                android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
            }
            // 前往/跳转
            hintLower.contains("前往") || hintLower.contains("go") ||
            textLower.contains("前往") || textLower.contains("go") ||
            hintLower.contains("跳转") || hintLower.contains("go") ||
            textLower.contains("跳转") || textLower.contains("go")
                -> {
                android.view.inputmethod.EditorInfo.IME_ACTION_GO
            }
            // 默认使用完成
            else -> android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
    }

    private fun hideInputActivity() {
        // 关闭InputActivity
        Log.d(TAG, "hideInputActivity: Closing InputActivity")
        InputActivity.closeCurrentInstance()

        // 重置状态
        currentFocusedEditText = null
        isInputActivityShown = false
    }

    override fun onInterrupt() {
        hideInputActivity()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideInputActivity()
    }

}

