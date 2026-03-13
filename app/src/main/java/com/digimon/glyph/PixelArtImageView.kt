package com.digimon.glyph

import android.graphics.Bitmap
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.roundToInt

class PixelArtImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val pixelPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }
    private val dstRect = Rect()
    private var processedBitmap: Bitmap? = null

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        processedBitmap = drawable?.let { buildProcessedBitmap(it) }
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = processedBitmap ?: return
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) return

        val scale = minOf(
            contentWidth.toFloat() / bitmap.width.toFloat(),
            contentHeight.toFloat() / bitmap.height.toFloat()
        )
        val drawWidth = (bitmap.width * scale).roundToInt()
        val drawHeight = (bitmap.height * scale).roundToInt()
        val left = paddingLeft + ((contentWidth - drawWidth) / 2)
        val top = paddingTop + ((contentHeight - drawHeight) / 2)
        dstRect.set(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, null, dstRect, pixelPaint)
    }

    private fun buildProcessedBitmap(drawable: Drawable): Bitmap {
        val source = (drawable as? BitmapDrawable)?.bitmap ?: renderDrawable(drawable)
        val bg = source.getPixel(0, 0)
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val yellow = Color.argb(255, 0xF2, 0xFF, 0x00)

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val pixel = source.getPixel(x, y)
                val isBackground =
                    Color.alpha(pixel) == Color.alpha(bg) &&
                    Color.red(pixel) == Color.red(bg) &&
                    Color.green(pixel) == Color.green(bg) &&
                    Color.blue(pixel) == Color.blue(bg)

                if (isBackground || Color.alpha(pixel) == 0) {
                    output.setPixel(x, y, Color.TRANSPARENT)
                } else {
                    output.setPixel(
                        x,
                        y,
                        Color.argb(Color.alpha(pixel), Color.red(yellow), Color.green(yellow), Color.blue(yellow))
                    )
                }
            }
        }
        return output
    }

    private fun renderDrawable(drawable: Drawable): Bitmap {
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }
}
