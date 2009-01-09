/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.util
import scala.collection.mutable.Set
import scala.collection.jcl.LinkedHashSet

class SetBuilder[T] {
  protected def newSet[T] : Set[T] = new LinkedHashSet[T]
  private var set : Set[T] = newSet
  def +=(t : T) = ck += t
  def ++=(t : Iterable[T]) = ck ++= t
  def done = {
    val ret = ck
    set = null
    ret.readOnly
  }
  private def ck : Set[T] = if (set == null) throw new Error else set
  override def toString = if (set == null) "invalid" else set.toString
}
