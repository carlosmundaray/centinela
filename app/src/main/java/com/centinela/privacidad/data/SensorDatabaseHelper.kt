package com.centinela.privacidad.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SensorDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_EVENTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SENSOR_TYPE TEXT NOT NULL,
                $COL_SENSOR_DETAIL TEXT NOT NULL,
                $COL_PACKAGE_NAME TEXT,
                $COL_APP_NAME TEXT NOT NULL,
                $COL_CONFIDENCE TEXT NOT NULL,
                $COL_REASON TEXT NOT NULL,
                $COL_START_TIME INTEGER NOT NULL,
                $COL_END_TIME INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX idx_sensor_events_start_time ON $TABLE_EVENTS ($COL_START_TIME DESC)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVENTS")
        onCreate(db)
    }

    fun insertEvent(
        sensorType: SensorType,
        sensorDetail: String,
        attribution: AppAttribution,
        startTimeMs: Long
    ): Long {
        val values = ContentValues().apply {
            put(COL_SENSOR_TYPE, sensorType.name)
            put(COL_SENSOR_DETAIL, sensorDetail)
            put(COL_PACKAGE_NAME, attribution.packageName)
            put(COL_APP_NAME, attribution.appName)
            put(COL_CONFIDENCE, attribution.confidence.name)
            put(COL_REASON, attribution.reason)
            put(COL_START_TIME, startTimeMs)
            putNull(COL_END_TIME)
        }
        return writableDatabase.insert(TABLE_EVENTS, null, values)
    }

    fun finishEvent(id: Long, endTimeMs: Long) {
        val values = ContentValues().apply {
            put(COL_END_TIME, endTimeMs)
        }
        writableDatabase.update(
            TABLE_EVENTS,
            values,
            "$COL_ID = ?",
            arrayOf(id.toString())
        )
    }

    fun finalizeOrphanedEvents(endTimeMs: Long) {
        val values = ContentValues().apply {
            put(COL_END_TIME, endTimeMs)
        }
        writableDatabase.update(
            TABLE_EVENTS,
            values,
            "$COL_END_TIME IS NULL",
            null
        )
    }

    fun clearAllEvents() {
        writableDatabase.delete(TABLE_EVENTS, null, null)
    }

    fun getRecentEvents(limit: Int = 500): List<SensorEventRecord> {
        val list = mutableListOf<SensorEventRecord>()
        readableDatabase.query(
            TABLE_EVENTS,
            null,
            null,
            null,
            null,
            null,
            "$COL_START_TIME DESC",
            limit.toString()
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
            val sensorIdx = cursor.getColumnIndexOrThrow(COL_SENSOR_TYPE)
            val detailIdx = cursor.getColumnIndexOrThrow(COL_SENSOR_DETAIL)
            val pkgIdx = cursor.getColumnIndexOrThrow(COL_PACKAGE_NAME)
            val appNameIdx = cursor.getColumnIndexOrThrow(COL_APP_NAME)
            val confIdx = cursor.getColumnIndexOrThrow(COL_CONFIDENCE)
            val reasonIdx = cursor.getColumnIndexOrThrow(COL_REASON)
            val startIdx = cursor.getColumnIndexOrThrow(COL_START_TIME)
            val endIdx = cursor.getColumnIndexOrThrow(COL_END_TIME)

            while (cursor.moveToNext()) {
                val sensorType = runCatching {
                    SensorType.valueOf(cursor.getString(sensorIdx))
                }.getOrDefault(SensorType.MICROPHONE)
                val confidence = runCatching {
                    AttributionConfidence.valueOf(cursor.getString(confIdx))
                }.getOrDefault(AttributionConfidence.LOW)

                list.add(
                    SensorEventRecord(
                        id = cursor.getLong(idIdx),
                        sensorType = sensorType,
                        sensorDetail = cursor.getString(detailIdx),
                        packageName = if (cursor.isNull(pkgIdx)) null else cursor.getString(pkgIdx),
                        appName = cursor.getString(appNameIdx),
                        confidence = confidence,
                        attributionReason = cursor.getString(reasonIdx),
                        startTimeMs = cursor.getLong(startIdx),
                        endTimeMs = if (cursor.isNull(endIdx)) null else cursor.getLong(endIdx)
                    )
                )
            }
        }
        return list
    }

    companion object {
        private const val DATABASE_NAME = "centinela_privacidad.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_EVENTS = "sensor_events"
        private const val COL_ID = "id"
        private const val COL_SENSOR_TYPE = "sensor_type"
        private const val COL_SENSOR_DETAIL = "sensor_detail"
        private const val COL_PACKAGE_NAME = "package_name"
        private const val COL_APP_NAME = "app_name"
        private const val COL_CONFIDENCE = "confidence"
        private const val COL_REASON = "reason"
        private const val COL_START_TIME = "start_time_ms"
        private const val COL_END_TIME = "end_time_ms"
    }
}
