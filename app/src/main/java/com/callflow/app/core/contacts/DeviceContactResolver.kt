package com.callflow.app.core.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DeviceContactResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = LruCache<String, String>(500)
    private val missing = LruCache<String, Boolean>(500)

    fun trimCache() {
        synchronized(cache) {
            cache.evictAll()
            missing.evictAll()
        }
    }

    open fun resolveContactName(phoneNumber: String): String? {
        val trimmed = phoneNumber.trim()
        if (trimmed.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        synchronized(cache) {
            cache.get(trimmed)?.let { return it }
            if (missing.get(trimmed) == true) return null
        }

        val digits = trimmed.filter(Char::isDigit)
        val last10 = if (digits.length >= 10) digits.takeLast(10) else null
        val candidates = linkedSetOf<String>()

        // Prioritize last 10 digits when an international prefix is present (e.g. +91)
        if (last10 != null && (trimmed.startsWith("+") || digits.startsWith("91") || digits.startsWith("0"))) {
            candidates.add(last10)
        }
        candidates.add(trimmed)
        if (digits.isNotEmpty()) {
            candidates.add(digits)
        }
        if (last10 != null) {
            candidates.add(last10)
            candidates.add("+91$last10")
            candidates.add("0$last10")
        }

        for (candidate in candidates) {
            val name = queryPhoneLookup(candidate)
            if (name != null) {
                synchronized(cache) {
                    cache.put(trimmed, name)
                    if (last10 != null) cache.put(last10, name)
                    missing.remove(trimmed)
                    if (last10 != null) missing.remove(last10)
                }
                return name
            }
        }

        // Fallback: Query CommonDataKinds.Phone directly
        val fallbackName = queryCommonDataKindsPhone(candidates, last10)
        if (fallbackName != null) {
            synchronized(cache) {
                cache.put(trimmed, fallbackName)
                if (last10 != null) cache.put(last10, fallbackName)
                missing.remove(trimmed)
                if (last10 != null) missing.remove(last10)
            }
            return fallbackName
        }

        synchronized(cache) {
            missing.put(trimmed, true)
            if (last10 != null) missing.put(last10, true)
        }
        return null
    }

    private fun queryPhoneLookup(number: String): String? = runCatching {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (index != -1) cursor.getString(index)?.takeIf(String::isNotBlank) else null
            } else null
        }
    }.getOrNull()

    private fun queryCommonDataKindsPhone(candidates: Set<String>, last10: String?): String? = runCatching {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
        )

        // Try exact match on candidates
        for (candidate in candidates) {
            val selection = "${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} = ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
            val args = arrayOf(candidate, candidate)
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (index != -1) {
                        cursor.getString(index)?.takeIf(String::isNotBlank)?.let { return it }
                    }
                }
            }
        }

        // Try suffix LIKE match on last 10 digits
        if (last10 != null) {
            val likeSelection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} LIKE ?"
            val likeArgs = arrayOf("%$last10", "%$last10")
            context.contentResolver.query(uri, projection, likeSelection, likeArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (index != -1) {
                        cursor.getString(index)?.takeIf(String::isNotBlank)?.let { return it }
                    }
                }
            }
        }

        null
    }.getOrNull()
}
