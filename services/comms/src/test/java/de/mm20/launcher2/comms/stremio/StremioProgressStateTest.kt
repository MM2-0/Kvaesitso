package de.mm20.launcher2.comms.stremio

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class StremioProgressStateTest {
    private fun progress(position: Long = 2500, time: Long = 2500, completed: Boolean = false) =
        WatchProgress("movie", "tt1", "Title", null, "tt1", "Title", 2000, null,
            positionMs = position, durationMs = 10000, timeWatchedMs = time, completed = completed)

    @Test fun writesActualMillisecondsAndPreservesUnknownServerFields() {
        val original = JSONObject("""{"_id":"tt1","custom":17,"state":{"noNotif":true,"watched":"keep"}}""")
        val result = StremioProgressState.update(original, progress())!!
        assertEquals(2500L, result.getJSONObject("state").getLong("timeOffset"))
        assertEquals(10000L, result.getJSONObject("state").getLong("duration"))
        assertEquals(17, result.getInt("custom"))
        assertTrue(result.getJSONObject("state").getBoolean("noNotif"))
        assertEquals("keep", result.getJSONObject("state").getString("watched"))
        assertFalse(original.getJSONObject("state").has("timeOffset"))
    }

    @Test fun seekingNearTheEndIsNotEvidenceOfTimeWatched() {
        val state = StremioProgressState.update(JSONObject(), progress(position = 9500, time = 1000))!!.getJSONObject("state")
        assertEquals(0, state.getInt("flaggedWatched"))
        assertEquals(9500L, state.getLong("timeOffset"))
    }

    @Test fun crossedWatchedThresholdCountsOnceAcrossRepeatedUpdates() {
        val once = StremioProgressState.update(JSONObject(), progress(position = 8000, time = 7500))!!
        val twice = StremioProgressState.update(once, progress(position = 8500, time = 8000))!!
        assertEquals(1L, twice.getJSONObject("state").getLong("timesWatched"))
        assertEquals(1, twice.getJSONObject("state").getInt("flaggedWatched"))
    }

    @Test fun completeClearsResumeButRetainsWatchedEvidence() {
        val result = StremioProgressState.update(JSONObject(), progress(position = 10000, time = 10000, completed = true))!!
        assertEquals(0L, result.getJSONObject("state").getLong("timeOffset"))
        assertEquals(1, result.getJSONObject("state").getInt("flaggedWatched"))
    }

    @Test fun olderDevicePositionCannotOverwriteNewerServerProgress() {
        val item = JSONObject().put("state", JSONObject().put("lastWatched", Instant.ofEpochMilli(3000).toString()).put("timeOffset", 7000))
        assertNull(StremioProgressState.update(item, progress()))
        assertEquals(7000, item.getJSONObject("state").getInt("timeOffset"))
    }

    @Test fun addingEarlierAndLaterEpisodesPreservesExistingWatchBits() {
        val ids = (1..12).map { "tt1:1:$it" }
        val first = StremioProgressState.markEpisode(null, ids.drop(5), ids[7])!!
        val earlier = StremioProgressState.markEpisode(first, ids, ids[0])!!
        val later = StremioProgressState.markEpisode(earlier, ids, ids[11])!!
        assertEquals(setOf(ids[0], ids[7], ids[11]), StremioWatched(videos = later).episodes(ids))
    }

    @Test fun missingMetadataAnchorNeverErasesServerWatchedData() {
        val field = StremioProgressState.markEpisode(null, listOf("other"), "other")!!
        assertEquals(field, StremioProgressState.markEpisode(field, listOf("tt1:1:1"), "tt1:1:1"))
    }

    @Test fun resumeUiPrefersMillisecondOffsetOverPercent() {
        val ui = StremioProgressState.resumeUi(2500, 10000, 80.0)!!
        assertEquals(0.25f, ui.fraction, 0.001f)
        assertEquals(7500L, ui.remainingMs)
        assertEquals(25, ui.percent)
    }

    @Test fun resumeUiFallsBackToPercentWhenDurationUnknown() {
        val ui = StremioProgressState.resumeUi(0, 0, 33.3)!!
        assertEquals(0.333f, ui.fraction, 0.01f)
        assertNull(ui.remainingMs)
        assertEquals(33, ui.percent)
        assertNull(StremioProgressState.resumeUi(0, 0, null))
        assertNull(StremioProgressState.resumeUi(0, 0, Double.NaN))
    }

    @Test fun videoOrderMatchesStremioSeasonEpisodeReleaseOrder() {
        val videos = listOf(Video("third", "", season = 1, episode = 2), Video("second", "", season = 1, episode = 1), Video("first", ""))
        assertEquals(listOf("first", "second", "third"), StremioProgressState.orderedVideoIds(videos))
    }
    @Test fun olderWatchedEpisodeMergesWithoutOverwritingNewerResume() {
        val ids = listOf("tt1:1:1", "tt1:1:2")
        val original = JSONObject().put("state", JSONObject().put("video_id", ids[1]).put("timeOffset", 4500)
            .put("duration", 10000).put("timeWatched", 4000).put("lastWatched", Instant.ofEpochMilli(3000).toString()))
        val p = progress(position = 10000, time = 10000, completed = true).copy(type = "series", videoId = ids[0], videoIds = ids)
        val state = StremioProgressState.update(original, p)!!.getJSONObject("state")
        assertEquals(ids[1], state.getString("video_id"))
        assertEquals(4500, state.getInt("timeOffset"))
        assertEquals(4000, state.getInt("timeWatched"))
        assertTrue(ids[0] in StremioWatched(videos = state.getString("watched")).episodes(ids))
    }

    @Test fun completedEpisodeAdvancesOnlyToAValidatedFollowingVideo() {
        val ids = listOf("tt1:1:1", "tt1:1:2")
        val p = progress(position = 10000, time = 10000, completed = true).copy(type = "series", videoId = ids[0], videoIds = ids, nextVideoId = ids[1])
        val state = StremioProgressState.update(JSONObject(), p)!!.getJSONObject("state")
        assertEquals(ids[1], state.getString("video_id"))
        assertEquals(1L, state.getLong("timeOffset"))
        assertEquals(0L, state.getLong("duration"))
        assertTrue(ids[0] in StremioWatched(videos = state.getString("watched")).episodes(ids))
        val invalid = StremioProgressState.update(JSONObject(), p.copy(nextVideoId = "unknown"))!!.getJSONObject("state")
        assertEquals(ids[0], invalid.getString("video_id"))
        assertEquals(0L, invalid.getLong("timeOffset"))
    }

}
