package com.dvait.base.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

@Entity
data class CapturedText(
    @Id var id: Long = 0,
    var text: String = "",
    var sourceApp: String = "",
    var sourceType: String = "",
    var timestamp: Long = 0,
    @HnswIndex(dimensions = 384, distanceType = io.objectbox.annotation.VectorDistanceType.COSINE)
    var embedding: FloatArray = FloatArray(0),
    @HnswIndex(dimensions = 768, distanceType = io.objectbox.annotation.VectorDistanceType.COSINE)
    var embeddingVyakyarth: FloatArray? = null
) {

    enum class SourceType { SCREEN, NOTIFICATION, OCR }
}
