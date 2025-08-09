package dev.brahmkshatriya.echo.extension

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.EchoMediaItem.toShelf
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.network.ApiService
import okhttp3.OkHttpClient

class DabYeetExtension : ExtensionClient, SearchFeedClient, TrackClient, AlbumClient, ArtistClient, ShareClient {

    private val client by lazy { OkHttpClient.Builder().build() }

    private val api = ApiService(client)

    // ===== Settings ===== //

    override suspend fun onExtensionSelected() {}

    override suspend fun onInitialize() {}

    //==== SearchFeedClient ====//

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        if (query.isBlank()) {
            return Feed(pagedData = PagedData.empty<Shelf>() )
        }

        val paged = PagedData.Single<Shelf> {
            
            val (albums, tracks) = defaultSearch(query)

            val albumShelf = Shelf.Lists.Items(
                title = "Albums",
                list = albums,
                type = Shelf.Lists.Type.Linear
            )

            val trackShelf = Shelf.Lists.Tracks(
                title = "Songs",
                subtitle = "Top Results",
                list = tracks,
                isNumbered = true,
                type = Shelf.Lists.Type.Grid
            )

            listOf(
                albumShelf,
                trackShelf
            )
        }

        return Feed(pagedData = paged)
    }

    private suspend fun defaultSearch(query: String, limit: Pair<Int, Int> = 0 to 0): Pair<List<EchoMediaItem>, List<Track>> = coroutineScope {
        val (trackLimit, albumLimit) = limit

        val albumsDeferred = async { api.search(query, albumLimit, MediaType.Album.type) }
        val tracksDeferred = async { api.search(query, trackLimit, MediaType.Track.type) }

        val albumResponse = albumsDeferred.await()
        val trackResponse = tracksDeferred.await()

        val albums = albumResponse.albums?.map { it.toAlbum().toShelf() } ?: emptyList()
        val tracks = trackResponse.tracks?.map { it.toTrack() } ?: emptyList()

        albums to tracks
    }

    enum class MediaType(val type: String) {
        Track("track"),
        Album("album"),
        Artist("artist"),
    }

    // ====== TrackClient ======= //

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track = track

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        val stream = api.getStream(streamable.id)
        return stream.url.toServerMedia()
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null
    
    // ====== AlbumClient ====== //

    override suspend fun loadAlbum(album: Album): Album {
        if (album.isLoaded()) {
            return album
        } else {
            return api.getAlbum(album.id).album.toAlbum()
        }
    }

    override suspend fun loadTracks(album: Album): Feed<Track>? {
        val albumList = api.getAlbum(album.id).album.tracks?.map { it.toTrack() }.orEmpty()
        if (albumList.isNotEmpty()) {
            return albumList.toFeed()
        }
        return null
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = PagedData.empty<Shelf>()

    // ====== ArtistClient ===== //
    
    override suspend fun loadArtist(artist: Artist): Artist {
        if (artist.isLoaded()) {
            return artist
        } else {
            return api.getArtist(artist.id).toArtist()
        }
    }

    override suspend fun loadFeed(artist: Artist) : Feed<Shelf> = listOf<Shelf>().toFeed()

    // ====== ShareClient ===== //

    override suspend fun onShare(item: EchoMediaItem): String {
        return when(item) {
            is EchoMediaItem.TrackItem -> "https://www.qobuz.com/us-en/album/${item.extras["albumId"]}"
            is EchoMediaItem.Lists.AlbumItem -> "https://www.qobuz.com/us-en/album/${item.id}"
            is EchoMediaItem.Profile.ArtistItem -> {
                val id = item.id
                val slug = item.extras["slug"]
                "https://www.qobuz.com/us-en/interpreter/$slug/$id"
            }
            is EchoMediaItem.Lists.PlaylistItem -> throw ClientException.NotSupported("TODO: Playlist sharing")
            is EchoMediaItem.Lists.RadioItem -> throw ClientException.NotSupported("Will not be implemented")
            is EchoMediaItem.Profile.UserItem -> throw ClientException.NotSupported("Will not be implemented")
        }
    }

    // ===== Utils ===== //

    private fun Any.isLoaded(): Boolean {
        return when (this) {
            is Track -> extras["isLoaded"] == "true"
            is Album -> extras["isLoaded"] == "true"
            is Artist -> extras["isLoaded"] == "true"
            else -> throw TypeCastException("Type mismatch: expected Echo Model but found ${this::class.simpleName}")
        }
    }

}