// Important instructions:
// - Render glass app tiles with exponential breathing focus glow, specular shimmer sweep, and Heart/Circle/Squircle shapes.
// - Provide a 4-card App Context & Info dialog (Open, Favorite, App Info specs, Hide/Uninstall) matching AppContextMenu.tsx.

package shree.tvhome.design

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics
import shree.tvhome.core.Themes

object AppTileDesign {

    interface TileActions {
        fun onToggleFavorite(pkg: String, label: String)
        fun onMoveFavorite(pkg: String, delta: Int)
        fun onHideApp(pkg: String, label: String)
    }

    // Custom glossy surface supporting Heart/Circle/Squircle silhouettes, breathing glow, and focus shimmer sweep.
    private class GlossyTileSurface(
        ctx: Context,
        private val settings: AppSettings,
        private val appAccentColor: Int
    ) : FrameLayout(ctx) {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val outerHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val shapePath = Path()
        private val rect = RectF()
        private val shimmerMatrix = Matrix()

        private var shimmerFraction = -1f
        private var breathPhase = 0f
        private var focusAnim: ValueAnimator? = null

        init {
            setWillNotDraw(false)
            clipChildren = false
            clipToPadding = false
        }

        fun setFocusVisualActive(active: Boolean) {
            focusAnim?.cancel()
            if (active) {
                focusAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 2600L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        val t = anim.animatedValue as Float
                        breathPhase = kotlin.math.sin(t * Math.PI).toFloat()
                        shimmerFraction = if (t < 0.65f) (t / 0.65f) else -1f
                        invalidate()
                    }
                    start()
                }
            } else {
                breathPhase = 0f
                shimmerFraction = -1f
                invalidate()
            }
        }

        private fun buildSilhouettePath(w: Float, h: Float, inset: Float) {
            shapePath.reset()
            rect.set(inset, inset, w - inset, h - inset)
            when (settings.tileShape) {
                "circle" -> {
                    val r = (minOf(rect.width(), rect.height()) / 2f).coerceAtLeast(4f)
                    shapePath.addCircle(rect.centerX(), rect.centerY(), r, Path.Direction.CW)
                }
                "heart" -> {
                    val l = rect.left; val t = rect.top; val rw = rect.width(); val rh = rect.height()
                    shapePath.moveTo(l + rw * 0.50f, t + rh * 0.94f)
                    shapePath.cubicTo(l + rw * 0.44f, t + rh * 0.88f, l + rw * 0.06f, t + rh * 0.58f, l + rw * 0.06f, t + rh * 0.32f)
                    shapePath.cubicTo(l + rw * 0.06f, t + rh * 0.14f, l + rw * 0.21f, t + rh * 0.05f, l + rw * 0.36f, t + rh * 0.05f)
                    shapePath.cubicTo(l + rw * 0.44f, t + rh * 0.05f, l + rw * 0.48f, t + rh * 0.10f, l + rw * 0.50f, t + rh * 0.17f)
                    shapePath.cubicTo(l + rw * 0.52f, t + rh * 0.10f, l + rw * 0.56f, t + rh * 0.05f, l + rw * 0.64f, t + rh * 0.05f)
                    shapePath.cubicTo(l + rw * 0.79f, t + rh * 0.05f, l + rw * 0.94f, t + rh * 0.14f, l + rw * 0.94f, t + rh * 0.32f)
                    shapePath.cubicTo(l + rw * 0.94f, t + rh * 0.58f, l + rw * 0.56f, t + rh * 0.88f, l + rw * 0.50f, t + rh * 0.94f)
                    shapePath.close()
                }
                else -> {
                    val cornerDp = Themes.getCornerRadiusDp(settings.tileShape)
                    val radius = LayoutMetrics.dpF(context, cornerDp)
                    shapePath.addRoundRect(rect, radius, radius, Path.Direction.CW)
                }
            }
        }

        override fun dispatchDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val focused = hasFocus()
            val theme = Themes.getTheme(settings.tileColor)
            val cols = if (focused) theme.focusedColors else theme.normalColors
            val glowColor = if (appAccentColor != 0) appAccentColor else settings.accentColor

            buildSilhouettePath(w, h, LayoutMetrics.dpF(context, 1.5f))

            bgPaint.shader = LinearGradient(0f, 0f, w * 0.6f, h, cols, null, Shader.TileMode.CLAMP)
            canvas.drawPath(shapePath, bgPaint)

            // Radiant inner backlight aura breathing in and out when focused.
            if (focused) {
                val auraAlpha = (85 + (breathPhase * 75)).toInt().coerceIn(70, 175)
                auraPaint.shader = RadialGradient(
                    w * 0.5f, h * 0.38f, maxOf(w, h) * 0.65f,
                    intArrayOf(LayoutMetrics.withAlpha(glowColor, auraAlpha), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawPath(shapePath, auraPaint)
            }

            // Clip child icon & text cleanly to the tile silhouette (including Heart & Circle).
            val saveCount = canvas.save()
            canvas.clipPath(shapePath)
            super.dispatchDraw(canvas)

            // Specular diagonal shimmer sweep across focused tile.
            if (focused && shimmerFraction in 0f..1f) {
                val sweepX = (-0.5f + shimmerFraction * 2.0f) * w
                shimmerPaint.shader = LinearGradient(
                    0f, 0f, w * 0.55f, h * 0.25f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(56, 255, 255, 255), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                shimmerMatrix.setTranslate(sweepX, 0f)
                shimmerPaint.shader.setLocalMatrix(shimmerMatrix)
                canvas.drawRect(0f, 0f, w, h, shimmerPaint)
            }
            canvas.restoreToCount(saveCount)

            // Outer breathing halo & crisp specular border ring.
            if (focused) {
                outerHaloPaint.strokeWidth = LayoutMetrics.dpF(context, 4.5f + (breathPhase * 2.5f))
                outerHaloPaint.color = LayoutMetrics.withAlpha(glowColor, (110 + breathPhase * 110).toInt())
                canvas.drawPath(shapePath, outerHaloPaint)

                rimPaint.strokeWidth = LayoutMetrics.dpF(context, 2.4f)
                rimPaint.color = Color.WHITE
                canvas.drawPath(shapePath, rimPaint)
            } else {
                rimPaint.strokeWidth = LayoutMetrics.dpF(context, 1.1f)
                rimPaint.color = Color.argb(68, 255, 255, 255)
                canvas.drawPath(shapePath, rimPaint)
            }
        }
    }
    // Builds a focusable glass app tile with breathing glow, shimmer, badge, and 4-card context dialog.
    fun create(
        ctx: Context,
        pm: PackageManager,
        settings: AppSettings,
        info: ResolveInfo,
        isFavRow: Boolean,
        isFav: Boolean,
        canMoveLeft: Boolean,
        canMoveRight: Boolean,
        w: Int,
        h: Int,
        actions: TileActions
    ): View {
        val pkg = info.activityInfo.packageName
        val label = info.loadLabel(pm)?.toString() ?: pkg
        val prefix = if (isFavRow) "fav_" else "all_"

        val surface = GlossyTileSurface(ctx, settings, settings.accentColor).apply {
            tag = "$prefix$pkg"
            isFocusable = true
            isClickable = true
            isLongClickable = true
            nextFocusRightId = View.NO_ID
        }

        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padH = LayoutMetrics.dynamicSpace(ctx, 0.008f, 6, 12, true)
            val padV = LayoutMetrics.dynamicSpace(ctx, 0.006f, 4, 10, false)
            setPadding(padH, padV, padH, padV)
        }

        val iconBox = FrameLayout(ctx)
        val iconSize = (minOf(w, h) * 0.44f).toInt().coerceAtLeast(LayoutMetrics.dp(ctx, 26))
        val iconView = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            try { setImageDrawable(info.loadIcon(pm)) } catch (_: Exception) {}
        }
        iconBox.addView(iconView, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))

        val badgeStr = settings.iconBadge
        if (badgeStr.isNotEmpty()) {
            val badgeTv = TextView(ctx).apply {
                text = badgeStr
                textSize = 9.5f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(215, 22, 28, 46))
                    setStroke(LayoutMetrics.dp(ctx, 1), Color.argb(120, 255, 255, 255))
                }
            }
            val bSize = LayoutMetrics.dp(ctx, 18)
            iconBox.addView(badgeTv, FrameLayout.LayoutParams(bSize, bSize, Gravity.BOTTOM or Gravity.END))
        }

        val labelTv = TextView(ctx).apply {
            tag = "tile_label"
            text = label
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            visibility = if (settings.nameplateMode == "hidden") View.GONE else View.VISIBLE
            setTextColor(Themes.getPrimaryTextColor(settings.tileColor))
        }

        inner.addView(iconBox, LinearLayout.LayoutParams(-2, -2))
        inner.addView(labelTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = LayoutMetrics.dp(ctx, 5) })
        surface.addView(inner, FrameLayout.LayoutParams(-1, -1))

        if (isFav) {
            val starTv = TextView(ctx).apply {
                text = "★"
                textSize = 10.5f
                setTextColor(Color.rgb(251, 191, 36))
            }
            val m = LayoutMetrics.dp(ctx, 7)
            surface.addView(starTv, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = m; rightMargin = m
            })
        }

        surface.setOnFocusChangeListener { v, hasFocus ->
            AnimationController.animateFocus(v, hasFocus, settings.motionStyle, 1.07f, 10f)
            surface.setFocusVisualActive(hasFocus)
            if (settings.nameplateMode == "focus_only") {
                labelTv.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
            }
        }

        surface.setOnClickListener {
            v -> v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90L).withEndAction {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(110L).start()
                launchApp(ctx, pm, pkg)
            }.start()
        }

        surface.setOnLongClickListener {
            showAppContextDialog(ctx, pm, settings, pkg, label, isFavRow, isFav, canMoveLeft, canMoveRight, actions)
            true
        }

        surface.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                showAppContextDialog(ctx, pm, settings, pkg, label, isFavRow, isFav, canMoveLeft, canMoveRight, actions)
                true
            } else false
        }

        return surface
    }

    fun updateTileAppearance(ctx: Context, tile: View, settings: AppSettings) {
        val labelTv = tile.findViewWithTag<TextView>("tile_label")
        labelTv?.setTextColor(Themes.getPrimaryTextColor(settings.tileColor))
        labelTv?.visibility = when (settings.nameplateMode) {
            "hidden" -> View.GONE
            "focus_only" -> if (tile.hasFocus()) View.VISIBLE else View.INVISIBLE
            else -> View.VISIBLE
        }
        tile.invalidate()
    }

    private fun launchApp(ctx: Context, pm: PackageManager, pkg: String) {
        val launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(launchIntent) } catch (_: Exception) {}
        }
    }

    // 4-card glass App Context & App Info modal modeled after AppContextMenu.tsx.
    private fun showAppContextDialog(
        ctx: Context,
        pm: PackageManager,
        settings: AppSettings,
        pkg: String,
        label: String,
        isFavRow: Boolean,
        isFav: Boolean,
        canMoveLeft: Boolean,
        canMoveRight: Boolean,
        actions: TileActions
    ) {
        val dialog = Dialog(ctx).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val padH = LayoutMetrics.dynamicSpace(ctx, 0.020f, 16, 26, true)
        val padV = LayoutMetrics.dynamicSpace(ctx, 0.020f, 14, 22, false)
        val gap = LayoutMetrics.dynamicSpace(ctx, 0.010f, 6, 12, false)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(21, 27, 39), Color.rgb(13, 17, 26))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(ctx, 26f)
                setStroke(LayoutMetrics.dp(ctx, 1), Color.argb(120, 255, 255, 255))
            }
        }

        // Header with app icon, title & package name.
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, padV / 2)
        }
        val iconImg = ImageView(ctx).apply {
            try { setImageDrawable(pm.getApplicationIcon(pkg)) } catch (_: Exception) {}
        }
        val iconBoxSize = LayoutMetrics.dp(ctx, 42)
        header.addView(iconImg, LinearLayout.LayoutParams(iconBoxSize, iconBoxSize).apply { rightMargin = padH / 2 })

        val titleCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(ctx).apply {
            text = label; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        })
        titleCol.addView(TextView(ctx).apply {
            text = pkg; textSize = 10f; typeface = Typeface.MONOSPACE; setTextColor(Color.argb(145, 255, 255, 255))
        })
        header.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        val actionList = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val infoSheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        fun makeActionCard(icon: String, title: String, sub: String, tint: Int, onClick: () -> Unit): LinearLayout {
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true; isClickable = true
                val cPadH = LayoutMetrics.dynamicSpace(ctx, 0.012f, 10, 16, true)
                val cPadV = LayoutMetrics.dynamicSpace(ctx, 0.010f, 8, 13, false)
                setPadding(cPadH, cPadV, cPadH, cPadV)
                val syncCardBg = {
                    background = GradientDrawable().apply {
                        setColor(if (hasFocus()) Color.argb(55, 255, 255, 255) else Color.rgb(28, 35, 49))
                        cornerRadius = LayoutMetrics.dpF(ctx, 18f)
                        setStroke(LayoutMetrics.dp(ctx, if (hasFocus()) 2 else 1), if (hasFocus()) tint else Color.rgb(55, 64, 81))
                    }
                }
                syncCardBg()
                setOnFocusChangeListener { v, f ->
                    AnimationController.animateFocus(v, f, settings.motionStyle, 1.02f, 4f)
                    syncCardBg()
                }
                setOnClickListener { onClick() }

                val badge = TextView(ctx).apply {
                    text = icon; textSize = 14f; gravity = Gravity.CENTER; setTextColor(tint)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(LayoutMetrics.withAlpha(tint, 48))
                    }
                }
                val bSize = LayoutMetrics.dp(ctx, 34)
                addView(badge, LinearLayout.LayoutParams(bSize, bSize).apply { rightMargin = cPadH })

                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                col.addView(TextView(ctx).apply { text = title; textSize = 12.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
                col.addView(TextView(ctx).apply { text = sub; textSize = 10f; setTextColor(Color.argb(150, 255, 255, 255)) })
                addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }

        // Card 1: Open App
        actionList.addView(
            makeActionCard("▶", "Open App", "Launch on TV screen", settings.accentColor) {
                dialog.dismiss(); launchApp(ctx, pm, pkg)
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap }
        )

        // Card 2: Add/Remove Favorites
        actionList.addView(
            makeActionCard(
                "★",
                if (isFav) "Remove from Favorites" else "Add to Favorites",
                if (isFav) "Unpin from top favorites ribbon" else "Pin to top favorites ribbon",
                Color.rgb(251, 191, 36)
            ) {
                dialog.dismiss(); actions.onToggleFavorite(pkg, label)
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap }
        )

        if (isFavRow && (canMoveLeft || canMoveRight)) {
            val orderRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            if (canMoveLeft) {
                orderRow.addView(
                    makeActionCard("◀", "Move Left", "Reorder earlier", Color.rgb(56, 189, 248)) {
                        dialog.dismiss(); actions.onMoveFavorite(pkg, -1)
                    },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { if (canMoveRight) rightMargin = gap }
                )
            }
            if (canMoveRight) {
                orderRow.addView(
                    makeActionCard("▶", "Move Right", "Reorder later", Color.rgb(56, 189, 248)) {
                        dialog.dismiss(); actions.onMoveFavorite(pkg, 1)
                    },
                    LinearLayout.LayoutParams(0, -2, 1f)
                )
            }
            actionList.addView(orderRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap })
        }

        // Card 3: App Info Sheet (inline specs view)
        actionList.addView(
            makeActionCard("ℹ", "App Info", "Version, package specs & system details", Color.rgb(56, 189, 248)) {
                actionList.visibility = View.GONE
                infoSheet.visibility = View.VISIBLE
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap }
        )

        // Card 4: Hide from Home
        actionList.addView(
            makeActionCard("👁", "Hide from Home", "Hide app from launcher shelves", Color.rgb(248, 113, 113)) {
                dialog.dismiss(); actions.onHideApp(pkg, label)
            },
            LinearLayout.LayoutParams(-1, -2)
        )

        // Build Inline App Info View (Version, Package, System Settings shortcut).
        val pkgInfo = try { pm.getPackageInfo(pkg, 0) } catch (_: Exception) { null }
        val verName = pkgInfo?.versionName ?: "1.0"
        infoSheet.addView(TextView(ctx).apply {
            text = "Version: $verName\nPackage: $pkg\nPermissions: TV Audio, Wake Lock, Network"
            textSize = 11f
            setTextColor(Color.rgb(215, 230, 250))
            setPadding(padH / 2, padV / 2, padH / 2, padV / 2)
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 255, 255, 255))
                cornerRadius = LayoutMetrics.dpF(ctx, 14f)
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap })

        infoSheet.addView(
            makeActionCard("⚙", "Android App Settings", "Open system storage & permissions", settings.accentColor) {
                dialog.dismiss()
                try {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap }
        )

        infoSheet.addView(
            makeActionCard("‹", "Back to Actions", "Return to app actions", Color.WHITE) {
                infoSheet.visibility = View.GONE
                actionList.visibility = View.VISIBLE
            },
            LinearLayout.LayoutParams(-1, -2)
        )

        root.addView(actionList)
        root.addView(infoSheet)
        dialog.setContentView(root, LinearLayout.LayoutParams(LayoutMetrics.dynamicSpace(ctx, 0.34f, 260, 420, true), -2))
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
