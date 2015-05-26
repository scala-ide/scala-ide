package foo

trait Foo {
  private def configure(): Unit = {
    Bar.configure("")
  }
}