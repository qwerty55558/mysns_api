package com.mysns.main.graphql.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Place(
    @Column(name = "place_latitude")
    val latitude: Double,

    @Column(name = "place_longitude")
    val longitude: Double,

    @Column(name = "place_name", length = 128)
    val name: String,

    @Column(name = "place_address", length = 256)
    val address: String? = null,

    @Column(name = "place_external_id", length = 128)
    val externalId: String? = null,

    @Column(name = "place_category_name", length = 64)
    val categoryName: String? = null,

    @Column(name = "place_category_code", length = 16)
    val categoryCode: String? = null,
) {
    // JPA 기본 생성자
    constructor() : this(0.0, 0.0, "", null, null, null, null)
}
