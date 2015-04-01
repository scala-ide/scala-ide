package debug

import TopLevel.topLevel

object Implicits extends TestApp {

  def implicitField[T](implicit v: T) = v.toString

  def implicitConversion[T](a: T) = a.toString

  def implicitBounds[A <% ViewBound](a: A): String = a.value

  def contextBounds[T: ContextBound](t: T) = t.toString


  import ClassWildcard._
  import ImplicitImport.fromClass

  implicit val localImplicitField = new ClassField

  implicit def classConversion(i: Int): ClassFunctionConversion = new ClassFunctionConversion

  implicit def classViewBound(i: ClassFunctionConversion): ViewBound = ViewBound(i)

  implicit val stringContextBounds: ContextBound[String] = new ContextBound[String]

  def foo() {
    import LocalWildcard._
    import ImplicitClasses._
    import ImplicitImport.local

    implicit val localField = new LocalField

    implicit def localConversion(i: Int): LocalFunctionConversion = new LocalFunctionConversion
    implicit def localViewBound(i: LocalFunctionConversion): ViewBound = ViewBound(i)


    // breakpoint line. Synch with TestValies.ImplicitsTestCase.breakpointLine
    val debug = ???

    // expressions that are tested
    val res = Seq(
      implicitField[TopLevelImport],
      implicitField[ClassField],
      implicitField[WildcardClassImport],
      implicitField[ImplicitClassImport],
      implicitField[LocalImplicitImport],
      implicitField[LocalWildcardImport],
      implicitField[LocalField],

      implicitConversion[CompanionObjectFunctionConversion](1),
      implicitConversion[ClassFunctionConversion](1),
      implicitConversion[LocalFunctionConversion](1),

      implicitBounds(new CompanionObjectFunctionConversion),
      implicitBounds(new ClassFunctionConversion),
      implicitBounds(new LocalFunctionConversion),

      contextBounds("ContextBounds"),

      new ClassWithImplicitArgument,
      new ClassWithMultipleArgumentListAndImplicits(1)(2),
      1 --> 2
    )
    print(res.mkString("\n"))
  }

  foo()
}

trait Printable {
  override def toString = getClass.getSimpleName
}

trait Conflict

class TopLevelImport extends Printable

class ClassField extends Printable

class WildcardClassImport extends Printable

class ImplicitClassImport extends Printable

class LocalImplicitImport extends Printable

class LocalWildcardImport extends Printable with Conflict

class LocalField extends Printable with Conflict

class LocalFunctionConversion extends Printable

class ClassFunctionConversion extends Printable

class CompanionObjectFunctionConversion extends Printable

class ClassWithImplicitArgument(implicit val i: LocalField) extends Printable
class ClassWithMultipleArgumentListAndImplicits(val a: Int)(val b: Int)(implicit val i: LocalField) extends Printable

case class ViewBound(v: Any) {
  def value = v.toString
}

object CompanionObjectFunctionConversion {
  implicit def conversion(a: Int): CompanionObjectFunctionConversion = new CompanionObjectFunctionConversion

  implicit def localViewBound(i: CompanionObjectFunctionConversion): ViewBound = ViewBound(i)
}

trait ImportAndValueConflict

object LocalWildcard {
  implicit val localImport: LocalWildcardImport = new LocalWildcardImport
}

object ClassWildcard {
  implicit val classImport: WildcardClassImport = new WildcardClassImport
}

object ImplicitImport {
  implicit val local: LocalImplicitImport = new LocalImplicitImport
  implicit val fromClass: ImplicitClassImport = new ImplicitClassImport
}

object TopLevel {
  implicit val topLevel: TopLevelImport = new TopLevelImport
}

class ContextBound[T]

object ImplicitClasses{
  implicit final class ArrowAssocWithoutAnyVal[A](private val self: A) {
    def --> [B](y: B): Tuple2[A, B] = Tuple2(self, y)
  }
}