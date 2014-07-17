/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.core.semantic

import org.junit.Test
import org.scalaide.ui.internal.editor.decorators.custom.AnnotationTraverserDef
import org.scalaide.ui.internal.editor.decorators.custom.TraverserDef.AnnotationDefinition

class CustomAnnotationHighlightingTest
  extends HighlightingTestHelpers(CustomHighlightingTest)
  with CustomHighlightingTest {

  private def fooAnnotationTraverser(pack: String) = AnnotationTraverserDef(
    message = "'annotations.foo' annotation found",
    annotation = AnnotationDefinition("annotations" :: pack :: Nil, "foo"))

  private def fooAnnotations = annotations("fooAnnotated") _

  @Test
  def customValAnnotationHighlighting() {
    withCompilationUnitAndCompiler("custom/AnnotationsVal.scala") { (src, compiler) =>

      val expected = List("'annotations.foo' annotation found [237, 10]")
      val actual = fooAnnotations(List(fooAnnotationTraverser("value")))(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def customVarAnnotationHighlighting() {
    withCompilationUnitAndCompiler("custom/AnnotationsVar.scala") { (src, compiler) =>

      val expected = List("'annotations.foo' annotation found [240, 10]")
      val actual = fooAnnotations(List(fooAnnotationTraverser("variable")))(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def customValInBodyAnnotationHighlighting() {
    withCompilationUnitAndCompiler("custom/AnnotationsValInBody.scala") { (src, compiler) =>

      val expected = List("'annotations.foo' annotation found [201, 3]",
        "'annotations.foo' annotation found [218, 8]")
      val actual = fooAnnotations(List(fooAnnotationTraverser("valInBody")))(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def customVarInBodyAnnotationHighlighting() {
    withCompilationUnitAndCompiler("custom/AnnotationsVarInBody.scala") { (src, compiler) =>

      val expected = List("'annotations.foo' annotation found [201, 3]",
        "'annotations.foo' annotation found [218, 8]")
      val actual = fooAnnotations(List(fooAnnotationTraverser("varInBody")))(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def customValInConstructorAnnotationHighlighting() {
    withCompilationUnitAndCompiler("custom/AnnotationsValConstructor.scala") { (src, compiler) =>

      val expected = List("'annotations.foo' annotation found [284, 10]")
      val actual = fooAnnotations(List(fooAnnotationTraverser("valConstructor")))(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def customVarInConstructorAnnotationHighlighting() {
    withCompilationUnitAndCompiler("custom/AnnotationsVarConstructor.scala") { (src, compiler) =>

      val expected = List("'annotations.foo' annotation found [282, 10]")
      val actual = fooAnnotations(List(fooAnnotationTraverser("varConstructor")))(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def customValInCaseConstructorAnnotationHighlighting() {
    withCompilationUnitAndCompiler("custom/AnnotationsValCaseConstructor.scala") { (src, compiler) =>

      val expected = List("'annotations.foo' annotation found [293, 10]")
      val actual = fooAnnotations(List(fooAnnotationTraverser("valCaseConstructor")))(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def customVarInCaseConstructorAnnotationHighlighting() {
    withCompilationUnitAndCompiler("custom/AnnotationsVarCaseConstructor.scala") { (src, compiler) =>

      val expected = List("'annotations.foo' annotation found [291, 10]")
      val actual = fooAnnotations(List(fooAnnotationTraverser("varCaseConstructor")))(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def customDefAnnotationHighlighting() {
    withCompilationUnitAndCompiler("custom/AnnotationsDef.scala") { (src, compiler) =>

      val expected = List("'annotations.foo' annotation found [240, 10]")
      val actual = fooAnnotations(List(fooAnnotationTraverser("method")))(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def customParameterlessDefAnnotationHighlighting() {
    withCompilationUnitAndCompiler("custom/AnnotationsDefParensless.scala") { (src, compiler) =>

      val expected = List("'annotations.foo' annotation found [251, 10]")
      val actual = fooAnnotations(List(fooAnnotationTraverser("parameterlessMethod")))(src, compiler)

      assertSameLists(expected, actual)
    }
  }

}