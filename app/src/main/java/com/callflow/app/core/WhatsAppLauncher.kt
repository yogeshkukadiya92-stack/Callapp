package com.callflow.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Offer both installed WhatsApp apps without relying on the default web-link handler. */
fun openWhatsApp(context: Context, phone: String, message: String = "") {
    val number = phone.filter(Char::isDigit)
    require(number.isNotBlank()) { "A phone number is required" }
    val uri = Uri.parse("https://wa.me/$number").buildUpon()
        .apply { if (message.isNotEmpty()) appendQueryParameter("text", message) }
        .build()
    val options = listOf("com.whatsapp", "com.whatsapp.w4b").mapNotNull { packageName ->
        Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }
    val intent = when (options.size) {
        0 -> throw android.content.ActivityNotFoundException("Install WhatsApp or WhatsApp Business")
        1 -> options.first()
        else -> Intent.createChooser(options.first(), "Choose WhatsApp app")
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, options.drop(1).toTypedArray())
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
