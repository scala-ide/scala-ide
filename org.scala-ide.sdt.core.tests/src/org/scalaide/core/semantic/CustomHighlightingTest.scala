/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.core.semantic

import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.ui.internal.editor.decorators.custom.TraverserDef
import org.scalaide.ui.internal.editor.decorators.custom.TraverserImpl

object CustomHighlightingTest extends TestProjectSetup("custom-highlighting")

trait CustomHighlightingTest {

  def annotations(annotationId: String)(traversers: Seq[TraverserDef])(compiler: ScalaPresentationCompiler, scu: ScalaCompilationUnit): List[String] = {
    val result = for {
      traverser <- traversers.toList
      (ann, pos) <- TraverserImpl.extract(compiler)(scu.sourceFile, annotationId, Seq(traverser.init(compiler)))
    } yield ann.getText() + " [" + pos.getOffset() + ", " + pos.getLength() + "]"

    result sortBy identity
  }

}