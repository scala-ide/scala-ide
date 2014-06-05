package org.scalaide.debug.internal.async

import org.apache.commons.collections4.map.{ LRUMap => CommonsLRUMap }
import scala.collection.convert.Wrappers.JMapWrapper

class LRUMap[K, V](val maxSize: Int, underlying: java.util.Map[K, V]) extends JMapWrapper[K, V](underlying) {
  def this(maxSize: Int) = this(maxSize, new CommonsLRUMap(maxSize).asInstanceOf[java.util.Map[K, V]])
}

