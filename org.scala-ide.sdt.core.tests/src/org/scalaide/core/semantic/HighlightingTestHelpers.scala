/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.core.semantic

import org.junit.Assert
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.scalaide.core.testsetup.TestProjectSetup

class HighlightingTestHelpers(projectSetup: TestProjectSetup) {

  def withCompilationUnitAndCompiler(path: String)(test: (ScalaPresentationCompiler, ScalaCompilationUnit) => Unit) {

    val unit = projectSetup.scalaCompilationUnit(path)

    if (!unit.exists()) throw new IllegalArgumentException(s"File at '$path' does not exist!")

    unit.withSourceFile { (src, compiler) =>
      val dummy = new compiler.Response[Unit]
      compiler.askReload(List(src), dummy)
      dummy.get

      val tree = new compiler.Response[compiler.Tree]
      compiler.askType(src, false, tree)
      tree.get
      test(compiler, unit)
    }
  }

  def assertSameLists(l1: List[String], l2: List[String]) {
    Assert.assertEquals(l1.mkString("\n"), l2.mkString("\n"))
  }
}