package debug

import hidden._
import imported._


object FileImports extends App {

  import imported2._

  def run() = {
    println("ala") //Breakpoint goes there so adter changes in this file change also TestValues.FileImports.breakpointLine
  }

  run()
}

