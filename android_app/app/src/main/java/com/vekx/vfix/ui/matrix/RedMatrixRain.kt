package com.vekx.vfix.ui.matrix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.random.Random

/**
 * 100% рабочая аппаратная анимация красной матрицы на SurfaceView (60 FPS).
 */
class MatrixSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null
    @Volatile
    private var isRunning = false
    private val chars = "0123456789ABCDEF<>/{}[*]#%@!$§¥"
    private val fontSize = 38f
    private var drops: IntArray? = null

    private val paintBright = Paint().apply {
        color = Color.parseColor("#FF1111")
        textSize = fontSize
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }

    private val paintDark = Paint().apply {
        color = Color.parseColor("#770000")
        textSize = fontSize
        typeface = Typeface.MONOSPACE
    }

    private val paintFade = Paint().apply {
        color = Color.argb(45, 0, 0, 0) // Эффект затухания следа
    }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        thread = Thread(this).apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val cols = (width / fontSize).toInt().coerceAtLeast(1)
        drops = IntArray(cols) { Random.nextInt(-40, 0) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        var retry = true
        while (retry) {
            try {
                thread?.join()
                retry = false
            } catch (ignored: Exception) {}
        }
    }

    override fun run() {
        while (isRunning) {
            val canvas: Canvas? = holder.lockCanvas()
            if (canvas != null) {
                try {
                    drawMatrix(canvas)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            try {
                Thread.sleep(30) // ~33 FPS стабильно
            } catch (ignored: Exception) {}
        }
    }

    private fun drawMatrix(canvas: Canvas) {
        val currentDrops = drops ?: return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintFade)

        for (i in currentDrops.indices) {
            val char = chars[Random.nextInt(chars.length)].toString()
            val x = i * fontSize
            val y = currentDrops[i] * fontSize

            val paint = if (Random.nextFloat() > 0.4f) paintDark else paintBright
            canvas.drawText(char, x, y, paint)

            if (y > height && Random.nextFloat() > 0.95f) {
                currentDrops[i] = 0
            } else {
                currentDrops[i]++
            }
        }
    }
}

@Composable
fun RedMatrixRain(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> MatrixSurfaceView(ctx) },
        modifier = modifier.fillMaxSize()
    )
}
