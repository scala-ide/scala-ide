package debug

object NestedClasses extends TestApp {

  val parentObjectField = "parentObjectField"

  def parentObjectMethod = "parentObjectMethod"


  object ParentObject {
    val parentObject2Field = "parentObject2Field"

    def parentObject2Method = "parentObject2Method"

    class InnerClass {
      val inner = new InnerClass2

      val parentClassField = "parentClassField"

      def parentClassMethod = "parentClassMethod"

      class InnerClass2 {
        def doIt(): Unit = {
          //breakpoint goes there so modify line number in TestValues also
          println(parentObjectField)
        }
      }

      val initObject = ParentObject.toString()
    }

  }

  new ParentObject.InnerClass().inner.doIt()
}