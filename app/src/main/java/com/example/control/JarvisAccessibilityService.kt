package com.example.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * User-enabled AccessibilityService empowering JARVIS's Google Voice Access–inspired
 * Offline Voice Control Engine:
 * - System navigation (Back, Home, Recents, Notifications, Quick Settings, Lock Screen, Screenshot)
 * - Directional gestures (Scroll Up/Down/Left/Right, Coordinate Tap)
 * - Numbered Tap Grid Overlay ("Show numbers" -> "Tap 3") using TYPE_ACCESSIBILITY_OVERLAY
 * - Voice Typing & Voice Text Editing (Set, Append, Clear, Delete word, Backspace, Replace, Copy/Paste)
 * - Screen label reading & element clicking by visible text
 */
class JarvisAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: FrameLayout? = null
    private val numberedBounds = ConcurrentHashMap<Int, Rect>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceRef = WeakReference(this)
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event-driven updates are read on demand without background logging or data export.
    }

    override fun onInterrupt() {
        hideNumberOverlayInternal()
    }

    override fun onDestroy() {
        hideNumberOverlayInternal()
        _isConnected.value = false
        if (serviceRef?.get() === this) {
            serviceRef = null
        }
        super.onDestroy()
    }

    fun dispatchSwipe(down: Boolean): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.5f
        val startY = if (down) dm.heightPixels * 0.72f else dm.heightPixels * 0.28f
        val endY = if (down) dm.heightPixels * 0.28f else dm.heightPixels * 0.72f
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 280L)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun dispatchHorizontalSwipe(scrollRight: Boolean): Boolean {
        val dm = resources.displayMetrics
        val y = dm.heightPixels * 0.5f
        val startX = if (scrollRight) dm.widthPixels * 0.80f else dm.widthPixels * 0.20f
        val endX = if (scrollRight) dm.widthPixels * 0.20f else dm.widthPixels * 0.80f
        val path = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 260L)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun dispatchTap(xFraction: Float, yFraction: Float): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels * xFraction.coerceIn(0.05f, 0.95f)
        val y = dm.heightPixels * yFraction.coerceIn(0.05f, 0.95f)
        return dispatchTapPixels(x, y)
    }

    fun dispatchTapPixels(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x.coerceAtLeast(1f), y.coerceAtLeast(1f))
            lineTo((x + 1f).coerceAtLeast(2f), (y + 1f).coerceAtLeast(2f))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun showNumberOverlayInternal(): Int {
        val root = rootInActiveWindow ?: return 0
        val targets = mutableListOf<Rect>()
        collectClickableBounds(root, targets, 0)
        if (targets.isEmpty()) return 0

        val density = resources.displayMetrics.density
        val statusBarOffset = 0
        val showAction = Runnable {
            hideNumberOverlayInternal()
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@Runnable
            val container = FrameLayout(this)
            numberedBounds.clear()

            targets.take(36).forEachIndexed { idx, rect ->
                val number = idx + 1
                numberedBounds[number] = rect

                val badge = TextView(this).apply {
                    text = number.toString()
                    textSize = 11f
                    setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0B1220"))
                    gravity = Gravity.CENTER
                    val padH = (6 * density).toInt()
                    val padV = (2 * density).toInt()
                    setPadding(padH, padV, padH, padV)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#FFB800"))
                        setStroke((1.5f * density).toInt(), Color.parseColor("#FFF8DC"))
                    }
                    elevation = 8f * density
                }

                val badgeSize = (24 * density).toInt()
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    badgeSize
                ).apply {
                    leftMargin = (rect.left + (4 * density).toInt()).coerceAtLeast(4)
                    topMargin = (rect.top - statusBarOffset + (2 * density).toInt()).coerceAtLeast(4)
                }
                container.addView(badge, lp)
            }

            val banner = TextView(this).apply {
                text = "JARVIS Voice Access • Say 'Tap 1..${numberedBounds.size}' or 'Hide numbers'"
                textSize = 11.5f
                setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                setTextColor(Color.parseColor("#FFE082"))
                gravity = Gravity.CENTER
                val padH = (14 * density).toInt()
                val padV = (6 * density).toInt()
                setPadding(padH, padV, padH, padV)
                background = GradientDrawable().apply {
                    cornerRadius = 18f * density
                    setColor(Color.parseColor("#E60B1220"))
                    setStroke((1.5f * density).toInt(), Color.parseColor("#FFB800"))
                }
            }
            val bannerLp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = (42 * density).toInt()
            }
            container.addView(banner, bannerLp)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            runCatching {
                wm.addView(container, params)
                overlayView = container
                _overlayActive.value = true
                _numberedTargetCount.value = numberedBounds.size
            }.onFailure {
                _overlayActive.value = false
                _numberedTargetCount.value = 0
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            showAction.run()
        } else {
            mainHandler.post(showAction)
        }
        return targets.size.coerceAtMost(36)
    }

    fun hideNumberOverlayInternal() {
        val removeAction = Runnable {
            val view = overlayView
            overlayView = null
            if (view != null) {
                val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                runCatching { wm?.removeViewImmediate(view) }
            }
            numberedBounds.clear()
            _overlayActive.value = false
            _numberedTargetCount.value = 0
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeAction.run()
        } else {
            mainHandler.post(removeAction)
        }
    }

    fun tapNumberedInternal(number: Int): Boolean {
        val rect = numberedBounds[number] ?: return false
        hideNumberOverlayInternal()
        return dispatchTapPixels(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun extractScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val lines = LinkedHashSet<String>()
        collectText(root, lines, 0)
        return lines.take(20).joinToString(" • ")
    }

    fun clickNodeByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(query).orEmpty()
        for (node in matches) {
            var target: AccessibilityNodeInfo? = node
            var hops = 0
            while (target != null && hops < 5) {
                if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
                target = target.parent
                hops++
            }
        }
        return false
    }

    private fun resolveEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && (focused.isEditable || focused.className?.toString()?.contains("EditText") == true)) {
            return focused
        }
        return findFirstEditableNode(root, 0)
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 12) return null
        if (node.isVisibleToUser && node.isEditable) return node
        for (i in 0 until node.childCount) {
            findFirstEditableNode(node.getChild(i), depth + 1)?.let { return it }
        }
        return null
    }

    private fun readEditableTextSafe(node: AccessibilityNodeInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && node.isShowingHintText) {
            return ""
        }
        return node.text?.toString().orEmpty()
    }

    fun setFocusedFieldText(text: String): Boolean {
        val target = resolveEditableNode() ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun appendFocusedFieldText(textToAdd: String): Boolean {
        val target = resolveEditableNode() ?: return false
        val current = readEditableTextSafe(target)
        val combined = if (current.isBlank()) textToAdd else "${current.trimEnd()} $textToAdd"
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combined)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun clearFocusedFieldText(): Boolean = setFocusedFieldText("")

    fun backspaceFocusedField(): Boolean {
        val target = resolveEditableNode() ?: return false
        val current = readEditableTextSafe(target)
        if (current.isEmpty()) return false
        val updated = current.dropLast(1)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun deleteLastWordFocusedField(): Boolean {
        val target = resolveEditableNode() ?: return false
        val current = readEditableTextSafe(target).trimEnd()
        if (current.isEmpty()) return false
        val lastSpace = current.lastIndexOf(' ')
        val updated = if (lastSpace >= 0) current.substring(0, lastSpace) else ""
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun replaceInFocusedField(oldPart: String, newPart: String): Boolean {
        val target = resolveEditableNode() ?: return false
        val current = readEditableTextSafe(target)
        if (current.isEmpty() || !current.contains(oldPart, ignoreCase = true)) return false
        val updated = current.replace(Regex(Regex.escape(oldPart), RegexOption.IGNORE_CASE), newPart)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun selectAllInFocusedField(): Boolean {
        val target = resolveEditableNode() ?: return false
        val len = readEditableTextSafe(target).length
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    fun performFieldClipboardAction(action: Int): Boolean {
        val target = resolveEditableNode() ?: return false
        return target.performAction(action)
    }

    private fun collectClickableBounds(
        node: AccessibilityNodeInfo?,
        out: MutableList<Rect>,
        depth: Int
    ) {
        if (node == null || depth > 14 || out.size >= 36) return
        if (node.isVisibleToUser && (node.isClickable || node.isEditable || node.isCheckable)) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() >= 24 && rect.height() >= 24) {
                val overlapsExisting = out.any { existing ->
                    kotlin.math.abs(existing.centerX() - rect.centerX()) < 22 &&
                        kotlin.math.abs(existing.centerY() - rect.centerY()) < 22
                }
                if (!overlapsExisting) {
                    out.add(rect)
                }
            }
        }
        for (i in 0 until node.childCount) {
            collectClickableBounds(node.getChild(i), out, depth + 1)
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: MutableSet<String>, depth: Int) {
        if (node == null || depth > 10 || out.size >= 25) return
        val txt = node.text?.toString()?.trim()
        if (!txt.isNullOrBlank() && txt.length in 2..60) {
            out.add(txt)
        } else {
            val desc = node.contentDescription?.toString()?.trim()
            if (!desc.isNullOrBlank() && desc.length in 2..60) {
                out.add(desc)
            }
        }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out, depth + 1)
        }
    }

    companion object {
        private var serviceRef: WeakReference<JarvisAccessibilityService>? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _overlayActive = MutableStateFlow(false)
        val overlayActive: StateFlow<Boolean> = _overlayActive.asStateFlow()

        private val _numberedTargetCount = MutableStateFlow(0)
        val numberedTargetCount: StateFlow<Int> = _numberedTargetCount.asStateFlow()

        fun performGlobalActionSafe(action: Int): Boolean {
            val service = serviceRef?.get() ?: return false
            return service.performGlobalAction(action)
        }

        fun performBack(): Boolean = performGlobalActionSafe(GLOBAL_ACTION_BACK)
        fun performHome(): Boolean = performGlobalActionSafe(GLOBAL_ACTION_HOME)
        fun performRecents(): Boolean = performGlobalActionSafe(GLOBAL_ACTION_RECENTS)
        fun performNotifications(): Boolean = performGlobalActionSafe(GLOBAL_ACTION_NOTIFICATIONS)
        fun performQuickSettings(): Boolean = performGlobalActionSafe(GLOBAL_ACTION_QUICK_SETTINGS)
        fun performPowerDialog(): Boolean = performGlobalActionSafe(GLOBAL_ACTION_POWER_DIALOG)

        fun performLockScreen(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                performGlobalActionSafe(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                false
            }
        }

        fun performTakeScreenshot(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                performGlobalActionSafe(GLOBAL_ACTION_TAKE_SCREENSHOT)
            } else {
                false
            }
        }

        fun performScroll(down: Boolean): Boolean = serviceRef?.get()?.dispatchSwipe(down) == true
        fun performHorizontalScroll(right: Boolean): Boolean =
            serviceRef?.get()?.dispatchHorizontalSwipe(right) == true

        fun performTapAt(xFraction: Float, yFraction: Float): Boolean =
            serviceRef?.get()?.dispatchTap(xFraction, yFraction) == true

        fun showNumberedTapGrid(): Int = serviceRef?.get()?.showNumberOverlayInternal() ?: 0
        fun hideNumberedTapGrid() {
            serviceRef?.get()?.hideNumberOverlayInternal()
        }

        fun tapNumberedElement(number: Int): Boolean =
            serviceRef?.get()?.tapNumberedInternal(number) == true

        fun clickElementByText(query: String): Boolean =
            serviceRef?.get()?.clickNodeByText(query) == true

        fun inputTextIntoFocusedField(text: String): Boolean =
            serviceRef?.get()?.setFocusedFieldText(text) == true

        fun appendTextToFocusedField(text: String): Boolean =
            serviceRef?.get()?.appendFocusedFieldText(text) == true

        fun clearFocusedField(): Boolean =
            serviceRef?.get()?.clearFocusedFieldText() == true

        fun backspaceFocusedField(): Boolean =
            serviceRef?.get()?.backspaceFocusedField() == true

        fun deleteLastWordInFocusedField(): Boolean =
            serviceRef?.get()?.deleteLastWordFocusedField() == true

        fun replaceInFocusedField(oldPart: String, newPart: String): Boolean =
            serviceRef?.get()?.replaceInFocusedField(oldPart, newPart) == true

        fun selectAllInFocusedField(): Boolean =
            serviceRef?.get()?.selectAllInFocusedField() == true

        fun copyFocusedField(): Boolean =
            serviceRef?.get()?.performFieldClipboardAction(AccessibilityNodeInfo.ACTION_COPY) == true

        fun cutFocusedField(): Boolean =
            serviceRef?.get()?.performFieldClipboardAction(AccessibilityNodeInfo.ACTION_CUT) == true

        fun pasteIntoFocusedField(): Boolean =
            serviceRef?.get()?.performFieldClipboardAction(AccessibilityNodeInfo.ACTION_PASTE) == true

        fun readActiveScreenText(): String = serviceRef?.get()?.extractScreenText().orEmpty()
    }
}
