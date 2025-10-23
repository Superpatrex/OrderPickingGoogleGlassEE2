package com.example.barscanner.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.barcode.common.Barcode

class BarcodeBoxView(context: Context, attrs: AttributeSet?) : View(context, attrs) 
{
    private val barcodes = mutableListOf<Barcode>()
    private var sourceWidth: Int = 1
    private var sourceHeight: Int = 1

    private val boxPaint = Paint().apply{
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6.0f
    }

    private val textPaint = Paint().apply{
        color = Color.RED
        textSize = 40.0f
    }

    fun updateView(barcodes: List<Barcode>, imageWidth: Int, imageHeight: Int) 
    {
        this.barcodes.clear()
        this.barcodes.addAll(barcodes)
        this.sourceWidth = imageWidth
        this.sourceHeight = imageHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) 
    {
        super.onDraw(canvas)
        
        if (barcodes.isEmpty()) 
        {
            return
        }

        val scaleX = width.toFloat() / sourceWidth.toFloat()
        val scaleY = height.toFloat() / sourceHeight.toFloat()

        for (barcode in barcodes) 
        {
            val box = barcode.boundingBox ?: continue

            val scaledBox = RectF(box.left * scaleX, box.top * scaleY, box.right * scaleX, box.bottom * scaleY)

            canvas.drawRect(scaledBox, boxPaint)
            
            canvas.drawText(barcode.rawValue.orEmpty(), scaledBox.left, scaledBox.top - 10, textPaint)
        }
    }
}