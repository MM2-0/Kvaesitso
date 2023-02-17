package de.mm20.launcher2.music

import android.media.session.PlaybackState
import android.media.session.PlaybackState.CustomAction
import android.os.Bundle
import android.util.Log

data class SupportedActions(
    val stop: Boolean = false,
    val skipToNext: Boolean = true,
    val skipToPrevious: Boolean = true,
    val fastForward: Boolean = false,
    val rewind: Boolean = false,
    val seekTo: Boolean = false,
    val setPlaybackSpeed: Boolean = false,
    val setRating: Boolean = false,
    val customActions: List<CustomAction> = emptyList()
) {
    constructor(actions: Long?, customActions: List<CustomAction>?) : this(
        stop = actions?.and(PlaybackState.ACTION_STOP) == PlaybackState.ACTION_STOP,
        skipToNext = actions?.and(PlaybackState.ACTION_SKIP_TO_NEXT) == PlaybackState.ACTION_SKIP_TO_NEXT,
        skipToPrevious = actions?.and(PlaybackState.ACTION_SKIP_TO_PREVIOUS) == PlaybackState.ACTION_SKIP_TO_PREVIOUS,
        fastForward = actions?.and(PlaybackState.ACTION_FAST_FORWARD) == PlaybackState.ACTION_FAST_FORWARD,
        rewind = actions?.and(PlaybackState.ACTION_REWIND) == PlaybackState.ACTION_REWIND,
        seekTo = actions?.and(PlaybackState.ACTION_SEEK_TO) == PlaybackState.ACTION_SEEK_TO,
        setPlaybackSpeed = actions?.and(PlaybackState.ACTION_SET_PLAYBACK_SPEED) == PlaybackState.ACTION_SET_PLAYBACK_SPEED,
        setRating = actions?.and(PlaybackState.ACTION_SET_RATING) == PlaybackState.ACTION_SET_RATING,
        customActions = customActions ?: emptyList(),
    ) {
        for (action in customActions ?: emptyList()) {
            Log.d("MM20", action.action.toString())
            val extras = action.extras ?: Bundle.EMPTY
            val keySet = extras.keySet()
            for (key in keySet) {
                Log.d("MM20", "$key: ${extras.get(key)}")
            }
        }
    }
}