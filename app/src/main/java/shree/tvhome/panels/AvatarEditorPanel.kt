// Important instructions:
// - Decode gallery bitmaps with sampling and recycle temporary bitmaps on dismiss to stay lightweight.
// - Derive dimensions and spacing from LayoutMetrics without hardcoded values or forced focus calls.

package shree.tvhome.panels

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics

object AvatarEditorPanel {

    private data class CropBtnSpec(val label: String, val weight: Float, val onClick: () -> Unit)

    // Opens the profile name and circular avatar framing editor dialog.
    fun show(
        ctx: Context,
        settings: AppSettings,
        onPickPhoto: () -> Unit,
        onSaved: () -> Unit
    ) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        var zoom = settings.profilePicZoom
        var panX = settings.profilePicPanX
        var panY = settings.profilePicPanY
        var rawBitmap: Bitmap? = loadUserBitmap(ctx, settings.profilePicUri)

        val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.022f, minDp = 16, maxDp = 28, horizontal = true)
        val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.024f, minDp = 14, maxDp = 24, horizontal = false)
        val gapH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 6, maxDp = 12, horizontal = true)
        val gapV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.01f, minDp = 6, maxDp = 14, horizontal = false)
        val previewSize = LayoutMetrics.dynamicSpace(ctx, ratio = 0.2f, minDp = 120, maxDp = 180, horizontal = false)
        val inputW = LayoutMetrics.dynamicSpace(ctx, ratio = 0.28f, minDp = 210, maxDp = 340, horizontal = true)

        val rootGlass = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(242, 24, 36, 78), Color.argb(248, 12, 18, 44))
            ).apply {
                cornerRadius = LayoutMetrics.dpF(ctx, 24f)
                setStroke(LayoutMetrics.dp(ctx, 1), Color.argb(140, 255, 255, 255))
            }
        }

        rootGlass.addView(TextView(ctx).apply {
            text = "✦  Profile & Avatar Studio"
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, gapV)
        })

        val contentRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Circular avatar crop canvas with center crosshairs and fallback initial.
        val cropPreview = object : View(ctx) {
            private val circlePath = Path()
            private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.argb(70, 255, 255, 255)
            }
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            private val drawMatrix = Matrix()

            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val r = minOf(cx, cy) - LayoutMetrics.dpF(context, 4f)
                if (r <= 0f) return

                circlePath.reset()
                circlePath.addCircle(cx, cy, r, Path.Direction.CW)

                canvas.save()
                canvas.clipPath(circlePath)

                val bmp = rawBitmap
                if (bmp != null && !bmp.isRecycled) {
                    val baseScale = maxOf((r * 2f) / bmp.width, (r * 2f) / bmp.height)
                    val finalScale = baseScale * zoom
                    drawMatrix.reset()
                    drawMatrix.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
                    drawMatrix.postScale(finalScale, finalScale)
                    drawMatrix.postTranslate(cx + (panX * r), cy + (panY * r))
                    canvas.drawBitmap(bmp, drawMatrix, fillPaint)

                    gridPaint.strokeWidth = LayoutMetrics.dpF(context, 1f)
                    canvas.drawLine(cx - r, cy, cx + r, cy, gridPaint)
                    canvas.drawLine(cx, cy - r, cx, cy + r, gridPaint)
                } else {
                    fillPaint.color = settings.accentColor
                    canvas.drawCircle(cx, cy, r, fillPaint)
                    textPaint.textSize = r * 0.65f
                    val initial = settings.userName.ifBlank { "A" }.trim().first().uppercaseChar().toString()
                    val ty = cy - ((textPaint.descent() + textPaint.ascent()) / 2f)
                    canvas.drawText(initial, cx, ty, textPaint)
                }
                canvas.restore()

                rimPaint.strokeWidth = LayoutMetrics.dpF(context, 2.5f)
                rimPaint.color = settings.accentColor
                canvas.drawCircle(cx, cy, r, rimPaint)
            }
        }
        contentRow.addView(cropPreview, LinearLayout.LayoutParams(previewSize, previewSize).apply {
            rightMargin = padH
        })

        val rightCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val nameInput = EditText(ctx).apply {
            isSingleLine = true
            hint = "Profile Name"
            setText(settings.userName)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(150, 200, 220, 245))
            setPadding(gapH, gapV, gapH, gapV)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = GradientDrawable().apply {
                setColor(Color.argb(55, 255, 255, 255))
                cornerRadius = LayoutMetrics.dpF(ctx, 12f)
                setStroke(LayoutMetrics.dp(ctx, 1), settings.accentColor)
            }
        }
        rightCol.addView(nameInput, LinearLayout.LayoutParams(inputW, -2))

        val controlRows = listOf(
            listOf(
                CropBtnSpec("🖼  Choose Photo", 1.4f) { dialog.dismiss(); onPickPhoto() },
                CropBtnSpec("✕ Remove", 1f) {
                    rawBitmap?.let { if (!it.isRecycled) it.recycle() }
                    rawBitmap = null
                    zoom = 1.0f
                    panX = 0f
                    panY = 0f
                    settings.prefs.edit()
                        .remove(AppSettings.PREF_PROFILE_PIC_URI)
                        .putFloat(AppSettings.PREF_PROFILE_PIC_ZOOM, 1f)
                        .putFloat(AppSettings.PREF_PROFILE_PIC_PAN_X, 0f)
                        .putFloat(AppSettings.PREF_PROFILE_PIC_PAN_Y, 0f)
                        .apply()
                    cropPreview.invalidate()
                    onSaved()
                }
            ),
            listOf(
                CropBtnSpec("🔍 +", 1f) { zoom = (zoom + 0.15f).coerceAtMost(2.8f); cropPreview.invalidate() },
                CropBtnSpec("🔍 −", 1f) { zoom = (zoom - 0.15f).coerceAtLeast(1.0f); cropPreview.invalidate() },
                CropBtnSpec("↺ Reset", 1f) { zoom = 1.0f; panX = 0f; panY = 0f; cropPreview.invalidate() }
            ),
            listOf(
                CropBtnSpec("◀", 1f) { panX = (panX - 0.10f).coerceIn(-0.85f, 0.85f); cropPreview.invalidate() },
                CropBtnSpec("▶", 1f) { panX = (panX + 0.10f).coerceIn(-0.85f, 0.85f); cropPreview.invalidate() },
                CropBtnSpec("▲", 1f) { panY = (panY - 0.10f).coerceIn(-0.85f, 0.85f); cropPreview.invalidate() },
                CropBtnSpec("▼", 1f) { panY = (panY + 0.10f).coerceIn(-0.85f, 0.85f); cropPreview.invalidate() }
            )
        )

        controlRows.forEachIndexed { rIdx, specs ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            specs.forEachIndexed { cIdx, spec ->
                row.addView(
                    makeGlossBtn(ctx, settings, spec.label, rIdx == 0 && cIdx == 0, spec.onClick),
                    LinearLayout.LayoutParams(0, -2, spec.weight).apply {
                        if (cIdx < specs.lastIndex) rightMargin = gapH
                    }
                )
            }
            rightCol.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gapV })
        }

        contentRow.addView(rightCol)
        rootGlass.addView(contentRow)

        val actionRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(
            makeGlossBtn(ctx, settings, "Cancel", false) { dialog.dismiss() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = gapH }
        )

        actionRow.addView(
            makeGlossBtn(ctx, settings, "✓  Save Profile", true) {
                settings.userName = nameInput.text.toString().trim()
                settings.profilePicZoom = zoom
                settings.profilePicPanX = panX
                settings.profilePicPanY = panY
                onSaved()
                Toast.makeText(ctx, "Profile saved", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            },
            LinearLayout.LayoutParams(0, -2, 1.3f)
        )
        rootGlass.addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = padV })

        dialog.setOnDismissListener {
            rawBitmap?.let { if (!it.isRecycled) it.recycle() }
            rawBitmap = null
        }

        dialog.setContentView(rootGlass)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    // Loads a sub-sampled bitmap from URI to prevent large allocations on low-RAM TVs.
    private fun loadUserBitmap(ctx: Context, uriStr: String?): Bitmap? {
        if (uriStr.isNullOrEmpty()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            ctx.contentResolver.openInputStream(Uri.parse(uriStr))?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) { null }
    }

    private fun makeGlossBtn(ctx: Context, settings: AppSettings, label: String, highlight: Boolean, onClick: () -> Unit) =
        TextView(ctx).apply {
            text = label
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            val padH = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 6, maxDp = 12, horizontal = true)
            val padV = LayoutMetrics.dynamicSpace(ctx, ratio = 0.008f, minDp = 6, maxDp = 12, horizontal = false)
            setPadding(padH, padV, padH, padV)
            background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, highlight, settings.tileColor)
            setOnFocusChangeListener { v, f ->
                AnimationController.animateFocus(v, f, settings.motionStyle, 1.04f, 4f)
                v.background = PanelSurfaceKit.glassCardBg(ctx, settings.accentColor, f || highlight, settings.tileColor)
            }
            setOnClickListener { onClick() }
        }
}
