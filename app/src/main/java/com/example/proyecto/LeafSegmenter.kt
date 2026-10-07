package com.example.proyecto

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * LeafSegmenter
 *
 * Puerto a Kotlin de preprocess_opencv.py (Módulo 2): segmenta la hoja del
 * fondo, recorta al contorno más grande, y normaliza a 224x224 con padding
 * reflejado — EXACTAMENTE el mismo método que usó build_dataset.py para
 * entrenar el modelo, para que el preprocesamiento en producción coincida
 * con el preprocesamiento de entrenamiento.
 */
class LeafSegmenter {

    companion object {
        private const val TAG = "LeafSegmenter"
        private const val TARGET_SIZE = 224
        private const val HUE_MIN = 25.0
        private const val HUE_MAX = 95.0
        private const val SAT_MIN = 30.0
        private const val VAL_MIN = 30.0
        private const val MIN_AREA_RATIO = 0.15
        private const val MARGIN_RATIO = 0.08
    }

    data class SegmentResult(
        val bitmap: Bitmap,
        val found: Boolean // true = se detectó y recortó la hoja, false = fallback (imagen completa)
    )

    fun segment(input: Bitmap): SegmentResult {
        val srcMat = Mat()
        Utils.bitmapToMat(input, srcMat) // RGBA

        val rgbMat = Mat()
        Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)

        val hsvMat = Mat()
        Imgproc.cvtColor(rgbMat, hsvMat, Imgproc.COLOR_RGB2HSV)

        val mask = Mat()
        Core.inRange(
            hsvMat,
            Scalar(HUE_MIN, SAT_MIN, VAL_MIN),
            Scalar(HUE_MAX, 255.0, 255.0),
            mask
        )

        // Limpieza morfológica, igual que en Python: cerrar huecos, quitar ruido
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0))

        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel, org.opencv.core.Point(-1.0, -1.0), 2)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel, org.opencv.core.Point(-1.0, -1.0), 1)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val totalArea = (rgbMat.rows() * rgbMat.cols()).toDouble()
        var largest: MatOfPoint? = null
        var largestArea = 0.0
        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area > largestArea) {
                largestArea = area
                largest = c
            }
        }

        kernel.release()

        // IMPORTANTE: "largest" es una referencia al mismo objeto MatOfPoint
        // que ya vive dentro de "contours" (no es una copia). Por eso el
        // boundingRect() debe calcularse ANTES de liberar los contornos;
        // liberar primero deja "largest" con memoria nativa inválida y
        // provoca el cv::Exception en pointSetBoundingRect.
        val found = largest != null && (largestArea / totalArea) >= MIN_AREA_RATIO
        val croppedMat: Mat

        if (found) {
            val rect = Imgproc.boundingRect(largest)
            for (c in contours) { c.release() }

            val marginX = (rect.width * MARGIN_RATIO).toInt()
            val marginY = (rect.height * MARGIN_RATIO).toInt()
            val x0 = maxOf(0, rect.x - marginX)
            val y0 = maxOf(0, rect.y - marginY)
            val x1 = minOf(rgbMat.cols(), rect.x + rect.width + marginX)
            val y1 = minOf(rgbMat.rows(), rect.y + rect.height + marginY)
            val safeRect = Rect(x0, y0, x1 - x0, y1 - y0)
            croppedMat = Mat(rgbMat, safeRect).clone()
        } else {
            for (c in contours) { c.release() }
            croppedMat = rgbMat.clone()
        }

        val finalMat = letterboxResize(croppedMat)
        val outputBitmap = Bitmap.createBitmap(finalMat.cols(), finalMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(finalMat, outputBitmap)

        Log.d(TAG, "Segmentación: found=$found, area_ratio=${largestArea / totalArea}")

        srcMat.release(); rgbMat.release(); hsvMat.release(); mask.release()
        hierarchy.release(); croppedMat.release(); finalMat.release()

        return SegmentResult(outputBitmap, found)
    }

    private fun letterboxResize(src: Mat, size: Int = TARGET_SIZE): Mat {
        val h = src.rows()
        val w = src.cols()
        val scale = size.toDouble() / maxOf(h, w)
        val newH = Math.round(h * scale).toInt()
        val newW = Math.round(w * scale).toInt()

        val resized = Mat()
        Imgproc.resize(src, resized, Size(newW.toDouble(), newH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)

        val top = (size - newH) / 2
        val bottom = size - newH - top
        val left = (size - newW) / 2
        val right = size - newW - left

        val bordered = Mat()
        Core.copyMakeBorder(resized, bordered, top, bottom, left, right, Core.BORDER_REFLECT)
        resized.release()
        return bordered
    }
}
