package com.brucewang.inputaccessibility

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText

class InputActivity : Activity() {

    companion object {
        private const val TAG = "InputActivity"
        const val EXTRA_HINT = "extra_hint"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_INPUT_TYPE = "extra_input_type"
        const val EXTRA_IME_OPTIONS = "extra_ime_options"

        private var targetEditTextNode: AccessibilityNodeInfo? = null
        private var currentInstance: InputActivity? = null

        fun start(
            context: Context,
            editTextNode: AccessibilityNodeInfo,
            hint: String?,
            text: String?,
            inputType: Int,
            imeOptions: Int
        ) {
            targetEditTextNode = editTextNode
            val intent = Intent(context, InputActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra(EXTRA_HINT, hint)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_INPUT_TYPE, inputType)
                putExtra(EXTRA_IME_OPTIONS, imeOptions)
            }

            // 尝试在第二个屏幕启动Activity
            val displayManager = context.getSystemService(DISPLAY_SERVICE) as DisplayManager
            val displays = displayManager.displays

            // Log.d(TAG, "Available displays: ${displays.size}")
            displays.forEachIndexed { index, display ->
                Log.d(TAG, "Display[$index]: id=${display.displayId}, name=${display.name}, flags=${display.flags}")
            }

            if (displays.size > 1) {
                // 优先选择非默认显示器（通常是副屏）
                val secondaryDisplay = displays.firstOrNull { it.displayId != android.view.Display.DEFAULT_DISPLAY }

                if (secondaryDisplay != null) {
                    Log.d(TAG, "Starting activity on secondary display: id=${secondaryDisplay.displayId}, name=${secondaryDisplay.name}")
                    val options = android.app.ActivityOptions.makeBasic()
                    options.launchDisplayId = secondaryDisplay.displayId
                    try {
                        context.startActivity(intent, options.toBundle())
                        Log.d(TAG, "Activity started successfully on secondary display")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start activity on secondary display", e)
                    }
                    return
                }
            }

            // 如果没有第二个屏幕，在默认屏幕启动
            Log.d(TAG, "Starting activity on default display")
            try {
                context.startActivity(intent)
                Log.d(TAG, "Activity started successfully on default display")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start activity on default display", e)
            }
        }

        /**
         * 关闭当前的InputActivity实例
         */
        fun closeCurrentInstance() {
            currentInstance?.let { activity ->
                Log.d(TAG, "Closing InputActivity instance")
                activity.finish()
                currentInstance = null
            }
        }

        fun getTargetEditText(): AccessibilityNodeInfo? = targetEditTextNode
    }

    private lateinit var etFloatingInput: EditText
    private lateinit var btnClose: Button
    private var textSyncEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 保存当前实例
        currentInstance = this

        // 记录当前显示器信息
        val currentDisplay = display
        Log.d(TAG, "Activity running on display: id=${currentDisplay?.displayId}, name=${currentDisplay?.name}")

        // 设置窗口属性
        window.apply {

            // 设置软键盘模式 - 自动调整窗口大小以适应软键盘
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }

        setContentView(R.layout.activity_input)

        etFloatingInput = findViewById(R.id.etFloatingInput)
        btnClose = findViewById(R.id.btnClose)

        setupView()
    }

    private fun setupView() {
        val hint = intent.getStringExtra(EXTRA_HINT)
        val text = intent.getStringExtra(EXTRA_TEXT)
        val inputType = intent.getIntExtra(EXTRA_INPUT_TYPE, 0)
        val imeOptions = intent.getIntExtra(EXTRA_IME_OPTIONS,
            android.view.inputmethod.EditorInfo.IME_ACTION_DONE)

        // 设置输入框属性
        etFloatingInput.apply {
            if (!hint.isNullOrEmpty()) {
                this.hint = hint
            }

            if (!text.isNullOrEmpty()) {
                setText(text)
                setSelection(text.length)
            }

            if (inputType != 0) {
                this.inputType = inputType
            }

            this.imeOptions = imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI

            // 监听文本变化，实时同步到目标EditText
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (textSyncEnabled) {
                        syncTextToTarget(s?.toString() ?: "")
                    }
                }
            })

            // 监听 Enter 键
            setOnEditorActionListener { _, actionId, _ ->
                triggerTargetAction(actionId)
                true
            }

            // 请求焦点
            requestFocus()
        }

        // 关闭按钮
        btnClose.setOnClickListener {
            finish()
        }

        // 延迟显示键盘
        etFloatingInput.postDelayed({
            showKeyboard()
        }, 100)
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        etFloatingInput.requestFocus()
        imm.showSoftInput(etFloatingInput, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * 实时同步文本到目标 EditText
     */
    private fun syncTextToTarget(text: String) {
        targetEditTextNode?.let { node ->
            try {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync text to target", e)
            }
        }
    }

    /**
     * 触发目标 EditText 的 EditorAction (Enter 键操作)
     */
    private fun triggerTargetAction(actionId: Int) {
        targetEditTextNode?.let { node ->
            try {
                Log.d(TAG, "Triggering target action for IME action: $actionId")

                var actionPerformed = false

                // 尝试使用 IME Enter 的 Accessibility Action
                val imeAction = node.actionList?.firstOrNull {
                    it.id == android.R.id.accessibilityActionImeEnter
                }

                imeAction?.let { action ->
                    actionPerformed = node.performAction(action.id)
                    Log.d(TAG, "Performed IME action via AccessibilityAction, success=$actionPerformed")
                }

//                Log.d(TAG, "Final action result: $actionPerformed")

                // 如果是搜索或发送操作成功，延迟关闭Activity
                if (actionPerformed &&
                    (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                            actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                            actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO)) {
                    etFloatingInput.postDelayed({
                        finish()
                    }, 200)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger target action", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== onDestroy() called ===")

        // 清除实例引用
        if (currentInstance == this) {
            currentInstance = null
        }

        targetEditTextNode = null
    }

    override fun onStart() {
        super.onStart()
//        Log.d(TAG, "=== onStart() called ===")
    }

    override fun onResume() {
        super.onResume()
//        Log.d(TAG, "=== onResume() called ===")
    }

    override fun onPause() {
        super.onPause()
//        Log.d(TAG, "=== onPause() called ===")
    }

    override fun onStop() {
        super.onStop()
//        Log.d(TAG, "=== onStop() called ===")
    }
}

