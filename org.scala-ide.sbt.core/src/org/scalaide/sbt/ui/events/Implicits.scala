package org.scalaide.sbt.ui.events

import org.eclipse.swt.events.TraverseEvent
import org.eclipse.swt.events.TraverseListener
import org.eclipse.jface.viewers.CheckStateChangedEvent
import org.eclipse.jface.viewers.ICheckStateListener

object Implicits {
  implicit def traverserEvent2Listener(f: TraverseEvent => Unit): TraverseListener = new TraverseListener {
    override def keyTraversed(e: TraverseEvent): Unit = f(e)
  }
  
  implicit def checkStateChangedEvent2Listener(f: CheckStateChangedEvent => Unit): ICheckStateListener = new ICheckStateListener {
    override def checkStateChanged(e: CheckStateChangedEvent): Unit = f(e)
  }
}