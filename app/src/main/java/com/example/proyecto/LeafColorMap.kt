package com.example.proyecto

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * EXPERIMENTO (rama `experimento`). Apagarlo aqui deja la app exactamente
 * como la version entregada: no se calcula ni se muestra el mapa.
 */
const val MAPA_COLOR_ACTIVO = true

/**
 * LeafColorMap -- mapa de color de la hoja, MODO PAPEL BLANCO.
 *
 * Puerto a Kotlin de ~/plagascan/mapa_papel.py (mismos umbrales, sin ajustes
 * nuevos). Marca zonas de la hoja cuyo color se aleja del verde de esa misma
 * hoja: descoloridas (palidas o secas), amarillentas y cafes/rojizas.
 *
 * - Es INFORMATIVO, no un diagnostico: no dice la causa.
 * - Es INDEPENDIENTE del clasificador del pulgon: recibe la foto original,
 *   no toca LeafSegmenter ni PlagaScanClassifier y se calcula DESPUES de que
 *   el resultado del pulgon ya se mostro.
 * - Solo se dibuja si alrededor de la hoja hay papel blanco (ver PAPEL_*).
 *
 * Limitacion medida (12 oct 2026): no detecta puntos pequenos oscuros,
 * rojizos o cafes de pocos milimetros.
 */
class LeafColorMap {

    data class Resultado(
        /** false: no se reconocio papel blanco alrededor de la hoja; no hay mapa. */
        val sobrePapel: Boolean,
        val pctDescolorida: Float = 0f,
        val pctAmarillenta: Float = 0f,
        val pctCafe: Float = 0f,
        /** Fraccion (0-1) de la hoja que se pudo revisar; el resto estaba en sombra. */
        val evaluada: Float = 0f,
        /** Foto reducida con las zonas marcadas (null si no hay mapa). */
        val imagen: Bitmap? = null
    )

    companion object {
        private const val TAG = "MapaColor"
        private const val WORK_SIZE = 640.0
        private const val HOJA_H_MIN = 22.0
        private const val HOJA_H_MAX = 95.0
        private const val MIN_AREA_RATIO = 0.03
        private const val BORDE_RATIO = 0.015
        private const val SAT_BAJA_REL = 0.60f
        private const val CAFE_H_MAX = 92f
        private const val AMARILLA_DH = 9f
        private const val AMARILLA_SAT_REL = 0.80f
        private const val L_MIN = 35
        private const val SOMBRA_REL = 0.75f
        private const val APERTURA_PX = 7.0
        private const val MIN_MANCHA_PX = 12

        // Comprobacion de papel blanco: fuera de la hoja, al menos 70 % de los
        // pixeles deben ser claros y grises (S < 50, V > 90 en HSV de OpenCV).
        // Medido en Python: fotos con flash sobre papel 0.96-1.00 (64 fotos);
        // fotos de campo de frijol 0.01-0.64 (89 fotos).
        private const val PAPEL_S_MAX = 50
        private const val PAPEL_V_MIN = 90
        private const val PAPEL_FRACCION_MIN = 0.70

        // Colores del mapa, en BGR (como en mapa_papel.py)
        private val BGR_DESCOLORIDA = intArrayOf(255, 0, 255) // magenta
        private val BGR_AMARILLENTA = intArrayOf(0, 215, 255) // amarillo
        private val BGR_CAFE = intArrayOf(255, 140, 0)        // azul claro
    }

