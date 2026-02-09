package com.feelbachelor.doompedia.domain

fun editDistanceAtMostOne(left: String, right: String): Boolean {
    if (left == right) return true
    val leftLength = left.length
    val rightLength = right.length
    if (kotlin.math.abs(leftLength - rightLength) > 1) {
        return false
    }

    var i = 0
    var j = 0
    var edits = 0

    while (i < leftLength && j < rightLength) {
        if (left[i] == right[j]) {
            i++
            j++
            continue
        }

        edits++
        if (edits > 1) return false

        when {
            leftLength > rightLength -> i++
            rightLength > leftLength -> j++
            else -> {
                i++
                j++
            }
        }
    }

    if (i < leftLength || j < rightLength) edits++
    return edits <= 1
}
