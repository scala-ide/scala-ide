package org.scalaide.util.internal

import java.util.LinkedHashMap
import java.lang.ref.WeakReference

/** A LRU fixed sized-cache using WeakReferences.
 *
 *  The map won't grow past `maxSize`, and on each cache miss it might
 *  remove the least-recently used entry, if the size of the map grows
 *  past `maxSize`.
 *
 *  This class is based on the Java LinkedHashMap implementation and is thread-safe.
 */
class FixedSizeCache[K, V](initSize: Int, maxSize: Int) {
  private def missed(key: K, orElse: => V): V = {
    val value = orElse
    jmap.put(key, new WeakReference(value))
    value
  }

  type JEntry = java.util.Map.Entry[K, WeakReference[V]]

  /* This linked map is ordered by access, meaning it removes the LRU entry. */
  private val jmap = new LinkedHashMap[K, WeakReference[V]](initSize min maxSize, 0.75f, /* accessOrder = */ true) {
    override def removeEldestEntry(entry: JEntry): Boolean = size() > maxSize
  }

  /** Return the value associated with K if found in cache, otherwise insert the value
   *  provided in `orElse`.
   */
  def getOrUpdate(key: K)(orElse: => V): V = synchronized {
    jmap.get(key) match {
      case null => missed(key, orElse)

      case ref => ref.get() match {
        case null  => missed(key, orElse)
        case value => value
      }
    }
  }
}
