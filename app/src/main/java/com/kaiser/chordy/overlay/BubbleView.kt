package com.kaiser.chordy.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.kaiser.chordy.data.MoodTier

/**
 * Chordy himself: a 48dp round face with two eyes and a mood-shaped mouth.
 * Drawn on Canvas — no Compose in WindowManager land, no recomposition loops,
 * just invalidate() when the mood changes. Dragging is handled by OverlayManager
 * through onTouch; the view itself only draws.
 */
class BubbleView(context: Context) : View(context) {

    var mood: MoodTier = MoodTier.CALM
        set(value) {
            field = value
            blinkUntil = 0L   // reset blink so a mood change always reads instantly
            invalidate()
        }

    // ---------- idle breathing ----------
    // A slow scale pulse so Chordy feels alive while sitting on screen.
    // One animator, ~2.6s cycle, running ONLY while attached; cancels on detach.
    // No 60fps-forever loop — the animator pauses itself between pulses.
    private var breathAnimator: ValueAnimator? = null

    private fun startBreathing() {
        if (breathAnimator != null) return
        breathAnimator = ValueAnimator.ofFloat(1f, 1.06f, 1f).apply {
            duration = 2600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                breathScale = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopBreathing() {
        breathAnimator?.cancel()
        breathAnimator = null
        breathScale = 1f
    }

    private var breathScale = 1f

    // ---------- reaction pop ----------
    // Quick squash-pop when a line lands — a physical "he noticed!" beat.
    private var popAnimator: ValueAnimator? = null

    fun pop() {
        post {
            popAnimator?.cancel()
            popAnimator = ValueAnimator.ofFloat(1f, 1.25f, 0.92f, 1f).apply {
                duration = 450
                interpolator = OvershootInterpolator(2.2f)
                addUpdateListener { anim ->
                    popScale = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    private var popScale = 1f

    private val bodyColor = Color.parseColor("#7FD4A8")
    private val inkColor = Color.parseColor("#101014")
    private val sadColor = Color.parseColor("#5FB988")

    // Blink cycle: 200ms closed every ~4s. Cheap life.
    private var blinkUntil = 0L
    private var nextBlinkAt = System.currentTimeMillis() + 4000

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bodyColor }
    private val sadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sadColor }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inkColor }
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f - 4f

        val now = System.currentTimeMillis()
        if (now >= nextBlinkAt) {
            blinkUntil = now + 200
            nextBlinkAt = now + 3200 + (Math.random() * 2200).toLong()
        }
        val blinking = now < blinkUntil

        // Combined scale: idle breath x reaction pop, drawn around center.
        val scale = breathScale * popScale
        if (scale != 1f) {
            canvas.save()
            canvas.scale(scale, scale, cx, cy)
        }

        // Body — the whole circle IS Chordy, no separate bubble sprite.
        canvas.drawCircle(cx, cy, r, bodyPaint)

        // Eyes: horizontal pair, flip vertical when angry (angry brows via slant)
        val eyeY = cy - r * 0.18f
        val eyeLX = cx - r * 0.32f
        val eyeRX = cx + r * 0.32f
        val eyeR = r * 0.09f

        if (blinking) {
            // little lines instead of dots
            canvas.drawLine(eyeLX - eyeR * 1.6f, eyeY, eyeLX + eyeR * 1.6f, eyeY, mouthPaint)
            canvas.drawLine(eyeRX - eyeR * 1.6f, eyeY, eyeRX + eyeR * 1.6f, eyeY, mouthPaint)
        } else {
            canvas.drawCircle(eyeLX, eyeY, eyeR, eyePaint)
            canvas.drawCircle(eyeRX, eyeY, eyeR, eyePaint)
            if (mood == MoodTier.ANGRY) {
                // slanted brows over the eyes
                canvas.drawLine(eyeLX - eyeR * 1.4f, eyeY - r * 0.16f, eyeLX + eyeR * 1.2f, eyeY - r * 0.08f, mouthPaint)
                canvas.drawLine(eyeRX + eyeR * 1.4f, eyeY - r * 0.16f, eyeRX - eyeR * 1.2f, eyeY - r * 0.08f, mouthPaint)
            }
        }

        // Mouth by mood
        val mouthY = cy + r * 0.30f
        val mouthW = r * 0.36f
        when (mood) {
            MoodTier.CALM -> {
                // small smile
                canvas.drawLine(cx - mouthW, mouthY - 2f, cx + mouthW, mouthY - 2f, mouthPaint)
            }
            MoodTier.ANXIOUS -> {
                // wavy uncertain line — a flat squiggle
                canvas.drawLine(cx - mouthW, mouthY, cx - mouthW / 3, mouthY - 6f, mouthPaint)
                canvas.drawLine(cx - mouthW / 3, mouthY - 6f, cx + mouthW / 3, mouthY + 4f, mouthPaint)
                canvas.drawLine(cx + mouthW / 3, mouthY + 4f, cx + mouthW, mouthY - 2f, mouthPaint)
            }
            MoodTier.ANGRY -> {
                // open frown — a filled arc turned down
                val rect = RectF(cx - mouthW, mouthY - 8f, cx + mouthW, mouthY + 10f)
                sadPaint.style = Paint.Style.FILL
                canvas.drawArc(rect, 180f, 180f, true, sadPaint)  // half-disc, open end up
            }
        }

        if (scale != 1f) canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startBreathing()
    }

    override fun onDetachedFromWindow() {
        stopBreathing()
        popAnimator?.cancel()
        popAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // OverlayManager attaches a touch listener externally for dragging;
        // this view is draw-only.
        return false
    }
}
