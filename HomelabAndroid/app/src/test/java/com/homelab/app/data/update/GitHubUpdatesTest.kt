package com.homelab.app.data.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubUpdatesTest {
    @Test
    fun `selects the named APK from a newer stable release`() {
        val release = Json { ignoreUnknownKeys = true }.decodeFromString<GitHubRelease>(
            """{"tag_name":"v1.6.10","draft":false,"prerelease":false,"assets":[
                {"name":"Homelab.apk.sha256","size":83},{"name":"Homelab.apk","size":12000}],
                "html_url":"https://github.com/example/homelab/releases/tag/v1.6.10"}"""
        )
        assertEquals(GitHubUpdate.Available("v1.6.10", 12000), release.evaluate("1.6.9"))
        assertEquals(GitHubUpdate.UpToDate, release.evaluate("1.6.10"))
        assertEquals(GitHubUpdate.UpToDate, release.evaluate("2.0.0"))
    }

    @Test
    fun `rejects drafts prereleases and unusable assets`() {
        val release = GitHubRelease("v1.6.8", assets = listOf(GitHubAsset("Homelab.apk", 12000)))
        assertEquals(GitHubUpdate.NoRelease, release.copy(draft = true).evaluate("1.6.7"))
        assertEquals(GitHubUpdate.NoRelease, release.copy(prerelease = true).evaluate("1.6.7"))
        assertEquals(GitHubUpdate.NoRelease, release.copy(tag = "v1.6.8-rc1").evaluate("1.6.7"))
        assertEquals(GitHubUpdate.NoRelease, release.copy(assets = emptyList()).evaluate("1.6.7"))
        assertEquals(GitHubUpdate.NoRelease, release.copy(
            assets = listOf(GitHubAsset("Homelab.apk", Long.MAX_VALUE))
        ).evaluate("1.6.7"))
    }
}
