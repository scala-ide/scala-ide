package org.scalaide.ui.editor

import org.eclipse.jface.text.source.Annotation

/**
 * Marker interface for all annotations that are created for the Scala editor.
 * Features of the Scala editor like quick assists can search for  annotations
 * of this type to operate on them.
 */
trait ScalaEditorAnnotation extends Annotation
