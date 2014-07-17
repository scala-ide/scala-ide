/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.core.semantic

import org.junit.Test
import org.scalaide.ui.internal.editor.decorators.custom.MethodTraverserDef
import org.scalaide.ui.internal.editor.decorators.custom.TraverserDef.MethodDefinition
import org.scalaide.ui.internal.editor.decorators.custom.AnnotationTraverserDef
import org.scalaide.ui.internal.editor.decorators.custom.TraverserDef.AnnotationDefinition

class CustomMethodHighlightingTest
  extends HighlightingTestHelpers(CustomHighlightingTest)
  with CustomHighlightingTest {

  @Test
  def customMethodHighlighting() {
    withCompilationUnitAndCompiler("custom/Methods.scala") { (src, compiler) =>
      val traversers = Seq(
        MethodTraverserDef(
          message = "'methods.Methods.foo' method found",
          methodDefinition = MethodDefinition("methods" :: Nil, "Methods", "foo")),
        MethodTraverserDef(
          message = "'methods.Methods.bar' method found",
          methodDefinition = MethodDefinition("methods" :: Nil, "Methods", "bar")),
        MethodTraverserDef(
          message = "'methods.Methods.baz' method found",
          methodDefinition = MethodDefinition("methods" :: Nil, "Methods", "baz")))

      val expected = List(
        "'methods.Methods.bar' method found [196, 5]",
        "'methods.Methods.baz' method found [205, 5]",
        "'methods.Methods.foo' method found [187, 5]")
      val actual = annotations("methods")(traversers)(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def customMethodHighlightingWithInheritance() {
    withCompilationUnitAndCompiler("custom/MethodsInheritance.scala") { (src, compiler) =>
      val traversers = Seq(
        MethodTraverserDef(
          message = "'methodsInheritance.Base.foo' method found",
          methodDefinition = MethodDefinition("methodsInheritance" :: Nil, "Base", "foo")),
        MethodTraverserDef(
          message = "'methodsInheritance.Base.bar' method found",
          methodDefinition = MethodDefinition("methodsInheritance" :: Nil, "Base", "bar")),
        MethodTraverserDef(
          message = "'methodsInheritance.Base.baz' method found",
          methodDefinition = MethodDefinition("methodsInheritance" :: Nil, "Base", "baz")))

      val expected = List(
        "'methodsInheritance.Base.bar' method found [353, 5]",
        "'methodsInheritance.Base.bar' method found [407, 5]",
        "'methodsInheritance.Base.baz' method found [362, 5]",
        "'methodsInheritance.Base.baz' method found [416, 5]",
        "'methodsInheritance.Base.foo' method found [344, 5]",
        "'methodsInheritance.Base.foo' method found [398, 5]")
      val actual = annotations("methods")(traversers)(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def mixingMethodsAndAnnotations() {
    withCompilationUnitAndCompiler("custom/Mix.scala") { (src, compiler) =>
      val traversers = Seq(
        MethodTraverserDef(
          message = "'mix.Methods.foo' method found",
          methodDefinition = MethodDefinition("mix" :: Nil, "Methods", "foo")),
        AnnotationTraverserDef(
          message = "'mix.foo' annotation found",
          annotation = AnnotationDefinition("mix" :: Nil, "foo")))

      val expected = List(
        "'mix.Methods.foo' method found [249, 5]",
        "'mix.foo' annotation found [285, 5]")
      val actual = annotations("methods")(traversers)(src, compiler)

      assertSameLists(expected, actual)
    }
  }

}