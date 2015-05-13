package debug

object ToolBoxBugs extends App {

  class C {
    def breakpoint = "bp"
    def zero = 0
  }

  (new C).breakpoint
}
