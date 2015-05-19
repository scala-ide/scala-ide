package debug

object ToolBoxBugs extends TestApp {

  class C {
    def breakpoint = "bp"
    def zero = 0
  }

  (new C).breakpoint
}
