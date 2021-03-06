/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.kharada.kotlinmvpandroidtv.ui.playback

import android.app.Activity
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
import android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
import android.media.session.PlaybackState
import android.support.v4.app.FragmentActivity
import android.view.KeyEvent
import android.widget.VideoView
import com.android.kharada.kotlinmvpandroidtv.R
import com.android.kharada.kotlinmvpandroidtv.model.Movie
import com.bumptech.glide.Glide
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget

/**
 * PlaybackOverlayActivity for video playback that loads PlaybackOverlayFragment
 */
class PlaybackOverlayActivity : Activity(), PlaybackOverlayFragment.OnPlayPauseClickedListener {

    private var mVideoView: VideoView? = null
    private var mPlaybackState = LeanbackPlaybackState.IDLE
    private var mSession: MediaSession? = null

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.playback_controls)
        loadViews()
        setupCallbacks()
        mSession = android.media.session.MediaSession(this, "LeanbackSampleApp")
        mSession!!.setCallback(MediaSessionCallback())
        mSession!!.setFlags(FLAG_HANDLES_MEDIA_BUTTONS or FLAG_HANDLES_TRANSPORT_CONTROLS)

        mSession!!.isActive = true
    }

    public override fun onDestroy() {
        super.onDestroy()
        mVideoView!!.suspend()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val playbackOverlayFragment = fragmentManager.findFragmentById(R.id.playback_controls_fragment) as PlaybackOverlayFragment
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                playbackOverlayFragment.togglePlayback(false)
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                playbackOverlayFragment.togglePlayback(false)
                return true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (mPlaybackState == LeanbackPlaybackState.PLAYING) {
                    playbackOverlayFragment.togglePlayback(false)
                } else {
                    playbackOverlayFragment.togglePlayback(true)
                }
                return true
            }
            else -> return super.onKeyUp(keyCode, event)
        }
    }

    /**
     * Implementation of OnPlayPauseClickedListener
     */
    override fun onFragmentPlayPause(movie: Movie, position: Int, playPause: Boolean?) {
        mVideoView!!.setVideoPath(movie.videoUrl)

        if (position == 0 || mPlaybackState == LeanbackPlaybackState.IDLE) {
            setupCallbacks()
            mPlaybackState = LeanbackPlaybackState.IDLE
        }

        if (playPause!! && mPlaybackState != LeanbackPlaybackState.PLAYING) {
            mPlaybackState = LeanbackPlaybackState.PLAYING
            if (position > 0) {
                mVideoView!!.seekTo(position)
                mVideoView!!.start()
            }
        } else {
            mPlaybackState = LeanbackPlaybackState.PAUSED
            mVideoView!!.pause()
        }
        updatePlaybackState(position)
        updateMetadata(movie)
    }

    private fun updatePlaybackState(position: Int) {
        val stateBuilder = PlaybackState.Builder()
                .setActions(availableActions)
        var state = android.media.session.PlaybackState.STATE_PLAYING
        if (mPlaybackState == LeanbackPlaybackState.PAUSED) {
            state = PlaybackState.STATE_PAUSED
        }
        stateBuilder.setState(state, position.toLong(), 1.0f)
        mSession!!.setPlaybackState(stateBuilder.build())
    }

    private val availableActions: Long
        get() {
            var actions = PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackState.ACTION_PLAY_FROM_SEARCH

            if (mPlaybackState == LeanbackPlaybackState.PLAYING) {
                actions = actions or PlaybackState.ACTION_PAUSE
            }

            return actions
        }

    private fun updateMetadata(movie: Movie) {
        val metadataBuilder = MediaMetadata.Builder()

        val title = movie.title.replace("_", " -")

        metadataBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                movie.description)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
                movie.cardImageUrl)

        // And at minimum the title and artist for legacy support
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, title)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, movie.studio)

        Glide.with(this)
                .load(android.net.Uri.parse(movie.cardImageUrl))
                .asBitmap()
                .into(object : SimpleTarget<Bitmap>(500, 500) {
                    override fun onResourceReady(resource: Bitmap?, glideAnimation: GlideAnimation<in Bitmap>?) {
                        metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, resource)
                        mSession!!.setMetadata(metadataBuilder.build())
                    }
                })
    }

    private fun loadViews() {
        mVideoView = findViewById(com.android.kharada.kotlinmvpandroidtv.R.id.videoView) as android.widget.VideoView
        mVideoView!!.isFocusable = false
        mVideoView!!.isFocusableInTouchMode = false
    }

    private fun setupCallbacks() {

        mVideoView!!.setOnErrorListener { mp, what, extra ->
            var msg = ""
            if (extra == android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
                msg = getString(com.android.kharada.kotlinmvpandroidtv.R.string.video_error_media_load_timeout)
            } else if (what == android.media.MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                msg = getString(com.android.kharada.kotlinmvpandroidtv.R.string.video_error_server_inaccessible)
            } else {
                msg = getString(com.android.kharada.kotlinmvpandroidtv.R.string.video_error_unknown_error)
            }
            mVideoView!!.stopPlayback()
            mPlaybackState = com.android.kharada.kotlinmvpandroidtv.ui.playback.PlaybackOverlayActivity.LeanbackPlaybackState.IDLE
            false
        }

        mVideoView!!.setOnPreparedListener {
            if (mPlaybackState == com.android.kharada.kotlinmvpandroidtv.ui.playback.PlaybackOverlayActivity.LeanbackPlaybackState.PLAYING) {
                mVideoView!!.start()
            }
        }

        mVideoView!!.setOnCompletionListener { mPlaybackState = com.android.kharada.kotlinmvpandroidtv.ui.playback.PlaybackOverlayActivity.LeanbackPlaybackState.IDLE }

    }

    public override fun onResume() {
        super.onResume()
        mSession!!.isActive = true
    }

    public override fun onPause() {
        super.onPause()
        if (mVideoView!!.isPlaying) {
            if (!requestVisibleBehind(true)) {
                // Try to play behind launcher, but if it fails, stop playback.
                stopPlayback()
            }
        } else {
            requestVisibleBehind(false)
        }
    }

    override fun onStop() {
        super.onStop()
        mSession!!.release()
    }


    override fun onVisibleBehindCanceled() {
        super.onVisibleBehindCanceled()
    }

    private fun stopPlayback() {
        if (mVideoView != null) {
            mVideoView!!.stopPlayback()
        }
    }

    /*
     * List of various states that we can be in
     */
    enum class LeanbackPlaybackState {
        PLAYING, PAUSED, BUFFERING, IDLE
    }

    private inner class MediaSessionCallback : android.media.session.MediaSession.Callback()

    companion object {
        private val TAG = "PlaybackOverlayActivity"
    }
}
