package com.oss.euphoriae.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model from GitHub Contributors API
 * Endpoint: GET https://api.github.com/repos/{owner}/{repo}/contributors
 */
@Serializable
data class GitHubContributor(
    val login: String,
    @SerialName("avatar_url")
    val avatarUrl: String,
    val contributions: Int,
    @SerialName("html_url")
    val htmlUrl: String
)
