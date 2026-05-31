package com.example.bargirl

import android.provider.BaseColumns

object ProfileContract {
    object ProfileEntry : BaseColumns {
        const val ID: String = "id"
        const val TABLE_NAME = "profiles"
        const val COLUMN_NAME_NICK_NAME = "nick_name"
        const val COLUMN_NAME_FULL_NAME = "full_name"
        const val COLUMN_NAME_STATUS = "status"
        const val COLUMN_NAME_AGE = "age"
        const val COLUMN_NAME_BAR = "bar"
        const val COLUMN_NAME_AVERAGE_RATING = "average_rating"
        const val COLUMN_NAME_KNOWN_BODY_COUNT = "known_body_count"
        const val COLUMN_NAME_LAST_KNOWN_ACTIVE = "last_known_active"
        const val COLUMN_NAME_BABIES = "babies"
        const val COLUMN_NAME_SCARES = "scares"
        const val COLUMN_NAME_HEALTH_REPORTED = "health_reported"
        const val COLUMN_NAME_AVERAGE_FINE = "average_fine"
        const val COLUMN_NAME_PROFILE_IMAGE_URI = "profile_image_uri"
    }
}