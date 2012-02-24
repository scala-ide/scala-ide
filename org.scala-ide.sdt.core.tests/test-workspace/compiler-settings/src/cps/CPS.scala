import scala.util.continuations._

object CPS extends App {
  class Display(val resume: (Unit => Any)) extends Throwable

  def display(prompt: String) = shift {
    cont: (Unit => Any) =>
      {
        println(prompt)
        throw new Display(cont)
      }
  }

  def foo(): Int = reset {
    display("foo!")
    5
  }

  def bar(): Unit = reset {
    display("bar!")
  }

  try {
    foo()
  } catch {
    case d: Display => println(d.resume())
  }

  try {
    bar()
  } catch {
    case d: Display => d.resume()
  }
}

