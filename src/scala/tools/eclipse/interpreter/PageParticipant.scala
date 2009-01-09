/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 * @author Josh Suereth
 */
// $Id$

package scala.tools.eclipse.interpreter

import org.eclipse.debug.internal.ui.DebugPluginImages
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants
import org.eclipse.ui.IActionBars
import org.eclipse.ui.console._
import org.eclipse.ui.part._
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jface.action.IToolBarManager

/**
 *  This class wires the console implementation for an interpreter 
 */
class PageParticipant extends IConsolePageParticipant {

  var page : IPageBookViewPage = _
  var console: Console = null
  
  override def init(page0 : IPageBookViewPage , console0 : IConsole ) = {
    page = page0
    console = console0.asInstanceOf[Console]  
    var actionBars = page.getSite.getActionBars
    var mgr = actionBars.getToolBarManager
    mgr.appendToGroup(IConsoleConstants.LAUNCH_GROUP, new Terminate)
    mgr.appendToGroup(IConsoleConstants.LAUNCH_GROUP, new Reset)
  }
  
  override def deactivated : Unit = {}
  
  override def activated : Unit = {}
  
  override def dispose : Unit = {}
  
  override def getAdapter(clazz: Class[_]) : Object = {
    if(clazz.isInstance(this)) {
      return this
    }
    return null
  }
  
  /**
   * This action is used to close an interpreter
   */
  class Terminate extends org.eclipse.jface.action.Action("Close Interpreter") {
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_LCL_TERMINATE))
    
    override def run : Unit = {
      console.close
    }
  }
  
  /**
   * This action is used to reset the memory in a console. 
   */
  class Reset extends org.eclipse.jface.action.Action("Reset Interpreter") {
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_LCL_RESET_MEMORY))
    
    override def run : Unit = {
      var element = console.getElement
      console.close
      ScalaConsoleMgr.mkConsole(element)
    }
  }
}
