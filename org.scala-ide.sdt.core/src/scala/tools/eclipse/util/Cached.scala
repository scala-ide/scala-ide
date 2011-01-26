/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

/**
 *  A ref cell to a managed resource. Overwrite 'create' and 'destroy'.
 */
//TODO find a better abstraction for Cached, apply, doIfExist,...
trait Cached[T] {
  import java.util.concurrent.locks.ReentrantReadWriteLock
  import scala.tools.eclipse.internal.logging.Tracer
  
//  private var inProgress = false
  @volatile
  private var elem : Option[Either[Throwable,T]] = None
  private val _rwl = new ReentrantReadWriteLock()
  
  /**
   * create only if it's doesn't already exists
   */
//  private def provide() : T = {
//    synchronized {
//      elem match {
//        case Some(t) => t
//        case None => {
//          while (!elem.isDefined) {
//            try {
//              while (inProgress)
//                wait
//            } catch {
//              case ex : InterruptedException =>
//                if (!elem.isDefined) {
//                  throw ex
//                }
//            }
//        
//            if (!elem.isDefined) {
//              inProgress = true
//              try {
//                val t = create
//                elem = Some(t)
//              } finally {
//                inProgress = false
//                notifyAll
//              }
//            }
//          }
//          elem.get
//        }
//      }
//    }
//  }
//  def invalidate() {
//    val oldElem = synchronized {
//      val elem0 = elem
//      elem = None
//      elem0
//    }
//    
//    oldElem match {
//      case Some(t) => destroy(t)
//      case _ =>
//    }
//  }

  // implementation based on http://download.oracle.com/javase/6/docs/api/java/util/concurrent/locks/ReentrantReadWriteLock.html
  private def provide() : Either[Throwable,T] = {
    _rwl.readLock().lock()
    try {
      elem match {
        case Some(t) => t
        case None => {
          // Must release read lock before acquiring write lock
          _rwl.readLock().unlock()
          _rwl.writeLock().lock()
          try {
            // Recheck state because another thread might have acquired
            //   write lock and changed state before we did.
            elem match {
              case Some(t) => t
              case None => {
                val t = try {
                  Right(create)
                } catch {
                  case error => Left(error)
                }
                elem = Some(t)
                t
              }
            }
          } finally {
            // Downgrade by acquiring read lock before releasing write lock
            _rwl.readLock().lock();
            _rwl.writeLock().unlock(); // Unlock write, still hold read
          }
        }
      }
    } finally {
      _rwl.readLock().unlock()  
    }
  }
  
  /**
   * Run the operation on the cached value if successfully created,
   * if the value not already created (or invalidated) then try to create the value,
   * and return the value as Right part.
   * If an exception was throw during the creation then the exception is return as Left part.
   */
  def apply[U](op : T => U) : Either[Throwable, U] = {
    Tracer.printlnWithStack("FIXME : access Cached in 'main' Thread, UI freeze possible", {Thread.currentThread.getName == "main"})
    provide().right.map(op)
  }
  
  /**
   * Run the operation if cached value is already available and successfully created, else do nothing
   * (doesn't try to create the value vs apply)
   */
  def doIfExist(op : T => Unit) : Unit = {
    //TODO should we used the readLock, as it's ok to use the old value if new value is writing...
    elem match {
      case Some(t) if t.isRight => t.right.map(op)
      case _ => // do nothing
    }
  }
  
  def invalidate() {
    var oldElem : Option[Either[Throwable,T]] = None
    _rwl.writeLock().lock()
    try {
      oldElem = elem
      elem = None
    } finally {
      _rwl.writeLock().unlock()
    }
    oldElem match {
      case Some(t) if t.isRight => t.right.map(destroy)
      case _ => // do nothing
    }
  }

  /** Should not throw. */
  protected def create() : T

  protected def destroy(t : T)
}
