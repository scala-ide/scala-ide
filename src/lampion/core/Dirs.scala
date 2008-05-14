/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.core;

object Dirs {
  abstract class Dir {
    def reverse : Dir;
    def select[T](a : T, b : T) : T;

  }
  object PREV extends Dir { 
    override def toString() = "prev";
    def reverse = NEXT; 
    def select[T](a : T, b : T) = a; 
  }
  object NEXT extends Dir { 
    override def toString() = "next";
    def reverse = PREV; def select[T](a : T, b : T) = b; 
  }
}

trait Dirs {
  val PREV = Dirs.PREV;
  val NEXT = Dirs.NEXT;
  type Dir = Dirs.Dir;
}
