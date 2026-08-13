package com.trodevel.generickeyvalueregistry

import java.io.File
import java.io.FileNotFoundException

abstract class Registry<K, V>(val config: Config) {
    protected val entries: MutableMap<K, Pair<BookKeeping, V>> = mutableMapOf()

    init {
        if (config.is_active) {
            load()
        }
    }

    protected abstract fun updateValue(value: V, newValue: V): Boolean

    fun addOrUpdateTs(key: K, value: V, timestamp: Long): UpdateStatus {
        val entry = entries[key]
        return if (entry == null) {
            val bk = BookKeeping(created = timestamp, last_seen = timestamp, changed = timestamp)
            entries[key] = Pair(bk, value)
            UpdateStatus.ADDED
        } else {
            val (bk, oldVal) = entry

            if (timestamp < bk.created) {
                bk.created = timestamp
            }
            if (timestamp > bk.last_seen) {
                bk.last_seen = timestamp
            }

            if (updateValue(oldVal, value)) {
                if (timestamp > bk.changed) {
                    bk.changed = timestamp
                }
                entries[key] = Pair(bk, value)
                UpdateStatus.EXISTING_UPDATED
            } else {
                UpdateStatus.EXISTING_NOT_UPDATED
            }
        }
    }

    abstract fun getSerializationVersion(value: V): Int

    abstract fun serializeKey(key: K): String

    abstract fun deserializeKey(s: String): K

    abstract fun serializeValue(value: V): String

    abstract fun deserializeValue(version: Int, s: String): V

    fun serializeBookkeeping(bk: BookKeeping): String =
        "${bk.created} ${bk.last_seen} ${bk.changed}"

    fun deserializeBookkeeping(s: String): BookKeeping {
        val parts = s.split(' ')
        return BookKeeping(
            created = parts[0].toLong(),
            last_seen = parts[1].toLong(),
            changed = parts[2].toLong()
        )
    }

    private fun loadHeader(lines: List<String>): Triple<Int, Int, Int> {
        if (lines.size < 4) return Triple(0, 0, 0)

        val header = lines[0]
        if (header != "GKVR") {
            throw IllegalArgumentException("Invalid format: Missing GKVR header")
        }

        val contentVersion = lines[2].toInt()
        val size = lines[3].toInt()
        return Triple(4, contentVersion, size)
    }

    private fun loadContent(lines: List<String>, startIdx: Int, contentVersion: Int, size: Int) {
        var idx = startIdx
        repeat(size) {
            if (idx >= lines.size) return@repeat
            val line = lines[idx++]

            val parts = line.split(" ", limit = 5)
            if (parts.size < 4) return@repeat

            val keyStr = parts[0]
            val bkStr = "${parts[1]} ${parts[2]} ${parts[3]}"
            val valueStr = if (parts.size > 4) parts[4] else ""

            val key = deserializeKey(keyStr)
            val value = deserializeValue(contentVersion, valueStr)
            val bk = deserializeBookkeeping(bkStr)
            entries[key] = Pair(bk, value)
        }
    }

    private fun load() {
        val file = File(config.filename)
        if (!file.exists()) {
            if (config.allow_missing_file) return
            throw FileNotFoundException("Registry file missing: ${config.filename}")
        }

        val lines = file.readLines()
        val (startIdx, contentVersion, size) = loadHeader(lines)
        if (size > 0) {
            loadContent(lines, startIdx, contentVersion, size)
        }
    }

    private fun saveHeader(writer: java.io.BufferedWriter) {
        writer.write("GKVR\n")
        writer.write("1\n")

        val contentVersion = if (entries.isNotEmpty()) {
            getSerializationVersion(entries.values.first().second)
        } else {
            1
        }

        writer.write("$contentVersion\n")
        writer.write("${entries.size}\n")
    }

    private fun saveContent(writer: java.io.BufferedWriter) {
        for ((key, pair) in entries) {
            val (bk, value) = pair
            writer.write("${serializeKey(key)} ${serializeBookkeeping(bk)} ${serializeValue(value)}\n")
        }
    }

    fun save() {
        if (!config.is_active) return

        File(config.filename).bufferedWriter().use { writer ->
            saveHeader(writer)
            saveContent(writer)
        }
    }

    fun has(key: K): Boolean = entries.containsKey(key)

    fun get(key: K): V = entries[key]?.second ?: throw NoSuchElementException("Key '$key' not found in registry")

    fun getBookkeeping(key: K): BookKeeping = entries[key]?.first ?: throw NoSuchElementException("Key '$key' not found in registry")

    fun delete(key: K) {
        entries.remove(key)
    }

    fun expireKeys(currentTimestamp: Long) {
        if (!config.must_expire_keys) return

        val expirationSecs = config.expiration_period_days.toLong() * 86400
        val threshold = currentTimestamp - expirationSecs

        val keysToDelete = entries.filter { it.value.first.last_seen < threshold }.keys
        keysToDelete.forEach { delete(it) }
    }

    fun getAllEntries(): Map<K, Pair<BookKeeping, V>> = entries
}
