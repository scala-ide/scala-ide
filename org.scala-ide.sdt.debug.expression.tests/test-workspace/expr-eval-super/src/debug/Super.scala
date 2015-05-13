package debug

object Super extends App {

  trait BaseTrait {
    def name = "BaseTrait"
    def id[T](e: T) = s"BaseTrait:$e"
    def list[T](elems: T*) = s"BaseTrait:${elems.toList}"

    object InnerObject {
      def simple = 1
      def vararg[T](elems: T*) = s"BaseTrait:InnerObject:${elems.toList}"
    }
  }

  trait BaseTrait1 extends BaseTrait {
    override def name = "BaseTrait1"
    override def id[T](e: T) = s"BaseTrait1:$e"
    override def list[T](elems: T*) = s"BaseTrait1:${elems.toList}"
  }

  trait BaseTrait2 extends BaseTrait {
    override def name = "BaseTrait2"
    override def id[T](e: T) = s"BaseTrait2:$e"
    override def list[T](elems: T*) = s"BaseTrait2:${elems.toList}"
  }

  class BaseClass extends BaseTrait1 with BaseTrait2 {
    override def name = "BaseClass"
    override def id[T](e: T) = s"BaseClass:$e"
    override def list[T](elems: T*) = s"BaseClass:${elems.toList}"
  }

  class DerivedClass extends BaseClass with BaseTrait1 with BaseTrait2 {
    override def name = "DerivedClass"
    override def id[T](e: T) = s"DerivedClass:$e"
    override def list[T](elems: T*) = s"DerivedClass:${elems.toList}"

    def breakpoint = "bp"
  }

  (new DerivedClass).breakpoint
}