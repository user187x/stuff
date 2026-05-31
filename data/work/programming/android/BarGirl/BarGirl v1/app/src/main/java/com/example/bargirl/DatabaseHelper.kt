package com.example.bargirl

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.bargirl.ProfileContract.ProfileEntry

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "BarGirl.db"

        // Corrected the string initialization to use concatenation for compile-time constants
        private const val SQL_CREATE_ENTRIES =
            "CREATE TABLE " + ProfileEntry.TABLE_NAME + " (" +
                    ProfileEntry.ID + " INTEGER PRIMARY KEY," +
                    ProfileEntry.COLUMN_NAME_NICK_NAME + " TEXT," +
                    ProfileEntry.COLUMN_NAME_FULL_NAME + " TEXT," +
                    ProfileEntry.COLUMN_NAME_STATUS + " TEXT," +
                    ProfileEntry.COLUMN_NAME_AGE + " INTEGER," +
                    ProfileEntry.COLUMN_NAME_BAR + " TEXT," +
                    ProfileEntry.COLUMN_NAME_AVERAGE_RATING + " TEXT," +
                    ProfileEntry.COLUMN_NAME_KNOWN_BODY_COUNT + " INTEGER," +
                    ProfileEntry.COLUMN_NAME_LAST_KNOWN_ACTIVE + " TEXT," +
                    ProfileEntry.COLUMN_NAME_BABIES + " INTEGER," +
                    ProfileEntry.COLUMN_NAME_SCARES + " TEXT," +
                    ProfileEntry.COLUMN_NAME_HEALTH_REPORTED + " TEXT," +
                    ProfileEntry.COLUMN_NAME_AVERAGE_FINE + " TEXT," +
                    ProfileEntry.COLUMN_NAME_PROFILE_IMAGE_URI + " TEXT)"

        // Corrected the string initialization
        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + ProfileEntry.TABLE_NAME
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply discard the data and start over
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }
}