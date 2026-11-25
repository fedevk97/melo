package `in`.melo.ui.launch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.SurfaceControl
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import `in`.melo.R
import kotlin.math.hypot

/** Holds the system splash until the M has risen; see drawable/splash_logo. */
object LaunchSplash {
    /** The M rising (1070ms) and a beat with it whole before the splash lets go. */
    const val ANIMATION_MS = 1_250L

    /** How long the splash fades when animations are off. */
    const val EXIT_FADE_MS = 160L

    /** How long the splash takes to open into the app; see [MeloLaunchIris]. */
    const val EXIT_IRIS_MS = 700L
}

/**
 * The splash leaving: the black screen opens from the M outward on a soft round edge,
 * like an iris, while the M grows a little and fades, and the app shows through.
 *
 * A view of its own laid over the window, since the system's splash view can be faded and
 * moved but not masked. It takes over from the splash with the M whole, drawn from
 * drawable/splash_logo_lit, the splash animation's last frame, in the colour of the app icon. Touches pass through.
 *
 * The hand-over from the system splash (holding its icon surface until this view's first
 * frame, and the 288dp icon box) is held for the
 * iris to take over.
 */
class MeloLaunchIris(context: Context) : View(context) {
    private val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val icon = `in`.melo.data.AppIcon.current(context)
    private var logo: Drawable? = context.getDrawable(litLogo(icon))
    private val logoBox = Rect()
    private var progress = 0f

    /** Off while the splash's own logo is still showing on top of this view. */
    private var drawsLogo = true
    private var onFirstDraw: (() -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (logoBox.isEmpty) centreLogoOn(width / 2, height / 2)
        val cx = logoBox.exactCenterX()
        val cy = logoBox.exactCenterY()
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawColor(SPLASH_BLACK)
        if (drawsLogo) logo?.let {
            val grow = 1f + LOGO_GROW * progress
            it.bounds = logoBox
            it.alpha = ((1f - progress).coerceIn(0f, 1f) * 255).toInt()
            canvas.save()
            canvas.scale(grow, grow, cx, cy)
            it.draw(canvas)
            canvas.restore()
        }
        // Clear inside the iris and keep what is past its soft edge. The edge grows from the
        // M's centre to past the farthest corner, so the sweep starts whole and ends clear.
        val feather = FEATHER_DP * resources.displayMetrics.density
        val far = hypot(maxOf(cx, w - cx), maxOf(cy, h - cy))
        val outer = progress * (far + feather)
        if (outer > 0f) {
            val inner = (outer - feather).coerceAtLeast(0f) / outer
            mask.shader = RadialGradient(
                cx, cy, outer,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.BLACK),
                floatArrayOf(0f, inner, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w, h, mask)
        }
        canvas.restoreToCount(layer)
        firstDrawn()
    }

    /**
     * The splash draws its icon in a 288dp box whatever size its icon view says it is, so
     * only the centre is taken from the view.
     */
    private fun centreLogoOn(cx: Int, cy: Int) {
        val half = (LOGO_DP * resources.displayMetrics.density / 2f).toInt()
        logoBox.set(cx - half, cy - half, cx + half, cy + half)
    }

