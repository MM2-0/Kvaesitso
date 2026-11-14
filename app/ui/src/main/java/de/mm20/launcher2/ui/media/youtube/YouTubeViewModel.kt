package de.mm20.launcher2.ui.media.youtube
import de.mm20.launcher2.i18n.R as I18nR

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import de.mm20.launcher2.comms.youtube.*
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class YouTubeViewModel : ViewModel(), KoinComponent {
    private val repository: YouTubeRepository by inject()
    val library: YouTubeLibrary by inject()
    val results = MutableStateFlow<Map<Boolean, List<YouTubeEntry>>>(emptyMap())
    val searching = MutableStateFlow(false)
    val resolving = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val controller = MutableStateFlow<MediaController?>(null)
    val nowPlaying = MutableStateFlow<MediaItem?>(null)
    val playing = MutableStateFlow(false)
    val buffering = MutableStateFlow(false)
    private var searchJob: Job? = null
    private var playJob: Job? = null
    private var connectJob: Deferred<MediaController>? = null
    private var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var playbackErrorMessage: String? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            nowPlaying.value = player.currentMediaItem
            playing.value = player.isPlaying
            buffering.value = player.playbackState == Player.STATE_BUFFERING
        }
        override fun onPlayerError(error: PlaybackException) { message.value = playbackErrorMessage }
    }
    fun openLink(context: Context, url: String, music: Boolean, autoplay: Boolean = true) {
        val parsed = de.mm20.launcher2.comms.youtube.flow.parseYouTubeLink(url)
        when (parsed) {
            is de.mm20.launcher2.comms.youtube.flow.YouTubeLink.Playlist,
            is de.mm20.launcher2.comms.youtube.flow.YouTubeLink.Album -> selectPlaylist(context, url, music)
            is de.mm20.launcher2.comms.youtube.flow.YouTubeLink.Video,
            is de.mm20.launcher2.comms.youtube.flow.YouTubeLink.Short -> {
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    searching.value = true
                    message.value = null
                    try {
                        val entries = repository.search(url, music)
                        results.value = results.value + (music to entries)
                        if (autoplay) entries.firstOrNull()?.let { play(context, it, music, entries) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message.value = context.getString(I18nR.string.telos_youtube_play_failed) }
                    finally { searching.value = false }
                }
            }
            is de.mm20.launcher2.comms.youtube.flow.YouTubeLink.Search -> search(context, parsed.query, music)
            null -> loadHome(context, music)
            else -> search(context, url, music)
        }
    }

    fun search(context: Context, query: String, music: Boolean) {
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching.value = true; message.value = null
            try { results.value = results.value + (music to repository.search(query.trim(), music)) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = context.getString(I18nR.string.telos_youtube_search_failed) }
            finally { searching.value = false }
        }
    }
    private suspend fun connect(context: Context): MediaController {
        controller.value?.let { return it }
        connectJob?.let { return it.await() }
        val app = context.applicationContext
        playbackErrorMessage = app.getString(I18nR.string.telos_youtube_player_error)
        val deferred = viewModelScope.async {
            val pending = MediaController.Builder(app, SessionToken(app, ComponentName(app, YouTubePlayerService::class.java))).buildAsync()
            future = pending
            val c = suspendCancellableCoroutine<MediaController> { continuation ->
                pending.addListener({
                    try { if (continuation.isActive) continuation.resume(pending.get()) }
                    catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }, ContextCompat.getMainExecutor(app))
                continuation.invokeOnCancellation { MediaController.releaseFuture(pending) }
            }
            controller.value = c; c.addListener(listener)
            nowPlaying.value = c.currentMediaItem; playing.value = c.isPlaying
            c
        }
        connectJob = deferred
        try { return deferred.await() } finally { connectJob = null }
    }
    val userPlaylists = MutableStateFlow<List<YouTubePlaylistSummary>>(emptyList())
    val selectedPlaylistTracks = MutableStateFlow<Pair<String, List<YouTubeEntry>>?>(null)
    val isLoadingPlaylists = MutableStateFlow(false)
    val sessionExpired = MutableStateFlow(false)

    fun loadUserPlaylists(context: Context) {
        viewModelScope.launch {
            if (YouTubeAccountManager.isExpired(context)) {
                sessionExpired.value = true
                userPlaylists.value = emptyList()
                return@launch
            }
            if (!kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repository.isLoggedIn() }) {
                userPlaylists.value = emptyList()
                return@launch
            }
            sessionExpired.value = false
            isLoadingPlaylists.value = true
            try {
                userPlaylists.value = repository.fetchUserPlaylists()
                if (YouTubeAccountManager.isExpired(context)) {
                    sessionExpired.value = true
                    userPlaylists.value = emptyList()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message.value = context.getString(I18nR.string.telos_youtube_search_failed) }
            finally {
                isLoadingPlaylists.value = false
            }
        }
    }

    fun selectPlaylist(context: Context, url: String, music: Boolean = true) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching.value = true
            message.value = null
            try {
                val pair = repository.fetchPlaylist(url)
                selectedPlaylistTracks.value = pair
                results.value = results.value + (music to pair.second)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message.value = context.getString(I18nR.string.telos_youtube_search_failed)
            } finally {
                searching.value = false
            }
        }
    }

    fun loadHome(context: Context, music: Boolean = false, refresh: Boolean = false) {
        if (!refresh && results.value[music]?.isNotEmpty() == true) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching.value = true; message.value = null
            try { results.value = results.value + (music to if (music) repository.fetchMusicHome() else repository.fetchHomeFeed()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message.value = context.getString(I18nR.string.telos_youtube_search_failed) }
            finally { searching.value = false }
        }
    }

    fun reconnect(context: Context) { 
        viewModelScope.launch { 
            runCatching { connect(context) }
            loadUserPlaylists(context)
        } 
    }
    fun play(context: Context, entry: YouTubeEntry, music: Boolean, tracks: List<YouTubeEntry> = listOf(entry)) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            resolving.value = true; message.value = null
            try {
                val playback = repository.resolve(entry, music)
                val c = connect(context)
                library.remember(playback.entry, music)
                val extras = Bundle().apply {
                    putBoolean("audioOnly", playback.audioOnly)
                    putString(YouTubePlayerService.EXTRA_QUEUE, withContext(Dispatchers.Default) { YouTubeQueue.encode(tracks) })
                    putInt(YouTubePlayerService.EXTRA_QUEUE_INDEX, tracks.indexOfFirst { it.url == entry.url })
                    playback.audioUrl?.let { putString(YouTubePlayerService.EXTRA_AUDIO_URL, it) }
                }
                val metadata = MediaMetadata.Builder().setTitle(playback.entry.title).setArtist(playback.entry.artist)
                    .setArtworkUri(playback.entry.thumbnail?.let(Uri::parse)).setExtras(extras).build()
                c.setMediaItem(MediaItem.Builder().setMediaId(entry.url).setUri(playback.streamUrl).setMediaMetadata(metadata).build())
                c.prepare(); c.play()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = context.getString(I18nR.string.telos_youtube_play_failed) }
            finally { resolving.value = false }
        }
    }
    fun loadOnlinePlaylist(context: Context, url: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching.value = true; message.value = null
            try {
                val pair = repository.fetchPlaylist(url)
                results.value = results.value + (true to pair.second)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = context.getString(I18nR.string.telos_youtube_search_failed) }
            finally { searching.value = false }
        }
    }

    fun importPlaylistUrl(url: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val pair = repository.fetchPlaylist(url)
                val name = pair.first
                val entries = pair.second
                if (entries.isNotEmpty()) {
                    entries.forEach { library.add(name, it) }
                    onDone(true)
                } else {
                    onDone(false)
                }
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    fun artistRadio(context: Context, entry: YouTubeEntry) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching.value = true; message.value = null
            try {
                val tracks = repository.artistRadio(entry)
                results.value = results.value + (true to tracks)
                if (tracks.isNotEmpty()) play(context, tracks.first(), true, tracks)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = context.getString(I18nR.string.telos_youtube_radio_failed) }
            finally { searching.value = false }
        }
    }

    fun moodRadio(context: Context, entry: YouTubeEntry, mood: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching.value = true; message.value = null
            try {
                val tracks = repository.search("${entry.artist} $mood music", true)
                results.value = results.value + (true to tracks)
                if (tracks.isNotEmpty()) play(context, tracks.first(), true, tracks)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = context.getString(I18nR.string.telos_youtube_search_failed) }
            finally { searching.value = false }
        }
    }
    fun toggle() { controller.value?.let { if (it.playWhenReady) it.pause() else it.play() } }
    fun stop() { controller.value?.run { stop(); clearMediaItems() }; nowPlaying.value = null }
    override fun onCleared() {
        controller.value?.removeListener(listener)
        future?.let(MediaController::releaseFuture)
        controller.value = null
    }
}
