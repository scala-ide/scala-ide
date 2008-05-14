/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.util;

class BenchTimer {
  // How do we get the current time
  def currentTime = scala.compat.Platform.currentTime
  // What is the magnitude of the time relative to seconds
  def timeMag = 1000d

  var from : Long = -1
  var accum : Long = 0
  reset

  def reset = {
    from = currentTime
    accum = 0
  }
  def assertEnabled = {}
  def assertDisabled = {}
  
  def update = {
    if (from != -1) {
      accum += (currentTime - from)
      from = currentTime
    }
  }
  def disabled = from == -1
  def disable = from = -1
  def enable = from = currentTime
  def elapsed = {
    update
    val result = (accum.toDouble / timeMag)
    (result * 1000d).toInt.toDouble / 1000d
  }
  def elapsedString = (elapsed * 1000) + " ms"
}
