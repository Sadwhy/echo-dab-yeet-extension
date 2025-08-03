package dev.brahmkshatriya.echo.extension.models

import dev.brahmkshatriya.echo.common.models.Artist as EchoArtist
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.extension.utils.IntToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ArtistResponse(
    val artist: Artist,
    val albums: List<Album>
) {
    fun toArtist(): EchoArtist {
        val albumList = albums.map { it.toAlbum() }
        return EchoArtist(
            id = artist.id,
            name = artist.name,
            cover = artist.image.high?.toImageHolder(),
            description = artist.biography?.content.orEmpty(),
            extras = mapOf(
                "similarArtistIds" to Json.encodeToString(artist.similarArtistIds),
                "slug" to artist.slug,
                "albumList" to Json.encodeToString(albumList),
                "isLoaded" to "true"
            )
        )
    }
}

@Serializable
data class Biography(
    val summary: String = "",
    val content: String = "",
    val source: String = "",
    val language: String = "en"
)

@Serializable
data class Artist(
    @Serializable(with = IntToString::class)
    val id: String,
    val name: String,
    val albumsCount: Int,
    val albumsAsPrimaryArtistCount: Int,
    val albumsAsPrimaryComposerCount: Int,
    val slug: String,
    val image: Images,
    val biography: Biography? = null,
    val similarArtistIds: List<String>,
    val information: String? = null
) {
    fun toArtist(): EchoArtist {
        return EchoArtist(
            id = id,
            name = name,
            cover = image.high?.toImageHolder(),
            description = biography?.content.orEmpty(),
            extras = mapOf(
                "similarArtistIds" to Json.encodeToString(similarArtistIds),
                "slug" to slug,
                "isLoaded" to "true"
            )
        )
    }
}