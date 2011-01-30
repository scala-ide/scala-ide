package scala.tools.eclipse
package util

/**
 * @author david.bernard
 *
 */
object Tracer {

  /** t0 init time */
  private val t0 = System.currentTimeMillis  

  /**  
   * A very primitive logger to replace call of classic println. 
   */
  def println(s : String) = if (IDESettings.tracerEnabled.value) {
    Console.println("ScalaPlugin--TRACE--" + (System.currentTimeMillis - t0) + "--" + Thread.currentThread.getName + "--:" + s)
  }
  
  def printlnItems(s : String, items : Iterable[Any]) = if (IDESettings.tracerEnabled.value) {
    for(item <- items) {
      Console.println("ScalaPlugin--TRACE--" + (System.currentTimeMillis - t0) + "--" + Thread.currentThread.getName + "--:" + s + " : " + item)
    }
  }

  def printlnWithStack(s : String, predicate : => Boolean = {true}) = if (IDESettings.tracerEnabled.value) {
    if (predicate) {
      Console.println("ScalaPlugin--TRACE--" + (System.currentTimeMillis - t0) + "--" + Thread.currentThread.getName + "--:" + s)
      Thread.dumpStack
    }
  }

  /**
   * A very primitive StopWatch (no stats,....)
   */
  def timeOf[T](label : String)(f : => T) : T = {
    val start = System.currentTimeMillis
    println(label + " -BEGIN-> ...")
    try {
      f
    } finally {
      val end = System.currentTimeMillis
      println(label + " -END-> " + (end - start) + "ms")
    }
  }
}

