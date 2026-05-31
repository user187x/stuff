package com.example.bargirl

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Rect
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.example.bargirl.ProfileContract.ProfileEntry
import de.hdodenhof.circleimageview.CircleImageView
import kotlin.math.abs

class MainActivity : AppCompatActivity(), GestureDetector.OnGestureListener {

    private lateinit var profileImageView: CircleImageView
    private lateinit var addImageButton: ImageButton
//    private lateinit var prevImageButton: ImageButton
//    private lateinit var nextImageButton: ImageButton
    private lateinit var healthStatusIcon: ImageView
    private lateinit var imageCountBadge: TextView
    private lateinit var statsAdapter: EditableStatsAdapter
    private lateinit var gestureDetector: GestureDetector
    private lateinit var rootContainer: ConstraintLayout // ADD THIS
    private lateinit var rootGestureDetector: GestureDetector // ADD THIS
    private lateinit var profileCardView: CardView // ADD THIS LINE




    // --- Data & State ---
    private lateinit var dbHelper: DatabaseHelper
    private var profiles = mutableListOf<Person>()
    private var currentProfileIndex = 0
    private var currentImageIndex = 0
    private var soundPool: SoundPool? = null
    private lateinit var vibrator: android.os.Vibrator

    // --- ActivityResultLauncher ---
    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            if (profiles.isEmpty()) return@let
            val currentProfile = profiles[currentProfileIndex]
            currentProfile.profileImageUris.add(it.toString())
            updateProfileInDb(currentProfile)
            currentImageIndex = currentProfile.profileImageUris.lastIndex
            displayCurrentImage()
            updateImageCounter()
        }
    }

    // --- Data Class ---
    data class Person(
        var id: Long = -1,
        var nickName: String,
        var fullName: String,
        var status: String,
        var age: Int,
        var bar: String,
        var averageRating: String,
        var knownBodyCount: Int,
        var lastKnownActive: String,
        var babies: Int,
        var scares: String,
        var healthReported: String,
        var averageFine: String,
        var profileImageUris: MutableList<String> = mutableListOf()
    )

    // --- Lifecycle Methods ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gestureDetector = GestureDetector(this, this)

        rootGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                if (abs(diffX) > 100 && abs(velocityX) > 100) {
                    if (diffX > 0) {
                        showPreviousProfile()
                    } else {
                        showNextProfile()
                    }
                    return true
                }
                return false
            }
        })

        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        initializeViews()
        setupRootTouchListener()

        setupButtonListeners()
        loadProfilesFromDb()

        // Initialize the adapter ONCE with an empty list
        // The callback will update the main profiles list
        statsAdapter = EditableStatsAdapter(mutableListOf()) { key, newValue ->
            if (profiles.isNotEmpty()) {
                val currentProfile = profiles[currentProfileIndex]
                when (key) {
                    "nickName" -> currentProfile.nickName = newValue
                    "fullName" -> currentProfile.fullName = newValue
                    "status" -> currentProfile.status = newValue
                    "age" -> currentProfile.age = newValue.toIntOrNull() ?: 0
                    "bar" -> currentProfile.bar = newValue
                    "averageRating" -> currentProfile.averageRating = newValue
                    "knownBodyCount" -> currentProfile.knownBodyCount = newValue.toIntOrNull() ?: 0
                    "lastKnownActive" -> currentProfile.lastKnownActive = newValue
                    "babies" -> currentProfile.babies = newValue.toIntOrNull() ?: 0
                    "scares" -> currentProfile.scares = newValue
                    "healthReported" -> currentProfile.healthReported = newValue
                    "averageFine" -> currentProfile.averageFine = newValue
                }
            }
        }

        val statsRecyclerView: RecyclerView = findViewById(R.id.stats_recycler_view)
        statsRecyclerView.adapter = statsAdapter
        statsRecyclerView.itemAnimator = null // Good for preventing flickers

        // --- Vibrator Initialization ---
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }

        val soundPool = SoundPool.Builder()
            .setMaxStreams(6) // The maximum number of simultaneous streams for this SoundPool
            .build()

        val soundId = soundPool.load(this, R.raw.beep, 1)

        if (profiles.isEmpty()) {
            addInitialDataToDb()
            loadProfilesFromDb()
        }

        if (profiles.isNotEmpty()) {
            displayProfile(currentProfileIndex)
        }

        val smashedButton: Button = findViewById(R.id.smashed_button)

        smashedButton.setOnClickListener {
            if (profiles.isNotEmpty()) {
                soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f)

                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))

                // Directly update the data source
                val currentProfile = profiles[currentProfileIndex]
                currentProfile.knownBodyCount++

                // Refresh the entire UI, including the RecyclerView
                displayProfile(currentProfileIndex)
            }
        }
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
        soundPool?.release()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRootTouchListener() {
        // This listener handles swipes on the main background
        rootContainer.setOnTouchListener { _, event ->
            // Create a Rect to store the screen bounds of the card
            val cardBounds = Rect()
            profileCardView.getHitRect(cardBounds)

            // Check if the touch event is inside the card's bounds
            if (cardBounds.contains(event.x.toInt(), event.y.toInt())) {
                // Touch is inside the card, let the image view's listener handle it
                false // We did not handle the event
            } else {
                // Touch is outside the card, use the root detector to check for a swipe
                rootGestureDetector.onTouchEvent(event)
                true // We are handling the event
            }
        }

        // This listener (which you already have) handles swipes on the image
        profileImageView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeViews() {
        rootContainer = findViewById(R.id.root_container)
        profileImageView = findViewById(R.id.profile_image)
        profileCardView = findViewById(R.id.profile_card)
        addImageButton = findViewById(R.id.imageButton)
        healthStatusIcon = findViewById(R.id.health_status_icon)
//        prevImageButton = findViewById(R.id.prev_image_button)
//        nextImageButton = findViewById(R.id.imageButton2)
        imageCountBadge = findViewById(R.id.image_count_badge)

        profileImageView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true // We are handling the event
        }
    }

    private fun setupButtonListeners() {
        //findViewById<Button>(R.id.prev_button).setOnClickListener { showPreviousProfile() }
       // findViewById<Button>(R.id.next_button).setOnClickListener { showNextProfile() }
        addImageButton.setOnClickListener { selectImageLauncher.launch("image/*") }
//        prevImageButton.setOnClickListener { showPreviousImage() }
//        nextImageButton.setOnClickListener { showNextImage() }
    }

    // --- Database Operations ---
    private fun addInitialDataToDb() {
        val initialProfiles = listOf(
            Person(nickName = "Fern", fullName = "Chananthicha Thidaporn", status = "Active", age = 25, bar = "Wrath", averageRating = "4/5", knownBodyCount = 24, lastKnownActive = "6/3/2025", babies = 1, scares = "Yes", healthReported = "No", averageFine = "฿2500"),
            Person(nickName = "Jenny", fullName = "Jiranapa Sriprom", status = "Inactive", age = 28, bar = "The Den", averageRating = "3/5", knownBodyCount = 15, lastKnownActive = "5/1/2025", babies = 0, scares = "No", healthReported = "Yes", averageFine = "฿1800"),
            Person(nickName = "Lisa", fullName = "Supawadee Ketkaew", status = "Active", age = 22, bar = "Paradise", averageRating = "5/5", knownBodyCount = 30, lastKnownActive = "6/25/2025", babies = 2, scares = "No", healthReported = "No", averageFine = "฿3000")
        )
        initialProfiles.forEach { addProfileToDb(it) }
    }

    private fun addProfileToDb(person: Person) {
        val db = dbHelper.writableDatabase
        val imageUrisString = person.profileImageUris.joinToString(",")
        val values = ContentValues().apply {
            put(ProfileEntry.COLUMN_NAME_NICK_NAME, person.nickName)
            put(ProfileEntry.COLUMN_NAME_FULL_NAME, person.fullName)
            put(ProfileEntry.COLUMN_NAME_STATUS, person.status)
            put(ProfileEntry.COLUMN_NAME_AGE, person.age)
            put(ProfileEntry.COLUMN_NAME_BAR, person.bar)
            put(ProfileEntry.COLUMN_NAME_AVERAGE_RATING, person.averageRating)
            put(ProfileEntry.COLUMN_NAME_KNOWN_BODY_COUNT, person.knownBodyCount)
            put(ProfileEntry.COLUMN_NAME_LAST_KNOWN_ACTIVE, person.lastKnownActive)
            put(ProfileEntry.COLUMN_NAME_BABIES, person.babies)
            put(ProfileEntry.COLUMN_NAME_SCARES, person.scares)
            put(ProfileEntry.COLUMN_NAME_HEALTH_REPORTED, person.healthReported)
            put(ProfileEntry.COLUMN_NAME_AVERAGE_FINE, person.averageFine)
            put(ProfileEntry.COLUMN_NAME_PROFILE_IMAGE_URI, imageUrisString)
        }
        db.insert(ProfileEntry.TABLE_NAME, null, values)
    }

    private fun updateProfileInDb(person: Person) {
        val db = dbHelper.writableDatabase
        val imageUrisString = person.profileImageUris.joinToString(",")
        val values = ContentValues().apply {
            put(ProfileEntry.COLUMN_NAME_PROFILE_IMAGE_URI, imageUrisString)
        }
        val selection = "${ProfileEntry.ID} = ?"
        val selectionArgs = arrayOf(person.id.toString())
        db.update(ProfileEntry.TABLE_NAME, values, selection, selectionArgs)
    }

    @SuppressLint("Range")
    private fun loadProfilesFromDb() {
        profiles.clear()
        val db = dbHelper.readableDatabase
        val cursor = db.query(ProfileEntry.TABLE_NAME, null, null, null, null, null, null)
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(ProfileEntry.ID))
                val nickName = getString(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_NICK_NAME))
                val fullName = getString(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_FULL_NAME))
                val status = getString(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_STATUS))
                val age = getInt(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_AGE))
                val bar = getString(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_BAR))
                val averageRating = getString(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_AVERAGE_RATING))
                val knownBodyCount = getInt(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_KNOWN_BODY_COUNT))
                val lastKnownActive = getString(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_LAST_KNOWN_ACTIVE))
                val babies = getInt(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_BABIES))
                val scares = getString(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_SCARES))
                val healthReported = getString(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_HEALTH_REPORTED))
                val averageFine = getString(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_AVERAGE_FINE))

                val imageUrisString = getString(getColumnIndexOrThrow(ProfileEntry.COLUMN_NAME_PROFILE_IMAGE_URI))
                val imageUris = if (imageUrisString?.isNotEmpty() == true) {
                    imageUrisString.split(",").toMutableList()
                } else {
                    mutableListOf()
                }

                profiles.add(Person(id, nickName, fullName, status, age, bar, averageRating, knownBodyCount, lastKnownActive, babies, scares, healthReported, averageFine, imageUris))
            }
        }
        cursor.close()
    }

    private fun updateImageCounter() {
        if (profiles.isEmpty()) {
            imageCountBadge.visibility = View.GONE
            return
        }

        val imageCount = profiles[currentProfileIndex].profileImageUris.size
        if (imageCount > 1) {
            imageCountBadge.text = imageCount.toString()
            imageCountBadge.visibility = View.VISIBLE
        } else {
            imageCountBadge.visibility = View.GONE
        }
    }

    private fun displayCurrentImage() {
        if (profiles.isEmpty()) return
        val currentProfile = profiles[currentProfileIndex]

        if (currentProfile.profileImageUris.isNotEmpty() && currentImageIndex in 0 until currentProfile.profileImageUris.size) {
            val imageUri = currentProfile.profileImageUris[currentImageIndex]
            profileImageView.setImageURI(imageUri.toUri())
        } else {
            profileImageView.setImageResource(R.drawable.placeholder_profile)
        }
    }

    // --- Navigation Logic ---
    private fun showNextProfile() {
        if (profiles.isNotEmpty()) {
            currentProfileIndex = (currentProfileIndex + 1) % profiles.size
            displayProfile(currentProfileIndex)
        }
    }

    private fun showPreviousProfile() {
        if (profiles.isNotEmpty()) {
            currentProfileIndex = (currentProfileIndex - 1 + profiles.size) % profiles.size
            displayProfile(currentProfileIndex)
        }
    }

    private fun showNextImage() {
        if (profiles.isEmpty()) return
        val imageUris = profiles[currentProfileIndex].profileImageUris
        if (imageUris.size > 1) {

            // Check if the current image is the last one before advancing
            val isWrappingAround = currentImageIndex == imageUris.lastIndex

            if (isWrappingAround) {
                shakeView(profileCardView)
            }

            currentImageIndex = (currentImageIndex + 1) % imageUris.size
            displayCurrentImage()
        }
    }

    private fun showPreviousImage() {
        if (profiles.isEmpty()) return
        val imageUris = profiles[currentProfileIndex].profileImageUris
        if (imageUris.size > 1) {

            // Check if the current image is the last one before advancing
            val isWrappingAround = currentImageIndex == imageUris.lastIndex

            if (isWrappingAround) {
                shakeView(profileCardView)
            }

            currentImageIndex = (currentImageIndex - 1 + imageUris.size) % imageUris.size
            displayCurrentImage()
        }
    }

    // Add this data class inside MainActivity
