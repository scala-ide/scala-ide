package t1000678_8

class Loggable

object Loggable {
  
  trait IRunUnit {
    val id: Integer
  }
  
  class RunUnit(val id : Integer) extends IRunUnit {
    trait IC {
      def foo: Unit
    }
    class C extends IC {
      def foo {}
    }
  }
}