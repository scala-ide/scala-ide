package org.scalaide.ui.wizards

import org.eclipse.core.runtime.Path
import org.junit.ComparisonFailure
import org.junit.Test

class ScalaFileCreatorInitialPathTest extends ScalaFileCreator {

  implicit class Implicit_===(path: String) {
    def ===(expected: String): Unit = {
      val isDirectory = !path.endsWith(".scala")
      val srcDirs = Seq("P/src", "P/src2") map (new Path(_))
      val actual = generateInitialPath(new Path(path), srcDirs, isDirectory)
      if (actual != expected)
        throw new ComparisonFailure("", expected, actual)
    }
  }

  @Test
  def empty_string_when_no_file_is_selected() =
    "P" === ""

  @Test
  def empty_string_when_file_is_in_default_package() =
    "P/src/file.scala" === ""

  @Test
  def package_path_with_dot_when_file_is_in_package() = {
    "P/src/a/file.scala" === "a."
    "P/src/a/b/file.scala" === "a.b."
    "P/src/a/b/c/file.scala" === "a.b.c."
  }

  @Test
  def package_path_with_dot_when_package_is_selected() = {
    "P/src/a" === "a."
    "P/src/a/b" === "a.b."
    "P/src/a/b/c" === "a.b.c."
  }

  @Test
  def empty_string_when_file_is_not_is_source_folder() =
    "P/folder/file.scala" === ""
}