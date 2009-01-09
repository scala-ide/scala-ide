/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.util;

class MaskSets[T] {
  /** determine bit mask location of <code>t</code> */
  protected def mask(that : T) = that.hashCode % 64
  private def maskFor(that : T) : Long = 1L << mask(that)
  trait Set extends scala.collection.Set[T] {
    private[MaskSets] var mask : Long = 0
    // initialize
    elements.foreach(e => mask = mask | maskFor(e))
    
    abstract override def contains(that : T) = {
      if ((mask & maskFor(that)) != 0) super.contains(that)
      else false
    }
    
    abstract override def subsetOf(that : scala.collection.Set[T]) = that match {
      case that : Set => 
        if ((mask & that.mask) == mask) super.subsetOf(that)
        else false
      case that => super.subsetOf(that)
    }
    override def hashCode = (mask.toInt ^ ((mask >> 32).toInt))
    override def equals(that : Any) = that match {
    case that : Set if mask != that.mask => false
    case _ => super.equals(that)
    }
  }
  trait MutableSet extends scala.collection.mutable.Set[T] with Set {
    abstract override def +=(that : T) = {
      mask = mask | maskFor(that)
      super.+=(that)
    }
    abstract override def ++=(that : Iterable[T]) = that match {
    case that : Set => mask = mask | that.mask; super.++=(that)
    case that => super.++=(that)
    }
    abstract override def -=(that : T) = throw new Error
    abstract override def --=(that : Iterable[T]) = throw new Error
  }
}
