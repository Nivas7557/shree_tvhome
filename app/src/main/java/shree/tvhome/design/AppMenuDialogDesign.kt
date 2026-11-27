// Important instructions:
// - Dedicated 4-card glass App Context & App Info modal (Open, Favorite/Reorder, App Info, Disable, Uninstall, Hide/Unhide).
// - Derive all dialog dimensions and paddings dynamically from LayoutMetrics without hardcoded values.
package shree.tvhome.design

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import shree.tvhome.core.AnimationController
import shree.tvhome.core.AppSettings
import shree.tvhome.core.LayoutMetrics

object AppMenuDialogDesign {
    // Opens the contextual glass modal for any visible or hidden app tile.
    fun show(
        ctx: Context,
        pm: PackageManager,
        settings: AppSettings,
        pkg: String,
        label: String,
        isFavRow: Boolean,
        isFav: Boolean,
        canMoveLeft: Boolean,
        canMoveRight: Boolean,
        isHiddenMode: Boolean = false,
        onLaunch: () -> Unit,
        onToggleFav: () -> Unit = {},
        onMoveFav: (Int) -> Unit = {},
        onHideOrUnhide: () -> Unit = {}
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
            text = label
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        titleCol.addView(TextView(ctx).apply {
            text = pkg
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.argb(145, 255, 255, 255))
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
                isFocusable = true
                isClickable = true
                val cPadH = LayoutMetrics.dynamicSpace(ctx, 0.012f, 10, 16, true)
                val cPadV = LayoutMetrics.dynamicSpace(ctx, 0.009f, 7, 12, false)
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
                    text = icon
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(tint)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(LayoutMetrics.withAlpha(tint, 48))
                    }
                }
                val bSize = LayoutMetrics.dp(ctx, 34)
                addView(badge, LinearLayout.LayoutParams(bSize, bSize).apply { rightMargin = cPadH })
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                col.addView(TextView(ctx).apply {
                    text = title
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                })
                col.addView(TextView(ctx).apply {
                    text = sub
                    textSize = 10f
                    setTextColor(Color.argb(150, 255, 255, 255))
                })
                addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
        actionList.addView(
            makeActionCard("▶", "Open App", "Launch on TV screen", settings.accentColor) {
                dialog.dismiss()
                onLaunch()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap }
        )
        if (!isHiddenMode) {
            actionList.addView(
                makeActionCard(
                    "★",
                    if (isFav) "Remove from Favorites" else "Add to Favorites",
                    if (isFav) "Unpin from top favorites ribbon" else "Pin to top favorites ribbon",
                    Color.rgb(251, 191, 36)
                ) {
                    dialog.dismiss()
                    onToggleFav()
                },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap }
            )
            if (isFavRow && (canMoveLeft || canMoveRight)) {
                val orderRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                if (canMoveLeft) {
                    orderRow.addView(
                        makeActionCard("◀", "Move Left", "Reorder earlier", Color.rgb(56, 189, 248)) {
                            dialog.dismiss()
                            onMoveFav(-1)
                        },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { if (canMoveRight) rightMargin = gap }
                    )
                }
                if (canMoveRight) {
                    orderRow.addView(
                        makeActionCard("▶", "Move Right", "Reorder later", Color.rgb(56, 189, 248)) {
                            dialog.dismiss()
                            onMoveFav(1)
                        },
                        LinearLayout.LayoutParams(0, -2, 1f)
                    )
                }
                actionList.addView(orderRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap })
            }
        }
        actionList.addView(
            makeActionCard("ℹ", "App Info", "Version, package specs & system details", Color.rgb(56, 189, 248)) {
                actionList.visibility = View.GONE
                infoSheet.visibility = View.VISIBLE
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap }
        )
        actionList.addView(
            makeActionCard(
                "👁",
                if (isHiddenMode) "Unhide to Home" else "Hide from Home",
                if (isHiddenMode) "Restore app back to launcher shelves" else "Hide app from launcher shelves",
                if (isHiddenMode) Color.rgb(46, 232, 138) else Color.rgb(175, 195, 235)
            ) {
                dialog.dismiss()
                onHideOrUnhide()
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap }
        )
        val pkgInfo = try { pm.getPackageInfo(pkg, 0) } catch (_: Exception) { null }
        val verName = pkgInfo?.versionName ?: "1.0"
        infoSheet.addView(TextView(ctx).apply {
            text = "Version: $verName\nPackage: $pkg\nStorage & App Management"
            textSize = 11f
            setTextColor(Color.rgb(215, 230, 250))
            setPadding(padH / 2, padV / 2, padH / 2, padV / 2)
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 255, 255, 255))
                cornerRadius = LayoutMetrics.dpF(ctx, 14f)
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap })
        infoSheet.addView(
            makeActionCard("🚫", "Disable / App Settings", "Manage permissions, storage or disable", Color.rgb(255, 185, 85)) {
                dialog.dismiss()
                try {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    Toast.makeText(ctx, "App details unavailable", Toast.LENGTH_SHORT).show()
                }
            },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap }
        )
        infoSheet.addView(
            makeActionCard("🗑", "Uninstall App", "Remove application from TV", Color.rgb(255, 95, 105)) {
                dialog.dismiss()
                try {
                    val delIntent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (delIntent.resolveActivity(pm) != null) ctx.startActivity(delIntent)
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
