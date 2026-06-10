package com.homehub.dashboard.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.homehub.dashboard.R
import com.homehub.dashboard.databinding.ActivityFullscreenCameraBinding
import com.homehub.dashboard.util.RemoteLogger
import java.util.Locale

class FullscreenCameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFullscreenCameraBinding
    private var cameraPlayer: ExoPlayer? = null
    private var activeCameraStreamPath: String? = null
    private val attemptedCameraStreamPaths = linkedSetOf<String>()
    private var isMuted = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        isMuted = intent.getBooleanExtra(EXTRA_START_MUTED, true)
        binding.btnCameraMute.setOnClickListener { toggleMute() }
        binding.btnCameraClose.setOnClickListener { finish() }
        updateMuteIcon()
    }

    override fun onStart() {
        super.onStart()
        startCameraFeed()
    }

    override fun onStop() {
        super.onStop()
        stopCameraFeed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    private fun startCameraFeed() {
        if (!CameraStreamConfig.isConfigured()) {
            binding.tvCameraStatus.text = "camera not configured"
            binding.tvCameraOverlay.visibility = View.VISIBLE
            return
        }
        if (cameraPlayer != null) return

        binding.playerCamera.useController = false
        binding.tvCameraOverlay.visibility = View.VISIBLE
        attemptedCameraStreamPaths.clear()

        val player = ExoPlayer.Builder(this).build().also { exo ->
            exo.playWhenReady = true
            exo.volume = if (isMuted) 0f else 1f
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            binding.tvCameraStatus.text = "live view"
                            binding.tvCameraOverlay.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            binding.tvCameraStatus.text = "live"
                            binding.tvCameraOverlay.visibility = View.GONE
                        }
                        Player.STATE_ENDED -> {
                            binding.tvCameraStatus.text = "stream ended"
                            binding.tvCameraOverlay.visibility = View.VISIBLE
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val retryPath = CameraStreamConfig.streamPaths.firstOrNull { it !in attemptedCameraStreamPaths }
                    if (retryPath != null) {
                        binding.tvCameraStatus.text = "live view"
                        binding.tvCameraOverlay.visibility = View.VISIBLE
                        playCameraStream(exo, retryPath)
                        return
                    }
                    binding.tvCameraStatus.text = "camera error: ${error.errorCodeName.lowercase(Locale.US)}"
                    binding.tvCameraOverlay.visibility = View.VISIBLE
                    RemoteLogger.e("fullscreen camera rtsp error: ${error.errorCodeName} ${error.message.orEmpty()}")
                }
            })
            playCameraStream(exo, CameraStreamConfig.streamPaths.first())
        }

        cameraPlayer = player
        binding.playerCamera.player = player
    }

    private fun playCameraStream(player: ExoPlayer, streamPath: String) {
        activeCameraStreamPath = streamPath
        attemptedCameraStreamPaths += streamPath
        binding.tvCameraStatus.text = "live view"
        player.setMediaItem(MediaItem.fromUri(CameraStreamConfig.buildUri(streamPath)))
        player.prepare()
    }

    private fun stopCameraFeed() {
        binding.playerCamera.player = null
        cameraPlayer?.release()
        cameraPlayer = null
        activeCameraStreamPath = null
        attemptedCameraStreamPaths.clear()
    }

    private fun toggleMute() {
        isMuted = !isMuted
        cameraPlayer?.volume = if (isMuted) 0f else 1f
        updateMuteIcon()
    }

    private fun updateMuteIcon() {
        binding.btnCameraMute.setImageResource(
            if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
        )
        binding.btnCameraMute.contentDescription =
            if (isMuted) "Unmute camera" else "Mute camera"
    }

    companion object {
        const val EXTRA_START_MUTED = "start_muted"
    }
}
