package com.wasal.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {

    fun time(timestamp: Long): String {
        return SimpleDateFormat(
            "HH:mm",
            Locale("ar")
        ).format(Date(timestamp))
    }
}
