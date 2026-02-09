package com.feelbachelor.doompedia.domain

import java.text.Normalizer
import java.util.Locale

private val whitespace = Regex("\\s+")

fun normalizeSearch(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .trim()
    return whitespace.replace(normalized, " ")
}
