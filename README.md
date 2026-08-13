# Registry (GKVR - Generic Key-Value Registry)

The `Registry` is a generic data structure designed to track items over time using a standardized approach for metadata (`BookKeeping`) and user-defined state (`Value`). It is deeply inspired by C++ template programming and custom serialization patterns.

## Architecture

The Kotlin implementation mimics the behavior of C++ templates by allowing developers to subclass the generic `Registry<K, V>` base class and define their own value structures and serialization hooks.

### C++ Inspiration

In C++, a registry might look like:

```cpp
namespace gkvr {
    template <class Key, class V>
    class Registry {
        // Implementation details
    };
}
```

Kotlin achieves this template-like behavior using generics:

```kotlin
abstract class Registry<K, V>(val config: Config) {
    // ...
}
```

### BookKeeping (Metadata) & UpdateStatus

The `Registry` inherently maintains metadata for every key. This `BookKeeping` structure is purely internal and should not be operated on directly by "user" classes.
- `created`: The epoch timestamp when the entry was first seen.
- `last_seen`: The epoch timestamp when the entry was most recently seen.
- `changed`: The epoch timestamp when the state of the entry last changed (updated only if `updateValue()` returns `true`).

The `addOrUpdateTs` method handles the logic for appropriately updating these timestamps on every transaction and returns an `UpdateStatus` enum:
- `UpdateStatus.ADDED`: The key was not present and has been added.
- `UpdateStatus.EXISTING_UPDATED`: The key existed and `updateValue()` modified the value (`bk.changed` updated).
- `UpdateStatus.EXISTING_NOT_UPDATED`: The key existed but `updateValue()` returned `false` (`bk.changed` not updated).

---

## API & Core Methods

### Abstract / Hook Methods
Subclasses are expected to override or customize these operations:

* `updateValue(value: V, newValue: V): Boolean`: Abstract method to update an existing value instance. Returns `true` if any field was changed, `false` otherwise.
* `getSerializationVersion(value: V): Int`: Returns the content version format (defaults to `1`).
* `serializeKey(key: K): String`: Abstract method to convert key to string.
* `deserializeKey(s: String): K`: Abstract method to convert string back to key object.
* `serializeValue(value: V): String`: Abstract method to convert value to string.
* `deserializeValue(version: Int, s: String): V`: Abstract method to convert string back to value object given a content serialization version.
* `serializeBookkeeping(bk: BookKeeping): String`: Converts `BookKeeping` timestamps to space-delimited string (`"created last_seen changed"`).
* `deserializeBookkeeping(s: String): BookKeeping`: Restores `BookKeeping` object from space-delimited string.

### Operations & Lifecycle Methods

* `addOrUpdateTs(key: K, value: V, timestamp: Long): UpdateStatus`: Adds a key if missing or updates metadata (`created`, `last_seen`, and `changed` if value modified) and value payload. Returns `UpdateStatus`.
* `has(key: K): Boolean`: Checks whether a given key exists in the registry.
* `get(key: K): V`: Returns the value for `key` without `BookKeeping` metadata. Throws `NoSuchElementException` if the key is not found.
* `getBookkeeping(key: K): BookKeeping`: Returns the `BookKeeping` metadata object for `key`. Throws `NoSuchElementException` if the key is not found.
* `delete(key: K)`: Removes a key-value entry from memory.
* `expireKeys(currentTimestamp: Long)`: Purges entries whose `lastSeen` timestamp exceeds `expirationPeriodDays` (if `mustExpireKeys` is enabled in configuration).
* `getAllEntries(): Map<K, Pair<BookKeeping, V>>`: Returns the complete map of entries mapping keys to `(BookKeeping, Value)` pairs.
* `save()`: Writes the current header and content to disk if configuration option `isActive` is enabled.

### Internal Load/Save Mechanics

* `load()` & `loadHeader()` / `loadContent()`: Handles header verification (`GKVR` magic header check) and line parsing during instantiation if `isActive` is set.
* `saveHeader()` & `saveContent()`: Writes header details (magic, version, size) followed by single-line representations of each entry.

---

### Serialization & Deserialization

The `Registry` utilizes a plaintext file format for storage, deliberately restricting each registry entry to exactly one line. This ensures that the data is trivial to traverse using command-line tools like `grep`.

In C++, serialization is often implemented via function overloading:

```cpp
namespace gkvr {
    ostream & serialize(ostream & os, const MyKey & key);
    ostream & serialize(ostream & os, const MyValue & value);
    std::size_t get_serialization_version(const MyValue & e);
}
```

In this Kotlin version, these operations are provided as abstract or open "hook" methods that subclasses must or can override:

- `serializeKey(key: K): String`
- `deserializeKey(s: String): K`
- `serializeValue(value: V): String`
- `deserializeValue(version: Int, s: String): V`
- `getSerializationVersion(value: V): Int`

### String Codec

To allow clean space-separated values on a single line, `StringCodec` provides `encode()` and `decode()` functions. These encode string keys by converting spaces to `+`, `+` to `++`, `\` to `\\`, and newline characters to literal `\n` characters prior to writing them to the file.

This guarantees that fields safely remain on the same line and don't break the space-separated integrity.

### Configuration

`Config` data class attributes:
* `is_active`: Controls whether file load and save operations are executed.
* `allow_missing_file`: If `true`, suppresses error when the file does not exist on disk during initial load.
* `filename`: Target filepath for reading and writing data.
* `must_expire_keys`: Toggles automated expiration logic.
* `expiration_period_days`: Lifespan window (in days) evaluated against entry `last_seen` timestamp.

---

### Code Snippet

```kotlin
import com.trodevel.generickeyvalueregistry.*
import org.json.JSONObject

data class Contact(
    var first_name: String = "",
    var last_name: String = "",
    var age: Int = 0
)

class ContactRegistry(config: Config) : Registry<String, Contact>(config) {
    override fun updateValue(value: Contact, newValue: Contact): Boolean {
        var updated = false
        if (newValue.first_name.isNotEmpty() && value.first_name != newValue.first_name) {
            value.first_name = newValue.first_name
            updated = true
        }
        if (newValue.last_name.isNotEmpty() && value.last_name != newValue.last_name) {
            value.last_name = newValue.last_name
            updated = True
        }
        if (newValue.age != 0 && value.age != newValue.age) {
            value.age = newValue.age
            updated = true
        }
        return updated
    }

    override fun serializeKey(key: String): String = StringCodec.encode(key)

    override fun deserializeKey(s: String): String = StringCodec.decode(s)

    override fun serializeValue(value: Contact): String {
        val json = JSONObject()
        json.put("first_name", value.first_name)
        json.put("last_name", value.last_name)
        json.put("age", value.age)
        return json.toString()
    }

    override fun deserializeValue(version: Int, s: String): Contact {
        if (version == 1) {
            val json = JSONObject(s)
            return Contact(
                first_name = json.optString("first_name", ""),
                last_name = json.optString("last_name", ""),
                age = json.optInt("age", 0)
            )
        }
        throw IllegalArgumentException("Unknown version: $version")
    }
}

// Instantiation with config
val config = Config(
    is_active = true,
    allow_missing_file = true,
    filename = "data.dat",
    must_expire_keys = true,
    expiration_period_days = 30
)

val registry = ContactRegistry(config)
val status = registry.addOrUpdateTs(
    "user_1",
    Contact(first_name = "Alice", last_name = "Smith", age = 30),
    timestamp = 1700000000L
)
// status == UpdateStatus.ADDED
registry.save()
```
