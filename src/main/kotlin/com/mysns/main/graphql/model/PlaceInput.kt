package com.mysns.main.graphql.model

data class PlaceInput(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val address: String? = null,
    val externalId: String? = null,
    val categoryName: String? = null,
    val categoryCode: String? = null,
) {
    fun toEntity(): Place = Place(
        latitude = latitude,
        longitude = longitude,
        name = name,
        address = address,
        externalId = externalId,
        categoryName = categoryName,
        categoryCode = categoryCode,
    )
}
