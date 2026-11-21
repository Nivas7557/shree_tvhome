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
import shree.tvhome.source.LauncherPreferences
import shree.tvhome.source.LauncherUtils
import shree.tvhome.source.MotionEngine

object ProfileCropDialog {

    private data class CropBtnSpec(val label: String, val weight: Float, val onClick: () -> Unit)

    fun show(
        ctx: Context,
        lp: LauncherPreferences,
        onPickPhoto: () -> Unit,
        onSaved: () -> Unit
    ) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        var zoom = lp.prefs.getFloat("profile_pic_zoom", 1.0f)
        var panX = lp.prefs.getFloat("profile_pic_pan_x", 0f)
        var panY = lp.prefs.getFloat("profile_pic_pan_y", 0f)
        var rawBitmap: Bitmap? = loadUserBitmap(ctx, lp.prefs.getString("profile_pic_uri", null))

        val rootGlass = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 24), dp(ctx, 20), dp(ctx, 24), dp(ctx, 20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(242, 24, 36, 78), Color.argb(248, 12, 18, 44))
            ).apply {
                cornerRadius = LauncherUtils.dpF(ctx, 24f)
                setStroke(dp(ctx, 1), Color.argb(140, 255, 255, 255))
            }
        }

        rootGlass.addView(TextView(ctx).apply {
            text = "✦  Profile & Avatar Studio"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(ctx, 14))
        })

        val contentRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val previewSize = dp(ctx, 150)
        val cropPreview = object : View(ctx) {
            private val circlePath = Path()
            private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.argb(70, 255, 255, 255)
                strokeWidth = 1.5f
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
                val r = minOf(cx, cy) - dp(ctx, 4)

                circlePath.reset()
                circlePath.addCircle(cx, cy, r, Path.Direction.CW)

                canvas.save()
                canvas.clipPath(circlePath)

                val bmp
