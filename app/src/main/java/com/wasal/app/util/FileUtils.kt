package com.wasal.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileUtils {

    fun displayName(
        context: Context,
        uri: Uri
    ): String? {
        return context.contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(
                OpenableColumns.DISPLAY_NAME
            )

            if (cursor.moveToFirst() && index >= 0) {
                cursor.getString(index)
            } else {
                null
            }
        }
    }
}
