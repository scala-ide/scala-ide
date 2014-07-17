/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.core.semantic

import org.junit.Test
import org.scalaide.ui.internal.editor.decorators.custom.AllMethodsTraverserDef
import org.scalaide.ui.internal.editor.decorators.custom.TraverserDef.TypeDefinition

class CustomClassHighlightingTest
  extends HighlightingTestHelpers(CustomHighlightingTest)
  with CustomHighlightingTest {

  @Test
  def scalaCollectionMutableHighlighting() {
    withCompilationUnitAndCompiler("custom/ScalaCollectionMutable.scala") { (spc, scu) =>
      val traversers = Seq(
        AllMethodsTraverserDef(
          message = "'scala.collection.mutable' call type found",
          typeDefinition = TypeDefinition("scala" :: "collection" :: "mutable" :: Nil, "Traversable")))

      val expected = List(
        "'scala.collection.mutable' call type found [181, 5]",
        "'scala.collection.mutable' call type found [195, 5]",
        "'scala.collection.mutable' call type found [266, 3]")
      val actual = annotations("scalaCollectionMutable")(traversers)(spc, scu)

      assertSameLists(expected, actual)
    }
  }

  // TODO - flaky test :(
  @Test
  def customTypeHighlighting() {
    withCompilationUnitAndCompiler("custom/Types.scala") { (spc, scu) =>
      val traversers = Seq(
        AllMethodsTraverserDef(
          message = "'types.Base' type found",
          typeDefinition = TypeDefinition("types" :: Nil, "Base")))

      val expected = List(
        "'types.Base' type found [252, 1]",
        "'types.Base' type found [261, 17]",
        "'types.Base' type found [313, 1]",
        "'types.Base' type found [365, 1]")
      val actual = annotations("baseType")(traversers)(spc, scu)

      assertSameLists(expected, actual)
    }
  }
}