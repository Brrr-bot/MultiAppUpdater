package com.homehub.dashboard.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File

class AlarmRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    // Secondary backup file — survives OTA updates. If SharedPreferences is ever
    // missing (e.g. after a forced reinstall), we restore from this file automatically.
    private val backupFile = File(context.filesDir, BACKUP_FILENAME)

    private val state = MutableStateFlow(load())

    fun observeAll(): Flow<List<AlarmEntity>> = state

    fun observeByWeekday(weekday: Int): Flow<List<AlarmEntity>> = state.map { list ->
        list.filter { it.weekday == weekday }.sortedWith(compareBy(AlarmEntity::hour, AlarmEntity::minute, AlarmEntity::id))
    }

    suspend fun getById(id: Long): AlarmEntity? = state.value.firstOrNull { it.id == id }

    suspend fun getEnabled(): List<AlarmEntity> = state.value.filter { it.enabled }

    suspend fun insert(alarm: AlarmEntity): Long {
        val nextId = (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
        val updated = state.value + alarm.copy(id = nextId)
        save(updated)
        return nextId
    }

    suspend fun update(alarm: AlarmEntity) {
        save(state.value.map { if (it.id == alarm.id) alarm else it })
    }

    suspend fun delete(alarm: AlarmEntity) {
        save(state.value.filterNot { it.id == alarm.id })
    }

    private fun load(): List<AlarmEntity> {
        // Try SharedPreferences first (normal path)
        val raw = prefs.getString(KEY_ALARMS, null)
        if (!raw.isNullOrBlank()) {
            return parseJson(raw).also { alarms ->
                // Keep backup in sync on every load so it's always fresh
                if (alarms.isNotEmpty()) writeBackup(alarms)
            }
        }
        // SharedPreferences empty — attempt restore from backup file
        if (backupFile.exists()) {
            val backupRaw = runCatching { backupFile.readText() }.getOrNull()
            if (!backupRaw.isNullOrBlank()) {
                val restored = parseJson(backupRaw)
                if (restored.isNotEmpty()) {
                    // Re-persist to SharedPreferences so future loads are fast
                    prefs.edit().putString(KEY_ALARMS, backupRaw).apply()
                    return restored
                }
            }
        }
        return emptyList()
    }

    private fun save(alarms: List<AlarmEntity>) {
        val sorted = alarms.sortedWith(
            compareBy(AlarmEntity::weekday, AlarmEntity::hour, AlarmEntity::minute, AlarmEntity::id)
        )
        val json = gson.toJson(sorted)
        prefs.edit().putString(KEY_ALARMS, json).apply()
        writeBackup(sorted)
        state.value = sorted
    }

    private fun writeBackup(alarms: List<AlarmEntity>) {
        runCatching {
            backupFile.writeText(gson.toJson(alarms))
        }
    }

    private fun parseJson(json: String): List<AlarmEntity> {
        val type = object : TypeToken<List<AlarmEntity>>() {}.type
        return runCatching {
            gson.fromJson<List<AlarmEntity>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME      = "home_hub_alarms"
        private const val KEY_ALARMS      = "alarms"
        private const val BACKUP_FILENAME = "alarms_backup.json"
    }
}
