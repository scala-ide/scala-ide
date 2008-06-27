/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.util
import scala.collection.jcl._

trait Observer {
  def invalidate : Unit
}

object Observer {
  import scala.ref._
  /** hash-able weak reference */
  abstract class Weak[T <: Observer](that : T) extends WeakReference[T](that) with Observer {
    def invalidate : Unit = get.map(_.invalidate)  
    override def hashCode = get.get.hashCode
    override def equals(that : Any) = that match {
    case that : Weak[_] => get equals that.get
    case that : Observer => get.map(_ equals that).getOrElse(false)
    case that => super.equals(that)
    }
  }
  trait Multi extends Collection[Observer] with Observer {
    def invalidate : Unit = foreach(_.invalidate)
  }
  class Set extends LinkedHashSet[Observer] {
    def invalidate : Unit = {
      foreach(_.invalidate)
      clear
    }
  }
  trait Membership[T] extends Observer {
    def   added(what : Collection[T]) : Unit
    def removed(what : Collection[T]) : Unit
  }
  
}
