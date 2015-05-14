/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.core.semantic

import org.junit.Test
import org.scalaide.ui.internal.editor.decorators.custom.AllMethodsTraverserDef
import org.scalaide.ui.internal.editor.decorators.custom.TraverserDef.TypeDefinition

class CustomHighlightingImprovedMessageTest
  extends HighlightingTestHelpers(CustomHighlightingTest)
  with CustomHighlightingTest {

  @Test
  def scalaCollectionMutableHighlighting(): Unit = {
    withCompilationUnitAndCompiler("custom/ScalaCollectionMutable.scala") { (spc, scu) =>
      val traversers = Seq(
        AllMethodsTraverserDef(
          message = select => s"'scala.collection.mutable' call type found on $select",
          typeDefinition = TypeDefinition("scala" :: "collection" :: "mutable" :: Nil, "Traversable")))

      val expected = List(
        "'scala.collection.mutable' call type found on map.foreach [266, 3]",
        "'scala.collection.mutable' call type found on mySeq.foreach [195, 5]",
        "'scala.collection.mutable' call type found on mySeq.head [181, 5]")
      val actual = annotations("scalaCollectionMutable")(traversers)(spc, scu)

      assertSameLists(expected, actual)
    }
  }

}