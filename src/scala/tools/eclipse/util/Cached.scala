/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import java.util.concurrent.locks.{ Condition, Lock, ReadWriteLock, ReentrantReadWriteLock }

trait Cached[T] {
  private val rwLock = new ReentrantReadWriteLock
  private val ready = rwLock.writeLock.newCondition
  private var inProgress = false
  private var elem : Option[T] = None
  
  def apply[U](op : T => U) : U = {
    val rl = rwLock.readLock
    val wl = rwLock.writeLock
    
    locked(rl) {
      elem match {
        case Some(t) => return op(t)
        case _ =>
      }
    }
    
    wl.lock
    while (!elem.isDefined) {
      while (inProgress)
        ready.await
        
      if (!elem.isDefined) {
        inProgress = true
        wl.unlock
	      try {
          val t = create
          wl.lock
          elem = Some(t)
        } finally {
          locked(wl) {
            inProgress = false
            ready.signalAll
          }
        }
      }
    }
    
    locked(rl) {
      wl.unlock
      op(elem.get)
    }
  }
  
  def invalidate() {
    val oldElem =
      locked(rwLock.writeLock) {
        val elem0 = elem
        elem = None
        elem0
      }
    
    oldElem match {
      case Some(t) => destroy(t)
      case _ =>
    }
  }

  protected def create() : T

  protected def destroy(t : T)
  
  private def locked[U](l : Lock)(op : => U) : U = {
    try {
      l.lock
      op
    } finally {
      l.unlock
    }
  }
}
