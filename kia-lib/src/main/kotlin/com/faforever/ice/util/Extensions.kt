package com.faforever.ice.util

fun <T: Any> T.isIn(vararg items: T) = items.contains(this)
fun <T: Any> T.isNotIn(vararg items: T) = !items.contains(this)