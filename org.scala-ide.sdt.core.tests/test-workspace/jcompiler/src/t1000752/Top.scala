package t1000752

trait Top {
  def loggable(self: AnyRef): Unit = {}
}

abstract class ATop extends Top {}