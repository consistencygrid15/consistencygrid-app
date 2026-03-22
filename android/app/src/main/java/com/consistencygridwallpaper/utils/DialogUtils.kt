package com.consistencygridwallpaper.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.consistencygridwallpaper.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object DialogUtils {

    fun showCustomDialog(
        context: Context,
        title: String,
        message: String,
        positiveButtonText: String,
        positiveButtonAction: () -> Unit,
        negativeButtonText: String? = null,
        negativeButtonAction: (() -> Unit)? = null,
        cancelable: Boolean = true
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_custom_premium, null)
        
        val tvTitle = view.findViewById<TextView>(R.id.dialog_title)
        val tvMessage = view.findViewById<TextView>(R.id.dialog_message)
        val btnPositive = view.findViewById<MaterialButton>(R.id.dialog_button_positive)
        val btnNegative = view.findViewById<MaterialButton>(R.id.dialog_button_negative)

        tvTitle.text = title
        tvMessage.text = message
        btnPositive.text = positiveButtonText

        // Using standard AlertDialog with custom transparent background because MaterialDialog
        // enforces a background color which ruins rounded corners of the MaterialCardView inside
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(cancelable)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnPositive.setOnClickListener {
            positiveButtonAction()
            dialog.dismiss()
        }

        if (negativeButtonText != null) {
            btnNegative.visibility = View.VISIBLE
            btnNegative.text = negativeButtonText
            btnNegative.setOnClickListener {
                negativeButtonAction?.invoke()
                dialog.dismiss()
            }
        } else {
            btnNegative.visibility = View.GONE
        }

        dialog.show()
    }
}
