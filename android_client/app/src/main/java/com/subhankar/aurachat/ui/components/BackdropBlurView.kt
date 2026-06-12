package com.subhankar.aurachat.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * BackdropBlurView is a custom View designed to capture the content drawn behind it,
 * downsample it, apply a blur effect, and draw it as its own background.
 *
 * To prevent nested drawing crashes (IllegalStateException: Recording currently in progress),
 * it performs the backdrop capture inside the OnPreDrawListener instead of during View.draw().
 */
class BackdropBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var blurRadius = 15f
    private var downsampleFactor = 4f
    private var overlayColor = Color.parseColor("#1A111318") // 10% dark overlay

    private var mBitmapToBlur: Bitmap? = null
    private var mBlurredBitmap: Bitmap? = null
    private var mBlurringCanvas: Canvas? = null
    private var mPaint: Paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var mRectSrc = Rect()
    private var mRectDst = Rect()

    private var mIsBlurring = false
    private var mDecorView: View? = null

    private val mPreDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (visibility == VISIBLE && isShown) {
            val root = findDecorView()
            if (root != null) {
                mDecorView = root
                captureBackdrop()
            }
        }
        true
    }

    init {
        // Initialize RenderEffect if on Android 12+ (Method A)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(
                RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
                )
            )
        }
    }

    fun setBlurRadius(radius: Float) {
        val clampedRadius = radius.coerceAtLeast(1f)
        if (this.blurRadius != clampedRadius) {
            this.blurRadius = clampedRadius
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(
                    RenderEffect.createBlurEffect(
                        clampedRadius,
                        clampedRadius,
                        Shader.TileMode.CLAMP
                    )
                )
            }
            invalidate()
        }
    }

    fun setDownsampleFactor(factor: Float) {
        val clampedFactor = factor.coerceAtLeast(1f)
        if (this.downsampleFactor != clampedFactor) {
            this.downsampleFactor = clampedFactor
            releaseBitmaps()
            invalidate()
        }
    }

    fun setOverlayColor(color: Int) {
        if (this.overlayColor != color) {
            this.overlayColor = color
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val root = findDecorView()
        if (root != null) {
            mDecorView = root
            root.viewTreeObserver.addOnPreDrawListener(mPreDrawListener)
        }
    }

    override fun onDetachedFromWindow() {
        mDecorView?.viewTreeObserver?.removeOnPreDrawListener(mPreDrawListener)
        mDecorView = null
        releaseBitmaps()
        super.onDetachedFromWindow()
    }

    private fun findDecorView(): View? {
        var view: View? = this
        while (view?.parent is View) {
            view = view.parent as View
        }
        return view
    }

    private fun releaseBitmaps() {
        mBitmapToBlur?.recycle()
        mBitmapToBlur = null
        mBlurredBitmap?.recycle()
        mBlurredBitmap = null
        mBlurringCanvas = null
    }

    private fun prepare(): Boolean {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return false

        val scaledW = (w / downsampleFactor).toInt().coerceAtLeast(1)
        val scaledH = (h / downsampleFactor).toInt().coerceAtLeast(1)

        if (mBitmapToBlur == null || mBitmapToBlur?.width != scaledW || mBitmapToBlur?.height != scaledH) {
            releaseBitmaps()
            try {
                mBitmapToBlur = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
                mBlurredBitmap = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
                mBlurringCanvas = Canvas(mBitmapToBlur!!)
            } catch (e: OutOfMemoryError) {
                return false
            }
        }
        return true
    }

    private fun captureBackdrop() {
        val decor = mDecorView ?: return
        if (!prepare()) return

        mIsBlurring = true

        // Calculate position relative to the root decor view
        val locations = IntArray(2)
        getLocationInWindow(locations)
        val decorLocations = IntArray(2)
        decor.getLocationInWindow(decorLocations)

        val relativeX = locations[0] - decorLocations[0]
        val relativeY = locations[1] - decorLocations[1]

        // Clear the temporary capture canvas
        mBitmapToBlur?.eraseColor(Color.TRANSPARENT)

        val saveCount = mBlurringCanvas?.save() ?: 0
        // Scale down and translate the capture canvas to only draw the region directly behind us
        mBlurringCanvas?.scale(1f / downsampleFactor, 1f / downsampleFactor)
        mBlurringCanvas?.translate(-relativeX.toFloat(), -relativeY.toFloat())

        try {
            decor.draw(mBlurringCanvas!!)
        } catch (e: Exception) {
            // Ignore drawing exceptions during system transitions
        } finally {
            mBlurringCanvas?.restoreToCount(saveCount)
            mIsBlurring = false
        }

        // Apply blur
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Method A: RenderEffect handles the blur natively on the GPU.
            mBitmapToBlur?.let { src ->
                mBlurredBitmap?.let { dst ->
                    val c = Canvas(dst)
                    c.drawBitmap(src, 0f, 0f, null)
                }
            }
        } else {
            // Method B: High-performance CPU Stack Blur fallback for API < 31
            val src = mBitmapToBlur
            val dst = mBlurredBitmap
            if (src != null && dst != null) {
                blurBitmapCpu(src, dst, (blurRadius / downsampleFactor).toInt().coerceAtLeast(1))
            }
        }

        invalidate()
    }

    override fun draw(canvas: Canvas) {
        if (mIsBlurring) {
            // Do not draw ourselves (background/blur/overlay) when capturing the snapshot!
            return
        }

        if (mBlurredBitmap != null) {
            mRectSrc.set(0, 0, mBlurredBitmap!!.width, mBlurredBitmap!!.height)
            mRectDst.set(0, 0, width, height)
            canvas.drawBitmap(mBlurredBitmap!!, mRectSrc, mRectDst, mPaint)
        }

        // Draw overlay tint to give a frosting effect
        canvas.drawColor(overlayColor)

        super.draw(canvas)
    }

    /**
     * Highly optimized, thread-safe Stack Blur CPU algorithm.
     * Blurs sentBitmap and writes output into helperBitmap.
     */
    private fun blurBitmapCpu(sentBitmap: Bitmap, helperBitmap: Bitmap, radius: Int) {
        val w = sentBitmap.width
        val h = sentBitmap.height
        val pix = IntArray(w * h)
        sentBitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int

        val vmin = IntArray(w.coerceAtLeast(h))
        val dv = IntArray(256 * div)
        for (i in 0 until 256 * div) {
            dv[i] = i / div
        }

        yw = 0
        yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (y in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (i in -radius..radius) {
                p = pix[yi + i.coerceIn(0, wm)]
                sir = stack[i + radius]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (x in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = (x + radius + 1).coerceAtMost(wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        for (x in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (i in -radius..radius) {
                yi = 0.coerceAtLeast(yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - Math.abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                yp += w
            }
            yi = x
            stackpointer = radius
            for (y in 0 until h) {
                pix[yi] = -0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = (y + radius + 1).coerceAtMost(hm) * w
                }
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        helperBitmap.setPixels(pix, 0, w, 0, 0, w, h)
    }
}

/**
 * BackdropBlur Compose wrapper function to embed BackdropBlurView inside Compose hierarchy.
 */
@Composable
fun BackdropBlur(
    modifier: Modifier = Modifier,
    blurRadius: Float = 15f,
    downsampleFactor: Float = 4f,
    overlayColor: Int = Color.parseColor("#12111318") // very subtle dark tint (~7% opacity)
) {
    AndroidView(
        factory = { context ->
            BackdropBlurView(context).apply {
                setBlurRadius(blurRadius)
                setDownsampleFactor(downsampleFactor)
                setOverlayColor(overlayColor)
            }
        },
        update = { view ->
            view.setBlurRadius(blurRadius)
            view.setDownsampleFactor(downsampleFactor)
            view.setOverlayColor(overlayColor)
        },
        modifier = modifier
    )
}