    fun analizar(input: Bitmap): Resultado {
        val inicio = System.currentTimeMillis()
        val mats = mutableListOf<Mat>()
        fun nueva(): Mat = Mat().also { mats.add(it) }
        fun <T : Mat> guarda(x: T): T = x.also { mats.add(it) }
        try {
            // --- Foto en BGR, reducida a 640 px de lado mayor ---
            val rgba = nueva()
            Utils.bitmapToMat(input, rgba)
            val bgrFull = nueva()
            Imgproc.cvtColor(rgba, bgrFull, Imgproc.COLOR_RGBA2BGR)
            val s = WORK_SIZE / max(bgrFull.cols(), bgrFull.rows())
            val img = if (s < 1.0) {
                nueva().also { Imgproc.resize(bgrFull, it, Size(), s, s, Imgproc.INTER_AREA) }
            } else bgrFull
            val rows = img.rows()
            val cols = img.cols()
            val n = rows * cols

            // --- Region de la hoja: saturada y de tono verde/amarillo ---
            val hsv = nueva()
            Imgproc.cvtColor(img, hsv, Imgproc.COLOR_BGR2HSV)
            val canalesHsv = ArrayList<Mat>()
            Core.split(hsv, canalesHsv)
            mats.addAll(canalesHsv)
            val hCanal = canalesHsv[0]
            val sCanal = canalesHsv[1]
            val vCanal = canalesHsv[2]
            val m = nueva()
            Imgproc.threshold(sCanal, m, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            val tonoHoja = nueva()
            Core.inRange(hCanal, Scalar(HOJA_H_MIN), Scalar(HOJA_H_MAX), tonoHoja)
            Core.bitwise_and(m, tonoHoja, m)
            val k5 = guarda(Mat.ones(5, 5, CvType.CV_8U))
            Imgproc.morphologyEx(m, m, Imgproc.MORPH_OPEN, k5)
            val contornos = ArrayList<MatOfPoint>()
            val jerarquia = nueva()
            Imgproc.findContours(m, contornos, jerarquia, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            mats.addAll(contornos)
            val mayor = contornos.maxByOrNull { Imgproc.contourArea(it) }
                ?: return Resultado(sobrePapel = false)
            val ratio = Imgproc.contourArea(mayor) / n
            if (ratio < MIN_AREA_RATIO) return Resultado(sobrePapel = false)
            val region = guarda(Mat.zeros(rows, cols, CvType.CV_8U))
            Imgproc.drawContours(region, listOf(mayor), -1, Scalar(255.0), -1)
            val e = max(3, (BORDE_RATIO * max(rows, cols)).roundToInt()).toDouble()
            val kBorde = guarda(Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(e, e)))
            Imgproc.erode(region, region, kBorde)

            // --- Fuera de la hoja: debe ser papel blanco ---
            val k25 = guarda(Mat.ones(25, 25, CvType.CV_8U))
            val regionDil = nueva()
            Imgproc.dilate(region, regionDil, k25)
            val regionArr = bytes(region)
            val dilArr = bytes(regionDil)
            val sArr = bytes(sCanal)
            val vArr = bytes(vCanal)
            var nFuera = 0
            var nPapel = 0
            for (i in 0 until n) {
                if (u(dilArr[i]) == 0) {
                    nFuera++
                    if (u(sArr[i]) < PAPEL_S_MAX && u(vArr[i]) > PAPEL_V_MIN) nPapel++
                }
            }
            val fraccionPapel = if (nFuera == 0) 0.0 else nPapel.toDouble() / nFuera
            if (fraccionPapel < PAPEL_FRACCION_MIN) {
                Log.d(TAG, "Sin papel blanco (fraccion=${f2(fraccionPapel)}); no se dibuja el mapa")
                return Resultado(sobrePapel = false)
            }

            // --- Color en Lab, con balance de blancos tomado del papel ---
            val lab = nueva()
            Imgproc.cvtColor(img, lab, Imgproc.COLOR_BGR2Lab)
            val canalesLab = ArrayList<Mat>()
            Core.split(lab, canalesLab)
            mats.addAll(canalesLab)
            val lArr = bytes(canalesLab[0])
            val aArr = bytes(canalesLab[1])
            val bArr = bytes(canalesLab[2])

            val lFuera = FloatArray(nFuera)
            run {
                var j = 0
                for (i in 0 until n) if (u(dilArr[i]) == 0) lFuera[j++] = u(lArr[i]).toFloat()
            }
            val p70 = percentil(lFuera, nFuera, 70.0)
            val p90 = percentil(lFuera, nFuera, 90.0)
            val aPap = FloatArray(nFuera)
            val bPap = FloatArray(nFuera)
            var nPap = 0
            for (i in 0 until n) {
                if (u(dilArr[i]) == 0 && u(lArr[i]) > p70) {
                    aPap[nPap] = (u(aArr[i]) - 128).toFloat()
                    bPap[nPap] = (u(bArr[i]) - 128).toFloat()
                    nPap++
                }
            }
            val aRef = if (nPap > 0) percentil(aPap, nPap, 50.0) else 0f
            val bRef = if (nPap > 0) percentil(bPap, nPap, 50.0) else 0f

            // --- Luz que llega a la hoja, medida en el papel de alrededor ---
            // (inpainting a 1/4 de resolucion, igual que en Python)
            val k15 = guarda(Mat.ones(15, 15, CvType.CV_8U))
            val tapa = nueva()
            Imgproc.dilate(region, tapa, k15)
            val chico = Size((cols / 4).toDouble(), (rows / 4).toDouble())
            val lChico = nueva()
            Imgproc.resize(canalesLab[0], lChico, chico, 0.0, 0.0, Imgproc.INTER_AREA)
            val tapaChica = nueva()
            Imgproc.resize(tapa, tapaChica, chico, 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.threshold(tapaChica, tapaChica, 0.0, 1.0, Imgproc.THRESH_BINARY)
            val luzChica = nueva()
            Photo.inpaint(lChico, tapaChica, luzChica, 5.0, Photo.INPAINT_TELEA)
            val luzF = nueva()
            luzChica.convertTo(luzF, CvType.CV_32F)
            Imgproc.GaussianBlur(luzF, luzF, Size(0.0, 0.0), 2.5)
            val luz = nueva()
            Imgproc.resize(luzF, luz, Size(cols.toDouble(), rows.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
            val luzArr = FloatArray(n).also { luz.get(0, 0, it) }

            // --- Que parte de la hoja se evalua ---
            val hoja = BooleanArray(n) { u(regionArr[it]) > 0 }
            val en = BooleanArray(n)
            val h = FloatArray(n)
            val c = FloatArray(n)
            val sat = FloatArray(n)
            var nHoja = 0
            var nEn = 0
            val limiteLuz = SOMBRA_REL * p90
            for (i in 0 until n) {
                if (!hoja[i]) continue
                nHoja++
                val l = u(lArr[i])
                if (l <= L_MIN || luzArr[i] < limiteLuz) continue
                en[i] = true
                nEn++
                val a = (u(aArr[i]) - 128) - aRef
                val b = (u(bArr[i]) - 128) - bRef
                h[i] = Math.toDegrees(atan2(b.toDouble(), a.toDouble())).toFloat()
                c[i] = hypot(a, b)
                sat[i] = c[i] / max(l, 20)
            }
            val evaluada = nEn.toFloat() / max(1, nHoja)
            if (nEn == 0) return Resultado(sobrePapel = true, evaluada = 0f)

            val satEn = FloatArray(nEn)
            val hEn = FloatArray(nEn)
            run {
                var j = 0
                for (i in 0 until n) if (en[i]) { satEn[j] = sat[i]; hEn[j] = h[i]; j++ }
            }
            val refSat = percentil(satEn, nEn, 75.0)
            val refH = percentil(hEn, nEn, 75.0)

            // --- Reglas de color (mismas que mapa_papel.py) ---
            val descolorida = ByteArray(n)
            val amarillenta = ByteArray(n)
            val cafe = ByteArray(n)
            for (i in 0 until n) {
                if (!en[i]) continue
                val baja = sat[i] < SAT_BAJA_REL * refSat
                val esCafe = h[i] < CAFE_H_MAX && c[i] > 8f
                if (esCafe) cafe[i] = 1
                if (baja && !esCafe) descolorida[i] = 1
                if (!baja && !esCafe && h[i] < refH - AMARILLA_DH && sat[i] >= AMARILLA_SAT_REL * refSat) {
                    amarillenta[i] = 1
                }
            }
            val kAbrir = guarda(Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(APERTURA_PX, APERTURA_PX)))
            val mDesc = limpiar(abrir(descolorida, rows, cols, kAbrir, mats), mats)
            val mAmar = limpiar(abrir(amarillenta, rows, cols, kAbrir, mats), mats)
            val mCafe = limpiar(abrir(cafe, rows, cols, kAbrir, mats), mats)
            val pct = { mk: Mat -> 100f * Core.countNonZero(mk) / max(1, nEn) }

            // --- Dibujo: fuera de la hoja en gris oscuro, sombra en gris, zonas en color ---
            val imgArr = bytes(img)
            val gris = nueva()
            Imgproc.cvtColor(img, gris, Imgproc.COLOR_BGR2GRAY)
            val grisArr = bytes(gris)
            for (i in 0 until n) {
                val p = i * 3
                if (!hoja[i]) {
                    val g = (u(grisArr[i]) * 0.5).toInt().toByte()
                    imgArr[p] = g; imgArr[p + 1] = g; imgArr[p + 2] = g
                } else if (!en[i]) {
                    for (k in 0..2) imgArr[p + k] = (0.5 * u(imgArr[p + k]) + 45.0).toInt().toByte()
                }
            }
            val salida = guarda(Mat(rows, cols, CvType.CV_8UC3))
            for ((mk, bgr) in listOf(mDesc to BGR_DESCOLORIDA, mAmar to BGR_AMARILLENTA, mCafe to BGR_CAFE)) {
                val mkArr = bytes(mk)
                for (i in 0 until n) {
                    if (u(mkArr[i]) == 0) continue
                    val p = i * 3
                    for (k in 0..2) imgArr[p + k] = (0.35 * u(imgArr[p + k]) + 0.65 * bgr[k]).toInt().toByte()
                }
            }
            salida.put(0, 0, imgArr)
            val k7 = guarda(Mat.ones(7, 7, CvType.CV_8U))
            for ((mk, bgr) in listOf(mDesc to BGR_DESCOLORIDA, mAmar to BGR_AMARILLENTA, mCafe to BGR_CAFE)) {
                val dil = nueva()
                Imgproc.dilate(mk, dil, k7)
                val cs = ArrayList<MatOfPoint>()
                val hier = nueva()
                Imgproc.findContours(dil, cs, hier, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                mats.addAll(cs)
                Imgproc.drawContours(salida, cs, -1, Scalar(bgr[0].toDouble(), bgr[1].toDouble(), bgr[2].toDouble()), 2)
            }
            val salidaRgba = nueva()
            Imgproc.cvtColor(salida, salidaRgba, Imgproc.COLOR_BGR2RGBA)
            val bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(salidaRgba, bitmap)

            val r = Resultado(
                sobrePapel = true,
                pctDescolorida = pct(mDesc),
                pctAmarillenta = pct(mAmar),
                pctCafe = pct(mCafe),
                evaluada = evaluada,
                imagen = bitmap
            )
            Log.d(
                TAG,
                "Mapa: papel=${f2(fraccionPapel)} evaluada=${f2(evaluada)} " +
                    "descolorida=${f2(r.pctDescolorida)} amarillenta=${f2(r.pctAmarillenta)} " +
                    "cafe=${f2(r.pctCafe)} tiempo=${System.currentTimeMillis() - inicio} ms"
            )
            return r
        } finally {
            mats.forEach { it.release() }
        }
    }

    // ---------------- utilidades ----------------

    private fun u(b: Byte): Int = b.toInt() and 0xFF

    private fun f2(x: Number): String = String.format(Locale.US, "%.2f", x.toDouble())

    /** Copia los pixeles de un Mat de 8 bits (1 o 3 canales) a un arreglo. */
    private fun bytes(mat: Mat): ByteArray {
        val arr = ByteArray((mat.total() * mat.channels()).toInt())
        val continuo = if (mat.isContinuous) mat else mat.clone()
        continuo.get(0, 0, arr)
        if (continuo !== mat) continuo.release()
        return arr
    }

    /** Percentil con interpolacion lineal (igual que numpy.percentile). */
    private fun percentil(valores: FloatArray, cuantos: Int, q: Double): Float {
        val v = valores.copyOf(cuantos)
        v.sort()
        val pos = q / 100.0 * (cuantos - 1)
        val lo = floor(pos).toInt()
        val hi = ceil(pos).toInt()
        return (v[lo] + (v[hi] - v[lo]) * (pos - lo)).toFloat()
    }

    /** Apertura morfologica: borra lineas finas (nervaduras) y deja manchas. */
    private fun abrir(mascara: ByteArray, rows: Int, cols: Int, kernel: Mat, mats: MutableList<Mat>): Mat {
        val m = Mat(rows, cols, CvType.CV_8U).also { mats.add(it) }
        m.put(0, 0, mascara)
        Imgproc.morphologyEx(m, m, Imgproc.MORPH_OPEN, kernel)
        return m
    }

    /** Quita manchas de menos de MIN_MANCHA_PX pixeles. Devuelve mascara 0/255. */
    private fun limpiar(m: Mat, mats: MutableList<Mat>): Mat {
        val etiquetas = Mat().also { mats.add(it) }
        val stats = Mat().also { mats.add(it) }
        val centros = Mat().also { mats.add(it) }
        val total = Imgproc.connectedComponentsWithStats(m, etiquetas, stats, centros, 8, CvType.CV_32S)
        val st = IntArray(total * 5).also { stats.get(0, 0, it) }
        val conservar = BooleanArray(total) { it > 0 && st[it * 5 + Imgproc.CC_STAT_AREA] >= MIN_MANCHA_PX }
        val lab = IntArray(m.rows() * m.cols()).also { etiquetas.get(0, 0, it) }
        val out = ByteArray(lab.size) { if (conservar[lab[it]]) 255.toByte() else 0.toByte() }
        return Mat(m.rows(), m.cols(), CvType.CV_8U).also { mats.add(it); it.put(0, 0, out) }
    }
}
