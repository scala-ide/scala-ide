package org.scalaide.core.quickassist

import org.junit.Test

class TypeMismatchTests {
  import UiQuickAssistTests._

  val stringPattern: String = "Transform expression: %s => %s"

  @Test
  def basicTypeMismatchQuickFixes(): Unit = {
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
          stringPattern.format("arrayOfInt_val", "arrayOfInt_val.toArray"))))
  }
}
