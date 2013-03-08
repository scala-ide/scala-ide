class ReferredClass/*ref*/ { }

class ReferringClass[T <: ReferredClass] {

  type typedSet = Set[ReferredClass]

  def foo[T <: ReferredClass]: Unit = {}
}