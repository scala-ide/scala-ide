package org.scalaide.ui.wizards

import org.junit.Test
import org.junit.ComparisonFailure

class ScalaFileCreatorTemplateVariablesTest extends ScalaFileCreator {

  import ScalaFileCreator._

  implicit class Implicit_===(path: String) {
    def ===(expected: Map[String, String]): Unit = {
      val actual = generateTemplateVariables(Seq("src", "src2"), path)
      if (actual != expected)
        throw new ComparisonFailure("", expected.toString(), actual.toString())
    }
  }

  @Test
  def no_variables() = {
    "File" === Map()
    "File.scala" === Map()
    "folder/File" === Map()
    "folder/a/b/c/File" === Map()
  }

  @Test
  def only_file_variable() =
    "src/File" === Map(VariableTypeName -> "File")

  @Test
  def package_and_file_variables() =
    "src/a.b.c.File" === Map(VariablePackageName -> "a.b.c", VariableTypeName -> "File")
}