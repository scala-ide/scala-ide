package scala.tools.nsc
//BACK-2.8.0 move from util to interactive package to use by interactive class  (doesn't exist in 2.8.0)
package interactive.util

import collection.mutable.HashMap
import collection.immutable

/** A hashmap with set-valued values, and an empty set as default value
 */
class MultiHashMap[K, V] extends HashMap[K, immutable.Set[V]] {
  override def default(key: K): immutable.Set[V] = Set() 
}
