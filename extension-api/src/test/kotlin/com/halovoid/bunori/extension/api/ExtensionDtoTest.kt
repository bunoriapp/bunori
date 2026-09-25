package com.halovoid.bunori.extension.api

import com.halovoid.bunori.extension.api.models.ChapterDto
import com.halovoid.bunori.extension.api.models.ExtensionMetadata
import com.halovoid.bunori.extension.api.models.ListingDto
import com.halovoid.bunori.extension.api.models.NovelDto
import com.halovoid.bunori.extension.api.models.SearchResultDto
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExtensionDtoTest {

    @Test
    fun testMetadataSerialization() {
        val meta = ExtensionMetadata(
            id = "novelfull",
            name = "NovelFull",
            version = "1.0.0",
            apiVersion = 1,
            lang = "en",
            baseUrl = "https://novelfull.com"
        )

        val jsonStr = ExtensionJson.json.encodeToString(meta)
        val decoded = ExtensionJson.json.decodeFromString<ExtensionMetadata>(jsonStr)

        assertEquals(meta.id, decoded.id)
        assertEquals(meta.name, decoded.name)
        assertEquals(meta.version, decoded.version)
        assertEquals(meta.apiVersion, decoded.apiVersion)
        assertEquals(meta.lang, decoded.lang)
        assertEquals(meta.baseUrl, decoded.baseUrl)
        assertNull(decoded.iconUrl)
    }

    @Test
    fun testNovelDtoSerializationWithUnknownKeys() {
        val rawJson = """
            {
                "url": "https://novelbins.com/b/test-novel",
                "title": "Test Novel",
                "author": "Test Author",
                "coverUrl": "https://novelbins.com/cover.jpg",
                "description": "Synopsis here",
                "status": "Ongoing",
                "genres": ["Action", "Fantasy"],
                "unknown_extra_field": 12345,
                "chapters": [
                    {
                        "url": "https://novelbins.com/b/test-novel/c1",
                        "title": "Chapter 1: The Beginning",
                        "index": 1,
                        "releaseDate": "2026-01-01",
                        "random_field": true
                    }
                ]
            }
        """.trimIndent()

        val decoded = ExtensionJson.json.decodeFromString<NovelDto>(rawJson)

        assertEquals("https://novelbins.com/b/test-novel", decoded.url)
        assertEquals("Test Novel", decoded.title)
        assertEquals("Test Author", decoded.author)
        assertEquals(2, decoded.genres.size)
        assertEquals("Action", decoded.genres[0])
        assertEquals(1, decoded.chapters.size)
        assertEquals("Chapter 1: The Beginning", decoded.chapters[0].title)
        assertEquals(1, decoded.chapters[0].index)
    }

    @Test
    fun testSearchResultAndListingDto() {
        val searchItem = SearchResultDto(
            url = "https://example.com/novel-1",
            title = "Sample Title",
            coverUrl = "https://example.com/cover.png",
            author = "Author Name"
        )
        val searchJson = ExtensionJson.json.encodeToString(searchItem)
        val decodedSearch = ExtensionJson.json.decodeFromString<SearchResultDto>(searchJson)
        assertEquals(searchItem, decodedSearch)

        val listing = ListingDto(id = "latest", name = "Latest Novels")
        val listingJson = ExtensionJson.json.encodeToString(listing)
        val decodedListing = ExtensionJson.json.decodeFromString<ListingDto>(listingJson)
        assertEquals(listing, decodedListing)
    }

    @Test
    fun testRepoEntrySerializationAndParsing() {
        val jsonArray = """
            [
                {
                    "id": "novelfull",
                    "name": "Novel Full",
                    "version": "1.0.0",
                    "apiVersion": 1,
                    "lang": "en",
                    "baseUrl": "https://novelfull.com",
                    "entryClass": "com.halovoid.bunorisources.crawler.NovelFull",
                    "bextUrl": "novelfull.bext",
                    "size": 12345
                }
            ]
        """.trimIndent()

        val entriesFromArray = com.halovoid.bunori.extension.api.models.ExtensionRepoEntry.parseIndex(jsonArray)
        assertEquals(1, entriesFromArray.size)
        assertEquals("novelfull", entriesFromArray[0].id)
        assertEquals(com.halovoid.bunori.extension.api.models.ExtensionFormat.BEXT_WASM, entriesFromArray[0].format)
        assertEquals("Novel Full", entriesFromArray[0].name)
        assertEquals("1.0.0", entriesFromArray[0].version)
        assertEquals("novelfull.bext", entriesFromArray[0].bextUrl)

        val wasmCatalogJson = """
            [
                {
                    "id": "novelbins",
                    "name": "Novel Bins",
                    "version": "1.0.0",
                    "apiVersion": 1,
                    "lang": "en",
                    "baseUrl": "https://novelbins.com",
                    "bextUrl": "https://github.com/BunoriApp/BunoriExtensions/releases/download/v1/novelbins.bext"
                }
            ]
        """.trimIndent()
        val wasmEntries = com.halovoid.bunori.extension.api.models.ExtensionRepoEntry.parseIndex(wasmCatalogJson)
        assertEquals(1, wasmEntries.size)
        assertNull(wasmEntries[0].entryClass)
        assertEquals("novelbins", wasmEntries[0].id)
        assertEquals(com.halovoid.bunori.extension.api.models.ExtensionFormat.BEXT_WASM, wasmEntries[0].format)

        val jsonObject = """
            {
                "repoName": "Bunori Extensions",
                "version": 1,
                "extensions": [
                    {
                        "id": "novelbins",
                        "name": "Novel Bins",
                        "version": "2.1.0",
                        "apiVersion": 1,
                        "lang": "en",
                        "baseUrl": "https://novelbins.com",
                        "entryClass": "com.halovoid.bunorisources.crawler.NovelBins",
                        "bextUrl": "novelbins.bext"
                    }
                ]
            }
        """.trimIndent()

        val entriesFromObject = com.halovoid.bunori.extension.api.models.ExtensionRepoEntry.parseIndex(jsonObject)
        assertEquals(1, entriesFromObject.size)
        assertEquals("novelbins", entriesFromObject[0].id)
        assertEquals(com.halovoid.bunori.extension.api.models.ExtensionFormat.BEXT_WASM, entriesFromObject[0].format)
        assertEquals("2.1.0", entriesFromObject[0].version)
        assertEquals("novelbins.bext", entriesFromObject[0].bextUrl)
    }

    @Test
    fun testSemVerComparison() {
        val cmp = com.halovoid.bunori.extension.api.models.ExtensionRepoEntry.Companion
        org.junit.Assert.assertTrue(cmp.isVersionNewer("1.0.1", "1.0.0"))
        org.junit.Assert.assertTrue(cmp.isVersionNewer("1.1.0", "1.0.9"))
        org.junit.Assert.assertTrue(cmp.isVersionNewer("2.0.0", "1.9.9"))
        org.junit.Assert.assertTrue(cmp.isVersionNewer("1.0.0", null))
        org.junit.Assert.assertFalse(cmp.isVersionNewer("1.0.0", "1.0.0"))
        org.junit.Assert.assertFalse(cmp.isVersionNewer("1.0.0", "1.0.1"))
        org.junit.Assert.assertFalse(cmp.isVersionNewer("1.0.9", "1.1.0"))
    }
}


