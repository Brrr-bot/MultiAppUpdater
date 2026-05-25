package com.homehub.dashboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class StarfieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private data class Star(
        val x: Float,
        val y: Float,
        val radius: Float,
        val alpha: Int,
        val twinkleOffset: Float
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var stars: List<Star> = emptyList()
    private val startTime = System.currentTimeMillis()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val rng = Random(0xC0FFEE)
        stars = (0 until 180).map {
            Star(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                radius = rng.nextFloat() * 1.8f + 0.4f,
                alpha = (rng.nextFloat() * 160 + 80).toInt(),
                twinkleOffset = rng.nextFloat() * Math.PI.toFloat() * 2f
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#050510"))
        val t = (System.currentTimeMillis() - startTime) / 1000f
        for (star in stars) {
            val twinkle = Math.sin((t * 0.8f + star.twinkleOffset).toDouble()).toFloat() * 0.3f + 0.7f
            paint.alpha = (star.alpha * twinkle).toInt().coerceIn(0, 255)
            paint.color = Color.WHITE
            canvas.drawCircle(star.x, star.y, star.radius, paint)
        }
        postInvalidateOnAnimation()
    }
}
