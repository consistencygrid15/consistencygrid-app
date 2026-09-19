package com.consistencygridwallpaper.reelcontrol.utils

object TimeFormatter {
    /**
     * Formats seconds into a human-readable duration string.
     * - Under 1 hour:  MM:SS  (e.g. "07:45")
     * - 1 hour+:       H:MM:SS (e.g. "1:15:30")
     *
     * Previously the function only divided into minutes and seconds, causing
     * watch-time sessions longer than 59 minutes to overflow (e.g. "75:30").
     */
    fun format(totalSeconds: Long): String {
        val hours = totalSeconds / 3600L
        val mins  = (totalSeconds % 3600L) / 60L
        val secs  = totalSeconds % 60L
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }
}
