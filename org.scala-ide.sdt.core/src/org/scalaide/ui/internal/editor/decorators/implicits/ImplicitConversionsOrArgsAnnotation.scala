/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.editor.decorators.implicits

import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.hyperlink.IHyperlink

object ImplicitAnnotation {
  final val ID = "scala.tools.eclipse.semantichighlighting.implicits.implicitConversionsOrArgsAnnotation"
}

abstract class ImplicitAnnotation(text: String) extends Annotation(ImplicitAnnotation.ID, /*isPersistent*/ false, text)

/** The source of this implicit conversion is computed lazily, only when needed. */
class ImplicitConversionAnnotation(_sourceLink: () => Option[IHyperlink], text: String) extends ImplicitAnnotation(text) {
  lazy val sourceLink = _sourceLink()
}

class ImplicitArgAnnotation(text: String) extends ImplicitAnnotation(text)
