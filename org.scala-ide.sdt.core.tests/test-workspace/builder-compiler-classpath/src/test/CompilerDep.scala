package test

class CompilerDep {
  def foo: Unit = {
    println(scala.tools.nsc.Main.prompt)
  }
}