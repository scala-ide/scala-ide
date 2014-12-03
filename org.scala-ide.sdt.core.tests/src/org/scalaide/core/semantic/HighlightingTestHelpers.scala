/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.core.semantic

import org.junit.Assert
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.core.compiler.IScalaPresentationCompiler

class HighlightingTestHelpers(projectSetup: TestProjectSetup) {

  def withCompilationUnitAndCompiler(path: String)(test: (IScalaPresentationCompiler, ScalaCompilationUnit) => Unit) {

    val unit = projectSetup.scalaCompilationUnit(path)

    if (!unit.exists()) throw new IllegalArgumentException(s"File at '$path' does not exist!")

    unit.withSourceFile { (src, compiler) =>
      compiler.askReload(List(unit)).get

      compiler.askLoadedTyped(src, false).get
      test(compiler, unit)
    }
  }

  def assertSameLists(l1: List[String], l2: List[String]) {
    Assert.assertEquals(l1.mkString("\n"), l2.mkString("\n"))
  }
}