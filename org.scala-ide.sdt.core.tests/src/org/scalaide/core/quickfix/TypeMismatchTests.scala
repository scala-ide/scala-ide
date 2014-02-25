package org.scalaide.core.quickfix

import org.junit.Test

class TypeMismatchTests {
  import QuickFixesTests._

  val stringPattern = "Transform expression: %s => %s"

  @Test
  def basicTypeMismatchQuickFixes() {
    withManyQuickFixesPerLine("typemismatch/Basic.scala")(
      List(
        List(
          stringPattern.format("List[List[Int]]()", "List[List[Int]]().flatten"),
          stringPattern.format("List[List[Int]]()", "List[List[Int]]().head"),
          stringPattern.format("List[List[Int]]()", "List[List[Int]]().last")),
        List(
          stringPattern.format("listOfInt_val", "listOfInt_val.flatten"),
          stringPattern.format("listOfInt_val", "listOfInt_val.head"),
          stringPattern.format("listOfInt_val", "listOfInt_val.last")),
        List(
          stringPattern.format("intVal", "Some(intVal)"),
          stringPattern.format("intVal", "Option(intVal)")),
        List(
          stringPattern.format("List[Int]()", "List[Int]().toArray")),
        List(
          stringPattern.format("arrayOfInt_val", "arrayOfInt_val.toArray")) //        ,
//        List(
//          stringPattern.format("5","Some(5)"),
//          stringPattern.format("5","Option(5)")
//        ),
//        List(
//          stringPattern.format("5 * 3 + 2","Some(5 * 3 + 2)"),
//          stringPattern.format("5 * 3 + 2","Option(5 * 3 + 2)")
//        ),
//        List(
//          stringPattern.format("5 * 3 + intVal","Some(5 * 3 + intVal)"),
//          stringPattern.format("5 * 3 + intVal","Option(5 * 3 + intVal)")
//        ),
//        List(
//          stringPattern.format("true","Some(true)"),
//          stringPattern.format("true","Option(true)")
//        ),
//        List(
//          stringPattern.format("(intVal % 4 == 2)","Some((intVal % 4 == 2))"),
//          stringPattern.format("(intVal % 4 == 2)","Option((intVal % 4 == 2))")
//        ),
//        List(
//          stringPattern.format("'5'","Some('5')"),
//          stringPattern.format("'5'","Option('5')")
//        ),
//        List(
//          stringPattern.format("\"asd\"","Some(\"asd\")"),
//          stringPattern.format("\"asd\"","Option(\"asd\")")
//        ),
//        List(
//          stringPattern.format("5.3f","Some(5.3f)"),
//          stringPattern.format("5.3f","Option(5.3f)")
//        )
          ))
  }
}