package com.kaiser.chordy.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.kaiser.chordy.data.MoodTier

/**
 * Owns Chordy's screen presence: the draggable face bubble plus the retractable
 * speech bubble showing the current line. Plain Views over WindowManager — no
 * Compose, no recomposition, one post() when text changes.
 *
 * Line display contract (per brief): text shows IMMEDIATELY in showLine();
 * while TTS audio is still loading, a subtle "…" indicator sits under the line
 * and disappears when audio lands (or fails — audio is optional).
 */
class OverlayManager(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubble: BubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var speechLayout: LinearLayout? = null
    private var speechParams: WindowManager.LayoutParams? = null
    private var speechText: TextView? = null
    private var speechState: TextView? = null
    private var added = false
    private var speechAdded = false
    private var currentLine: String = ""
    private var lineGeneration = 0   // stale-callback guard: bump on every showLine

    companion object {
        private const val TAG = "OverlayManager"
        private const val LINE_VISIBLE_MS = 6_000L   // how long a line stays up
        private const val FADE_IN_MS = 350L          // bubble fade-in
        private const val FADE_OUT_MS = 700L         // bubble fade-out (the fix)
    }

    // drag bookkeeping
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var downAt = 0L

    // ---------- public API ----------

    /** Show the floating face. No-op if already on screen. */
    fun show(mood: MoodTier) {
        mainHandler.post {
            if (added) {
                bubble?.mood = mood
                return@post
            }
            val params = WindowManager.LayoutParams(
                dp(48), dp(48),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(16)
                y = dp(180)
            }
            val face = BubbleView(context).apply { this.mood = mood }
            face.setOnTouchListener { _, event -> handleDrag(event) }
            runCatching { wm.addView(face, params) }
                .onFailure { bubble = null; bubbleParams = null }
                .onSuccess {
                    bubble = face
                    bubbleParams = params
                    added = true
                }
        }
    }

    /** Hide face + speech bubble (service stop, overlay permission revoked). */
    fun hide() {
        mainHandler.post {
            if (speechAdded) speechLayout?.let { runCatching { wm.removeView(it) } }
            if (added) bubble?.let { runCatching { wm.removeView(it) } }
            speechAdded = false
            added = false
            bubble = null
            bubbleParams = null
            speechLayout = null
            speechText = null
            speechState = null
            speechParams = null
        }
    }

    /**
     * Display the current line immediately. [audioPending] true = TTS still
     * loading, show the subtle indicator until onAudioReady()/onAudioFailed().
     */
    fun showLine(text: String, mood: MoodTier, audioPending: Boolean) {
        val gen = ++lineGeneration
        currentLine = text
        mainHandler.post {
            bubble?.mood = mood
            if (!added) return@post
            ensureSpeechViews()
            speechText?.text = text
            speechState?.visibility = if (audioPending) View.VISIBLE else View.GONE
            positionSpeech()
            if (!speechAdded) {
                speechParams?.let { p ->
                    speechLayout?.let { layout ->
                        runCatching { wm.addView(layout, p) }
                            .onSuccess {
                                speechAdded = true
                                showSpeechWithFade()
                            }
                    }
                }
            } else {
                // Already visible (LLM swap-in): bump alpha back to full in
                // case a fade-out was mid-flight when the new line landed.
                speechLayout?.animate()?.cancel()
                speechLayout?.alpha = 1f
            }
            // Auto-retract 6s after THIS line landed — the generation guard
            // cancels it if a newer line swaps in before the timer fires.
            mainHandler.postDelayed({ if (lineGeneration == gen) hideSpeech() }, LINE_VISIBLE_MS)
        }
    }

    /** TTS bytes landed: stop the loading indicator. */
    fun onAudioReady() {
        mainHandler.post { speechState?.visibility = View.GONE }
    }

    /** Face mood update without a new line (counter reset, etc.). */
    fun setMood(mood: MoodTier) {
        mainHandler.post { bubble?.mood = mood }
    }

    /** Physical "he noticed!" pop on the face — call with every new line. */
    fun pop() {
        mainHandler.post { bubble?.pop() }
    }

    /** Quiet "thinking" bubble while the LLM writes the line. */
    fun showThinking() {
        mainHandler.post {
            if (!added) return@post
            ensureSpeechViews()
            speechText?.text = "…"
            speechState?.visibility = View.GONE
            positionSpeech()
            if (!speechAdded) {
                speechParams?.let { p ->
                    speechLayout?.let { layout ->
                        runCatching { wm.addView(layout, p) }
                            .onSuccess {
                                speechAdded = true
                                showSpeechWithFade()
                            }
                    }
                }
            }
        }
    }

    /** LLM failed — fade the thinking bubble away honestly. */
    fun clearThinking() {
        mainHandler.post { hideSpeech() }
    }

    fun isShowing(): Boolean = added

    // ---------- internals ----------

    private fun hideSpeech() {
        val layout = speechLayout
        if (!speechAdded || layout == null) return
        // Fade out, THEN remove — a hard removeView made the line vanish
        // mid-read with zero grace (the "doesn't fade" complaint).
        speechAdded = false
        layout.animate()
            .alpha(0f)
            .setDuration(FADE_OUT_MS)
            .withEndAction { runCatching { wm.removeView(layout) } }
            .start()
    }

    private fun ensureSpeechViews() {
        if (speechLayout != null) return
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61C1C24"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#33333F"))
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val lineView = TextView(context).apply {
            setTextColor(Color.parseColor("#EDEDEF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }
        val stateView = TextView(context).apply {
            setTextColor(Color.parseColor("#7FD4A8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            text = "… finding the right words"   // subtle loading indicator
        }
        layout.addView(lineView)
        layout.addView(stateView)
        speechLayout = layout
        speechText = lineView
        speechState = stateView
        speechParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    /** Fade the speech bubble IN when a line arrives. */
    private fun showSpeechWithFade() {
        val layout = speechLayout ?: return
        layout.alpha = 0f
        layout.animate().alpha(1f).setDuration(FADE_IN_MS).start()
    }

    /** Place the speech bubble to the right of the face, clamped on-screen. */
    private fun positionSpeech() {
        val face = bubble ?: return
        val params = speechParams ?: return
        val loc = IntArray(2)
        face.getLocationOnScreen(loc)
        val metrics = context.resources.displayMetrics
        params.x = (loc[0] + face.width + dp(10)).coerceAtMost(metrics.widthPixels - dp(220))
        params.y = (loc[1] - dp(6)).coerceAtLeast(dp(8))
        if (speechAdded) runCatching { wm.updateViewLayout(speechLayout, params) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleDrag(event: MotionEvent): Boolean {
        val face = bubble ?: return false
        val params = bubbleParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                downAt = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = initialX + (event.rawX - initialTouchX).toInt()
                params.y = initialY + (event.rawY - initialTouchY).toInt()
                runCatching { wm.updateViewLayout(face, params) }
                if (speechAdded) positionSpeech()
            }
            MotionEvent.ACTION_UP -> {
                val moved = kotlin.math.abs(event.rawX - initialTouchX) > dp(8) ||
                        kotlin.math.abs(event.rawY - initialTouchY) > dp(8)
                if (!moved) toggleSpeech()
            }
        }
        return true
    }

    private fun toggleSpeech() {
        if (speechAdded) {
            hideSpeech()
        } else if (currentLine.isNotBlank()) {
            ensureSpeechViews()
            speechText?.text = currentLine
            speechState?.visibility = View.GONE
            positionSpeech()
            speechParams?.let { p ->
                speechLayout?.let { layout ->
                    runCatching { wm.addView(layout, p) }
                        .onSuccess {
                            speechAdded = true
                            showSpeechWithFade()
                        }
                }
            }
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
        ).toInt()
}
