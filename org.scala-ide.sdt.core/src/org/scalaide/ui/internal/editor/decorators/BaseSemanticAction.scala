/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.editor.decorators

import scala.reflect.internal.util.SourceFile
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.text.IPainter
import org.eclipse.jface.text.{ Position => JFacePosition }
import org.eclipse.jface.text.TextViewer
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.AnnotationPainter
import org.eclipse.jface.text.source.IAnnotationAccess
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.SWT
import org.eclipse.ui.editors.text.EditorsUI
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.eclipse.AnnotationUtils
import org.scalaide.util.internal.eclipse.EclipseUtils
import org.scalaide.ui.internal.editor.decorators.implicits.MacroExpansionAnnotation

/**
 * Represents basic properties - enabled, bold an italic.
 *
 * Format of properties:
 *
 * scala.tools.eclipse.ui.preferences.$preferencePageId.(enabled|text.italic|text.bold)
 */
private[decorators] class Properties(preferencePageId: String) {
  private val base = s"scala.tools.eclipse.ui.preferences.$preferencePageId."

  val active = base + "enabled"

  val bold = base + "text.bold"

  val italic = base + "text.italic"
}

/**
 * Base for creating custom semantic actions.
 *
 * If you provide a `preferencePageId`, you have to create a properties page for it
 * with properties matching those from [[org.scalaide.ui.internal.editor.decorators.Properties]] class.
 *
 * @param sourceViewer
 * @param annotationId id of annotation (must match id from plugin.xml)
 * @param preferencePageId id of preference page, optional
 */
abstract class BaseSemanticAction(
  sourceViewer: ISourceViewer,
  annotationId: String,
  preferencePageId: Option[String])
  extends SemanticAction
  with HasLogger {

  private val propertiesOpt = preferencePageId.map(id => new Properties(id))

  protected val annotationAccess = new IAnnotationAccess {
    def getType(annotation: Annotation) = annotation.getType
    def isMultiLine(annotation: Annotation) = true
    def isTemporary(annotation: Annotation) = true
  }

  protected def pluginStore: IPreferenceStore = ScalaPlugin.plugin.getPreferenceStore

  protected def isFontStyleBold = propertiesOpt match {
    case Some(properties) if pluginStore.getBoolean(properties.bold) => SWT.BOLD
    case _ => SWT.NORMAL
  }

  protected def isFontStyleItalic = propertiesOpt match {
    case Some(properties) if pluginStore.getBoolean(properties.italic) => SWT.ITALIC
    case _ => SWT.NORMAL
  }

  protected lazy val P_COLOR = {
    val lookup = new org.eclipse.ui.texteditor.AnnotationPreferenceLookup()
    val pref = lookup.getAnnotationPreference(annotationId)
    pref.getColorPreferenceKey()
  }

  protected def colorValue = {
    val rgb = PreferenceConverter.getColor(EditorsUI.getPreferenceStore, P_COLOR)
    ColorManager.colorManager.getColor(rgb)
  }

  protected val textStyleStrategy = new HighlightingTextStyleStrategy(isFontStyleBold | isFontStyleItalic)

  protected val painter: AnnotationPainter = {
    val p = new AnnotationPainter(sourceViewer, annotationAccess)
    p.addAnnotationType(annotationId, annotationId)
    p.addTextStyleStrategy(annotationId, textStyleStrategy)
    //FIXME settings color of the underline is required to active TextStyle (bug ??, better way ??)
    p.setAnnotationTypeColor(annotationId, colorValue)
    val textViewer = sourceViewer.asInstanceOf[TextViewer]
    textViewer.addPainter(p)
    textViewer.addTextPresentationListener(p)
    p
  }

  protected def findAll(compiler: ScalaPresentationCompiler, scu: ScalaCompilationUnit, sourceFile: SourceFile): Map[Annotation, JFacePosition]

  //TODO monitor P_ACTIVATE to register/unregister update
  //TODO monitor P_ACTIVATE to remove existings annotation (true => false) or update openning file (false => true)
  override def apply(scu: ScalaCompilationUnit): Unit = {
    scu.doWithSourceFile { (sourceFile, compiler) =>

      def findAnnotations(): Map[Annotation, JFacePosition] = {
        val response = new compiler.Response[compiler.Tree]
        compiler.askLoadedTyped(sourceFile, response)
        response.get(200) match {
          case Some(Left(_)) => findAll(compiler, scu, sourceFile)
          case Some(Right(exc)) =>
            logger.error(exc); Map.empty
          case None => logger.warn("Timeout while waiting for `askLoadedTyped` during semantic highlighting."); Map.empty
        }
      }

      val annotationsToAdd: Map[Annotation, JFacePosition] = propertiesOpt match {
        case Some(properties) if pluginStore.getBoolean(properties.active) => findAnnotations()
        case None => findAnnotations() // properties disabled, count as active
        case _ => Map.empty
      }

      val (implicitAnnotations, macroExpansionAnnotations) = annotationsToAdd.partition(_._1.getType == annotationId)

      AnnotationUtils.update(sourceViewer, annotationId, implicitAnnotations)
      AnnotationUtils.update(sourceViewer, MacroExpansionAnnotation.ID, macroExpansionAnnotations)
    }
  }

  private val _listener = new IPropertyChangeListener {
    def propertyChange(event: PropertyChangeEvent) {
      propertiesOpt.foreach { properties =>
        val changed = event.getProperty() match {
          case properties.bold | properties.italic | P_COLOR => true
          case properties.active => {
            refresh()
            false
          }
          case _ => false
        }
        if (changed) {
          textStyleStrategy.fontStyle = isFontStyleBold | isFontStyleItalic
          painter.setAnnotationTypeColor(annotationId, colorValue)
          painter.paint(IPainter.CONFIGURATION)
        }
      }
    }
  }

  private def refresh() = {
    import org.scalaide.util.internal.Utils._
    for {
      page <- EclipseUtils.getWorkbenchPages
      editorReference <- page.getEditorReferences
      editorInput <- Option(editorReference.getEditorInput)
      compilationUnit <- Option(JavaPlugin.getDefault.getWorkingCopyManager.getWorkingCopy(editorInput))
      scu <- compilationUnit.asInstanceOfOpt[ScalaCompilationUnit]
    } apply(scu)
  }

  PropertyChangeListenerProxy(_listener, pluginStore, EditorsUI.getPreferenceStore).autoRegister()

}