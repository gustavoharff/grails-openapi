package dev.harff.grails.openapi

import kotlin.reflect.full.memberProperties

object KotlinNullabilityChecker {

    @JvmStatic
    fun isNullable(cls: Class<*>, propertyName: String): Boolean {
        val kClass = cls.kotlin
        val prop = kClass.memberProperties.find { it.name == propertyName }
        return prop?.returnType?.isMarkedNullable ?: false
    }
}
