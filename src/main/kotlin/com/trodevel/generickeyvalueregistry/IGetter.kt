package com.trodevel.generickeyvalueregistry

/**
 * Interface for adding or updating entries in the registry.
 */
interface IGetter<K, V> {
    /**
     * Adds a new entry or updates an existing one associated with [key].
     */
    fun addOrUpdateTs(key: K, value: V, timestamp: Long): UpdateStatus
}
