/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/license.html
 */
package org.scalaide.core
package semantic

import org.scalaide.core.IScalaPlugin
import org.scalaide.core.testsetup.TestProjectSetup
import org.junit.Before
import org.junit.Test
import org.scalaide.ui.internal.preferences.ImplicitsPreferencePage
import org.scalaide.ui.internal.editor.decorators.implicits.ImplicitHighlightingPresenter
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.compiler.IScalaPresentationCompiler

object ImplicitsHighlightingTest extends TestProjectSetup("implicits-highlighting")

class ImplicitsHighlightingTest extends HighlightingTestHelpers(ImplicitsHighlightingTest) {

  @Before
  def setPreferences() {
    IScalaPlugin().getPreferenceStore.setValue(ImplicitsPreferencePage.P_CONVERSIONS_ONLY, false)
  }

  @Test
  def implicitConversion() {
    withCompilationUnitAndCompiler("implicit-highlighting/Implicits.scala") { (src, compiler) =>

      val expected = List(
        "Implicit conversion found: `List(1,2)` => `listToString(List(1,2)): String` [180, 9]",
        "Implicit conversion found: `List(1,2,3)` => `listToString(List(1,2,3)): String` [151, 11]"
      )
      val actual = implicits(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def implicitConversionsFromPredef() {
    withCompilationUnitAndCompiler("implicit-highlighting/DefaultImplicits.scala") { (src, compiler) =>

      val expected = List(
        "Implicit conversion found: `4` => `int2Integer(4): Integer` [74, 1]"
      )
      val actual = implicits(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def implicitArguments() {
    withCompilationUnitAndCompiler("implicit-highlighting/ImplicitArguments.scala") {(src, compiler) =>

      val expected = List (
        "Implicit arguments found: `takesImplArg` => `takesImplArg( implicits.ImplicitArguments.s )` [118, 12]"
      )
      val actual = implicits(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  def implicits(compiler: IScalaPresentationCompiler, scu: ScalaCompilationUnit) = {
    val implicits = ImplicitHighlightingPresenter.findAllImplicitConversions(compiler, scu, scu.lastSourceMap().sourceFile)
    implicits.toList map {
      case (ann, p) =>
        ann.getText() +" ["+ p.getOffset() + ", "+ p.getLength() +"]"
    } sortBy identity
  }
}