// It holds all the necessary info for an editable statistic
    data class EditableStat(
        val key: String, // A unique key to identify the stat (e.g., "nickName")
        val label: String,
        var value: String,
        val iconRes: Int,
        val inputType: Int // To set the keyboard type (e.g., text, number)
    )

    // Add this adapter class inside MainActivity
// It's built to handle the EditText changes
    class EditableStatsAdapter(

        private var stats: MutableList<EditableStat>, // Change to var and MutableList
        private val onStatChanged: (key: String, newValue: String) -> Unit
    ) : RecyclerView.Adapter<EditableStatsAdapter.StatViewHolder>() {

        inner class StatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.stat_icon)
            val value: EditText = view.findViewById(R.id.stat_value)
            val label: TextView = view.findViewById(R.id.stat_label)
            var textWatcher: TextWatcher? = null
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatViewHolder {
            // Get the parent RecyclerView to calculate dimensions
            val recyclerView = parent as RecyclerView

            // Calculate the height each row should have to fit perfectly on screen.
            // We have 12 items in a 3-column grid, which means exactly 4 rows.
            val itemHeight = recyclerView.height / 4

            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_stat_card, parent, false)

            // Apply the calculated height to the card
            view.layoutParams.height = itemHeight

            return StatViewHolder(view)
        }

        override fun onBindViewHolder(holder: StatViewHolder, position: Int) {
            val stat = stats[position]

            // Remove the old watcher to prevent updates from recycled views
            holder.value.removeTextChangedListener(holder.textWatcher)

            // Set the data
            holder.icon.setImageResource(stat.iconRes)
            holder.value.setText(stat.value)
            holder.value.inputType = stat.inputType
            holder.label.text = stat.label

            // Add the new watcher
            holder.textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newValue = s.toString()
                    // Update the local data to prevent cursor jumps
                    if (stat.value != newValue) {
                        stat.value = newValue
                        onStatChanged(stat.key, newValue)
                    }
                }
            }
            holder.value.addTextChangedListener(holder.textWatcher)
        }

        fun updateData(newStats: List<EditableStat>) {
            stats.clear()
            stats.addAll(newStats)
            notifyDataSetChanged() // Refresh the whole list
        }

        override fun getItemCount() = stats.size
    }

    private fun shakeView(view: View) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
            .setDuration(400)
            .start()
    }

    private fun displayProfile(index: Int) {
        if (index !in 0 until profiles.size) return

        val profile = profiles[index]

        val statList = listOf(
            // ... your list of EditableStat objects remains the same
            EditableStat("nickName", "Nick Name", profile.nickName, android.R.drawable.ic_menu_view, InputType.TYPE_CLASS_TEXT),
            EditableStat("fullName", "Full Name", profile.fullName, android.R.drawable.ic_menu_myplaces, InputType.TYPE_CLASS_TEXT),
            EditableStat("status", "Status", profile.status, android.R.drawable.ic_menu_compass, InputType.TYPE_CLASS_TEXT),
            EditableStat("age", "Age", profile.age.toString(), android.R.drawable.ic_menu_my_calendar, InputType.TYPE_CLASS_NUMBER),
            EditableStat("bar", "Bar", profile.bar, android.R.drawable.ic_menu_compass, InputType.TYPE_CLASS_TEXT),
            EditableStat("averageRating", "Average Rating", profile.averageRating, android.R.drawable.star_on, InputType.TYPE_CLASS_TEXT),
            EditableStat("knownBodyCount", "Known Body Count", profile.knownBodyCount.toString(), android.R.drawable.ic_dialog_info, InputType.TYPE_CLASS_NUMBER),
            EditableStat("lastKnownActive", "Last Known Active", profile.lastKnownActive, android.R.drawable.ic_menu_recent_history, InputType.TYPE_CLASS_DATETIME),
            EditableStat("babies", "Babies", profile.babies.toString(), android.R.drawable.sym_def_app_icon, InputType.TYPE_CLASS_NUMBER),
            EditableStat("scares", "Scares", profile.scares, android.R.drawable.ic_dialog_alert, InputType.TYPE_CLASS_TEXT),
            EditableStat("healthReported", "Health Reported", profile.healthReported, android.R.drawable.ic_menu_report_image, InputType.TYPE_CLASS_TEXT),
            EditableStat("averageFine", "Average Fine", profile.averageFine, android.R.drawable.ic_menu_send, InputType.TYPE_CLASS_TEXT)
        )

        // UPDATE THE ADAPTER INSTEAD OF CREATING A NEW ONE
        statsAdapter.updateData(statList)

        val statsRecyclerView: RecyclerView = findViewById(R.id.stats_recycler_view)
        statsRecyclerView.adapter = statsAdapter
        // Optional: To prevent flickering on updates
        statsRecyclerView.itemAnimator = null


        // --- Keep the rest of the original logic ---
        currentImageIndex = 0
        displayCurrentImage()
        updateImageCounter()

        if (profile.healthReported.equals("Yes", ignoreCase = true)) {
            healthStatusIcon.visibility = View.VISIBLE
            healthStatusIcon.setImageResource(android.R.drawable.stat_notify_error)
            healthStatusIcon.setColorFilter(getColor(android.R.color.holo_orange_dark))
        } else {
            healthStatusIcon.visibility = View.GONE
        }
    }

    // --- Gesture Detection Logic ---

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 == null) return false

        val diffX = e2.x - e1.x
        val diffY = e2.y - e1.y

        // We only care about horizontal swipes
        if (abs(diffX) > abs(diffY)) {
            // Check if the swipe is significant enough
            if (abs(diffX) > 100 && abs(velocityX) > 100) {

                if (diffX > 0) {
                    // Swipe Right
                    showPreviousImage()
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else {
                    // Swipe Left
                    showNextImage()
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                }
                return true
            }
        }
        return false
    }

    // You must implement the other methods of the interface, but they can be empty
    override fun onDown(e: MotionEvent): Boolean = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean = true
    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean = true
    override fun onLongPress(e: MotionEvent) {}
}