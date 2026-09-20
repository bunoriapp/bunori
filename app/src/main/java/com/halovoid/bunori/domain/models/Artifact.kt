package com.halovoid.bunori.domain.models

import com.halovoid.bunori.data.db.entities.ArtifactEntity

data class Artifact(
    val id: Int,
    val novelUrl: String,
    val requestId: String,
    val artifactDestination: String,
    val artifactName: String
)