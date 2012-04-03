package scala.tools.eclipse.ui

import org.eclipse.debug.core.ILaunch

class ScalaTestRunSession(fLaunch: ILaunch, fRunName: String) {
  //private var fLaunch: ILaunch
  //private var 
  
  var startedCount = 0
  var ignoredCount = 0
  var totalCount = 0
  var errorCount = 0
  var failureCount = 0
  private var running = false
  
  def run() {
    running = true
  }
  
  def stop() {
    running = false
  }
  
  def isStopped = false  // should change when user stop it
  
  def isRunning = running
}