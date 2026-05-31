package com.xxx.music

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chibde.visualizer.BarVisualizer
import com.example.mp3visualizer.databinding.ActivityMainBinding
import com.google.android.material.slider.Slider
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // View Binding to easily access UI elements
    private lateinit var binding: ActivityMainBinding

    // Android's primary class for playing audio
    private var mediaPlayer: MediaPlayer? = null

    // Handler for updating the seek bar progress periodically
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    // Activity Result Launcher for handling the file picker intent
    private val getAudioFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // When a file is selected, load it
            loadAudio(it)
        }
    }

    // Activity Result Launcher for handling the permission request
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // If permission is granted, launch the file picker
                openFilePicker()
            } else {
                // If permission is denied, inform the user
                Toast.makeText(this, "Permission denied. Cannot load audio files.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate the layout and set it as the content view
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize UI components and listeners
        setupUI()
    }

    /**
     * Sets up the initial state of UI elements and registers event listeners.
     */
    private fun setupUI() {
        // Initially, playback controls are disabled until a file is loaded
        binding.playPauseButton.isEnabled = false
        binding.seekBar.isEnabled = false

        // Eject button will always open the file picker in this implementation
        binding.ejectButton.setOnClickListener {
            checkPermissionAndOpenFilePicker()
        }

        // Play/Pause button toggles playback
        binding.playPauseButton.setOnClickListener {
            togglePlayback()
        }

        // SeekBar allows the user to scrub through the audio
        binding.seekBar.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // Pause updates while the user is scrubbing
                mediaPlayer?.pause()
                handler.removeCallbacks(runnable)
            }

            override fun onStopTrackingTouch(slider: Slider) {
                // When scrubbing stops, seek to the new position and resume playback
                mediaPlayer?.seekTo(slider.value.toInt())
                if (mediaPlayer?.isPlaying == false) {
                    mediaPlayer?.start()
                    updatePlayPauseButton(true)
                    startSeekBarUpdate()
                }
            }
        })

        // Volume slider controls the media player's volume
        binding.volumeSlider.addOnChangeListener { _, value, _ ->
            mediaPlayer?.setVolume(value, value)
        }
    }

    /**
     * Checks if storage permission is granted. If not, requests it.
     * If granted, opens the file picker.
     */
    private fun checkPermissionAndOpenFilePicker() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                openFilePicker()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    /**
     * Launches the system file picker to select an audio file.
     */
    private fun openFilePicker() {
        getAudioFile.launch("audio/*")
    }

    /**
     * Loads and prepares the selected audio file for playback.
     * @param uri The URI of the selected audio file.
     */
    private fun loadAudio(uri: Uri) {
        // Stop and release any existing media player instance
        stopAndReleasePlayer()

        mediaPlayer = MediaPlayer().apply {
            try {
                // Set the data source and prepare the player
                setDataSource(applicationContext, uri)
                prepareAsync() // Prepare asynchronously to avoid blocking the UI thread
                setOnPreparedListener { mp ->
                    // Once prepared, enable controls and set up the visualizer
                    binding.playPauseButton.isEnabled = true
                    binding.seekBar.isEnabled = true
                    binding.seekBar.valueTo = mp.duration.toFloat()
                    binding.fileNameTextView.text = uri.path?.substringAfterLast("/") ?: "Audio File"

                    // Link the visualizer to the media player session
                    val audioSessionId = mp.audioSessionId
                    if (audioSessionId != -1) {
                        binding.barVisualizer.setAudioSessionId(audioSessionId)
                    }

                    // Start playback automatically
                    togglePlayback()
                }
                setOnCompletionListener {
                    // When playback completes, reset the UI
                    stopAndReleasePlayer()
                    binding.fileNameTextView.text = "Select an audio file"
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(applicationContext, "Error playing file", Toast.LENGTH_SHORT).show()
                    stopAndReleasePlayer()
                    true
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(applicationContext, "Error loading file", Toast.LENGTH_SHORT).show()
                stopAndReleasePlayer()
            }
        }
    }

    /**
     * Toggles the audio playback state between playing and paused.
     */
    private fun togglePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                handler.removeCallbacks(runnable)
                updatePlayPauseButton(false)
            } else {
                it.start()
                startSeekBarUpdate()
                updatePlayPauseButton(true)
            }
        }
    }

    /**
     * Starts a runnable to periodically update the SeekBar's progress.
     */
    private fun startSeekBarUpdate() {
        runnable = Runnable {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    binding.seekBar.value = it.currentPosition.toFloat()
                    handler.postDelayed(runnable, 1000)
                }
            }
        }
        handler.post(runnable)
    }

    /**
     * Updates the play/pause button icon based on the playback state.
     * @param isPlaying True if audio is currently playing.
     */
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (isPlaying) {
            binding.playPauseButton.setIconResource(R.drawable.ic_pause)
        } else {
            binding.playPauseButton.setIconResource(R.drawable.ic_play)
        }
    }

    /**
     * Stops playback and releases MediaPlayer resources.
     */
    private fun stopAndReleasePlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        binding.barVisualizer.release()
        binding.playPauseButton.isEnabled = false
        binding.seekBar.isEnabled = false
        binding.seekBar.value = 0f
        updatePlayPauseButton(false)
        handler.removeCallbacks(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure the media player is released when the app is destroyed
        stopAndReleasePlayer()
    }
}
