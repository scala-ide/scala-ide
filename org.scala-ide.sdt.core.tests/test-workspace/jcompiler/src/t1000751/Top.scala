package t1000751

trait Top {
  protected def foo
}

abstract class ATop extends Top {
  final protected def foo {}
}