    /**
     * Lays the iris over [root], runs it once, and takes it away again. [icon] is the
     * splash's logo view, when the system hands one over: the M is drawn where that was.
     */
    fun runOver(root: ViewGroup, icon: View?, onCovered: () -> Unit) {
        if (icon != null && icon.width > 0 && icon.height > 0) {
            val at = IntArray(2)
            val origin = IntArray(2)
            icon.getLocationInWindow(at)
            root.getLocationInWindow(origin)
            centreLogoOn(at[0] - origin[0] + icon.width / 2, at[1] - origin[1] + icon.height / 2)
        }
        root.addView(
            this,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        // The splash's animated logo is on a surface of its own above the whole window.
        // Drawn twice over, the faint dots would flash brighter; taken away a frame early,
        // the logo would blink. So where the system allows it this view starts as plain
        // black under that surface and hides it in the very frame its own M is drawn.
        val iconSurface = iconSurfaceOf(icon)
        drawsLogo = iconSurface == null
        // A splash with no logo (a plain black page) gets the M rising here,
        // so a launch never shows only black before the app opens.
        val drawsItself = icon == null && playLogoHere()
        val handOver = {
            postOnAnimation {
                postOnAnimation {
                    onCovered()
                    if (drawsItself) postDelayed({ open(root) }, LaunchSplash.ANIMATION_MS) else open(root)
                }
            }
        }
        onFirstDraw = {
            if (iconSurface == null) {
                handOver()
            } else {
                postOnAnimation {
                    drawsLogo = true
                    hideWithNextDraw(iconSurface)
                    invalidate()
                    handOver()
                }
            }
        }
        invalidate()
        // Whatever happens to the first draw, the splash is not left up.
        postDelayed({ firstDrawn() }, FIRST_DRAW_WAIT_MS)
    }

    /** Swaps the still M for drawable/splash_logo and starts it; false if it cannot. */
    private fun playLogoHere(): Boolean {
        val animated = context.getDrawable(risingLogo(icon)) as? android.graphics.drawable.Animatable ?: return false
        val drawable = animated as Drawable
        drawable.callback = this
        logo = drawable
        animated.start()
        return true
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === logo || super.verifyDrawable(who)

    private fun iconSurfaceOf(icon: View?): SurfaceControl? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching { (icon as? SurfaceView)?.surfaceControl?.takeIf { it.isValid } }.getOrNull()
    }

    private fun hideWithNextDraw(surface: SurfaceControl) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            val hide = SurfaceControl.Transaction().setAlpha(surface, 0f)
            if (rootSurfaceControl?.applyTransactionOnDraw(hide) != true) hide.apply()
        }
    }

    private fun firstDrawn() {
        val then = onFirstDraw ?: return
        onFirstDraw = null
        then()
    }

    private fun open(root: ViewGroup) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LaunchSplash.EXIT_IRIS_MS
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    root.removeView(this@MeloLaunchIris)
                }
            })
            start()
        }
    }

    private companion object {
        /** Melo's window black, as the splash's background. */
        val SPLASH_BLACK = Color.rgb(0x0B, 0x0B, 0x0B)

        /** The soft edge of the iris. */
        const val FEATHER_DP = 140f

        /** How much the M grows as it fades. */
        const val LOGO_GROW = 0.25f

        /** The splash icon's box when it has no icon background. */
        const val LOGO_DP = 288f

        /** The longest the splash waits for the iris's first draw before it goes anyway. */
        const val FIRST_DRAW_WAIT_MS = 400L
    }
}

/** The splash's last frame in the colour of [icon]; it matches the theme of the icon launched from. */
private fun litLogo(icon: `in`.melo.data.AppIcon): Int = when (icon) {
    `in`.melo.data.AppIcon.MINT -> R.drawable.splash_logo_lit
    `in`.melo.data.AppIcon.ICE -> R.drawable.splash_logo_lit_ice
    `in`.melo.data.AppIcon.AMETHYST -> R.drawable.splash_logo_lit_amethyst
    `in`.melo.data.AppIcon.EMERALD -> R.drawable.splash_logo_lit_emerald
    `in`.melo.data.AppIcon.MONO -> R.drawable.splash_logo_lit_mono
}

/** The M rising in the colour of [icon]. */
private fun risingLogo(icon: `in`.melo.data.AppIcon): Int = when (icon) {
    `in`.melo.data.AppIcon.MINT -> R.drawable.splash_logo
    `in`.melo.data.AppIcon.ICE -> R.drawable.splash_logo_ice
    `in`.melo.data.AppIcon.AMETHYST -> R.drawable.splash_logo_amethyst
    `in`.melo.data.AppIcon.EMERALD -> R.drawable.splash_logo_emerald
    `in`.melo.data.AppIcon.MONO -> R.drawable.splash_logo_mono
}
