package me.commandblock2.tsGenerator

import kotlin.reflect.KClass

fun KClass<*>.binaryName(): String {
    val fullName = this.java.name
    return fullName.substring(fullName.lastIndexOf('.') + 1)
}