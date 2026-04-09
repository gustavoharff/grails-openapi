package dev.harff.grails.openapi

data class KotlinArticle(
    val id: Long,
    val title: String,
    val summary: String?,
    val viewCount: Int?,
    val published: Boolean
)
