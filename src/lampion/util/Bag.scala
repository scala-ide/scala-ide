/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.util

/** supports O(1) addition of other collections */

sealed abstract class Bag[+T] extends Collection[T] {
  def underlying : Iterable[T]
  def +[S >: T](what : S) : Bag[S]
  override def ++[B >: T](what : Iterable[B]) : Bag[B] = throw new Error
}
 
object EmptyBag extends Bag[Nothing] {
  def underlying = Nil
  def size = 0
  def elements = Nil.elements
  def +[S >: Nothing](what : S) : Bag[S] = ConsBag(what :: Nil, EmptyBag)
  override def ++[S >: Nothing](what : Iterable[S]) = new ConsBag(what, EmptyBag)
}
case class ConsBag[+T](underlying : Iterable[T], previous : Bag[T]) extends Bag[T] {
  def elements = underlying.elements ++ previous.elements
  def +[S >: T](what : S) : Bag[S] = underlying match {
  case underlying : List[t] => ConsBag(what :: underlying.asInstanceOf[List[T]], previous)
  case _ => ConsBag(what :: Nil, this)
  }
  override def ++[S >: T](what : Iterable[S]) = new ConsBag(what, this)
  override def toString = elements.toList.toString
  def size = underlying match {
  case underlying : Collection[_] => underlying.size + previous.size
  }
}


