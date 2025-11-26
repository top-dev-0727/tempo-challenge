## Code Review

You are reviewing the following code submitted as part of a task to implement an item cache in a highly concurrent application. The anticipated load includes: thousands of reads per second, hundreds of writes per second, tens of concurrent threads.
Your objective is to identify and explain the issues in the implementation that must be addressed before deploying the code to production. Please provide a clear explanation of each issue and its potential impact on production behaviour.

```kotlin
import java.util.concurrent.ConcurrentHashMap

class SimpleCache<K, V> {
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val ttlMs = 60000 // 1 minute
    
    data class CacheEntry<V>(val value: V, val timestamp: Long)
    
    fun put(key: K, value: V) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }
    
    fun get(key: K): V? {
        val entry = cache[key]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
                return entry.value
            }
        }
        return null
    }
    
    fun size(): Int {
        return cache.size
    }
}
```

## Issues Identified

### 1. **Memory Leak - Expired Entries Never Removed**
**Issue**: Expired cache entries are never removed from the underlying `ConcurrentHashMap`. The `get()` method only returns `null` for expired entries but doesn't delete them.

**Impact**: 
- The cache will grow indefinitely, consuming unbounded memory
- Over time, even with a 1-minute TTL, entries accumulate faster than they expire
- With hundreds of writes per second, the cache could consume gigabytes of memory within hours
- Eventually leads to `OutOfMemoryError` and application crash
- The `size()` method returns an inflated count including dead entries

**Fix**: Remove expired entries in the `get()` method and implement a cleanup mechanism (e.g., background eviction thread or `LinkedHashMap` with access-order tracking).

---

### 2. **Race Condition in TTL Check and Value Retrieval**
**Issue**: Between checking if an entry is valid and returning its value, another thread could delete the entry or the object could be garbage collected.

```kotlin
val entry = cache[key]  // Thread A reads entry
// Thread B could delete cache[key] here
if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
    return entry.value  // Using stale reference
}
```

**Impact**:
- Potential `NullPointerException` or undefined behavior
- Data inconsistency in highly concurrent scenarios
- Unpredictable failures under load

**Fix**: Atomically check and retrieve the value using `cache.compute()` or `cache.computeIfPresent()` to ensure atomicity.

---

### 3. **No Null Safety for Expired Entries**
**Issue**: When an entry expires, `get()` returns `null` without removing it. This is ambiguous—callers can't distinguish between "key doesn't exist" and "key exists but expired."

**Impact**:
- Semantically confusing API
- Callers may not understand that an expired key is still occupying memory
- Could lead to incorrect business logic if null is cached or reused

**Fix**: Either remove expired entries or provide separate methods like `getIfValid()` vs `getAndRemoveIfExpired()`.

---

### 4. **Time-Based Expiration Without Removal is Inefficient**
**Issue**: Lazy expiration (checking on access) only works if keys are accessed after expiration. Keys that are never re-accessed will remain in memory forever.

**Impact**:
- Cache fills up with stale data from rarely-accessed or abandoned keys
- With thousands of writes per second, the cache could accumulate entries much faster than they're accessed for eviction
- Memory bloat is virtually guaranteed

**Fix**: Implement a background eviction thread that periodically removes expired entries, or use a scheduled cleanup task.

---

### 5. **No Exception Handling**
**Issue**: `System.currentTimeMillis()` could theoretically throw exceptions or be affected by system clock adjustments. No error handling is in place.

**Impact**:
- Unexpected runtime exceptions could crash the cache
- System clock skew could cause all entries to appear expired or valid indefinitely

**Fix**: Consider using `System.nanoTime()` (monotonic) or add defensive exception handling.

---