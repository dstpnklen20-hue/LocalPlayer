package com.localplayer.app.util

import java.util.Locale

/** Нормализация для поиска и сортировки: без регистра, «ё» = «е». */
fun String.normalized(): String = lowercase(Locale.ROOT).replace('ё', 'е')

/** Буква для алфавитного индекса: первая буква/цифра строки; цифры и прочее — «#». */
fun indexLetter(text: String): String {
    val ch = text.firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return if (ch.isLetter()) ch.uppercaseChar().let { if (it == 'Ё') "Е" else it.toString() } else "#"
}

/** «3:07» или «1:02:45». */
fun formatDuration(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0) / 1000)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** «3,4 МБ» — размер файла. */
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f КБ".format(kb)
    return "%.1f МБ".format(kb / 1024.0)
}
