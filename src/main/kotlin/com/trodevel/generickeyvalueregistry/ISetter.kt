package com.trodevel.generickeyvalueregistry

/**
 * Interface for checking existence and retrieving values from the registry.
 */
interface ISetter<K, V> {
    /**
     * Checks whether a given key exists in the registry.
     */
    fun has(key: K): Boolean

    /**
     * Returns the value for [key].
     */
    fun get(key: K): V
}
