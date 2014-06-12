package org.scalaide.ui.internal.editor

import org.eclipse.jface.text.IDocument
import org.eclipse.ui.IEditorInput
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.text.Position
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IMarker
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.jface.text.source.IAnnotationModel
import collection.JavaConversions._
import org.eclipse.jface.text.source.Annotation
import org.eclipse.ui.texteditor.MarkerAnnotation
import org.eclipse.jdt.ui.text.folding.DefaultJavaFoldingStructureProvider
import org.eclipse.jdt.ui.text.folding.IJavaFoldingStructureProvider
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.eclipse.ui.texteditor.AbstractRulerActionDelegate
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jface.text.source.IVerticalRulerInfo
import org.eclipse.jface.text.source.IAnnotationModelListener
import org.scalaide.ui.internal.editor.decorators.implicits.MacroExpansionAnnotation

//object myLog { //TODO: remove
//  import java.io.PrintWriter
//  import java.io.File
//  val writer = new PrintWriter(new File("/home/nikiforo/logger.log"))
//  def log(s: String) {
//    writer.write(s + "\n")
//    writer.flush
//  }
//  def log(any: Any) {
//    log(any.toString + "\n")
//  }
//}

//FOR DEBUG
//import java.io.PrintWriter
//import java.io.File
//val writer = new PrintWriter(new File("/home/nikiforo/logger.log"))
//writer.write("SECRET PHRASE I")
//writer.flush


trait ScalaMacroLineNumbers { self: ScalaMacroEditor =>
  import org.eclipse.jface.text.source.LineNumberChangeRulerColumn
  import org.eclipse.jface.text.source.ISharedTextColors

  class MyRange(val startLine: Int, val endLine: Int) {}

  var macroExpansionLines: List[MyRange] = Nil

  var correspondingLineNumbers: Array[Int] = new Array[Int](10000)  //FIXME: size of array for real-pseudo lines

  def computeCorrespondingLineNumbers {
    val ranges = getMacroExpansionNotCountedLines
    def containedInMacroExpansion(lineNum: Int) = {
      val rangesThatContain = ranges.filter(x => x.startLine <= lineNum && lineNum <= x.endLine)
      !rangesThatContain.isEmpty
    }
    correspondingLineNumbers(0) = 0
    val l = correspondingLineNumbers.length
    for (lineNum <- 1 to correspondingLineNumbers.length -1) {
      if (containedInMacroExpansion(lineNum)) correspondingLineNumbers(lineNum) = correspondingLineNumbers(lineNum - 1)
      else correspondingLineNumbers(lineNum) = correspondingLineNumbers(lineNum - 1) + 1
    }
  }

  class LineNumberChangeRulerColumnWithMacro(sharedColors: ISharedTextColors)
    extends LineNumberChangeRulerColumn(sharedColors) {
    override def createDisplayString(line: Int): String = {
//      if (!correspondingLineNumbers.isDefined) (line + 1).toString
      (correspondingLineNumbers(line) + 1).toString
    }
  }

  def getMacroExpansionNotCountedLines = {
    def getCurrentMacroPositions(annotationModel: Option[IAnnotationModel]) = {
      val annotations = annotationModel.map(_.getAnnotationIterator)

      var t: List[Position] = Nil
      for {
        doc <- document
        annotationModel <- annotationModel
        annotations <- annotations
        annotationNoType <- annotations
      } {
        val annotation = annotationNoType.asInstanceOf[Annotation]
        if (annotation.getType == "scala.tools.eclipse.macroMarkerId") {
          t = annotationModel.getPosition(annotation) :: t
        }
      }
      t
    }

    for {
      currentMacroPosition <- getCurrentMacroPositions(annotationModel)
      doc <- document
    } yield new MyRange(
      doc.getLineOfOffset(currentMacroPosition.offset) + 1, //First expanded line is the line of macroExpandee
      doc.getLineOfOffset(currentMacroPosition.offset + currentMacroPosition.length))
  }
}

class MacroAnnotationActionDelegate extends AbstractRulerActionDelegate {
  import org.eclipse.jface.action.Action
  import org.eclipse.jface.action.IAction
  import org.eclipse.ui.texteditor.TextEditorAction

  var macroRulerAction: Option[MacroRulerAction] = None

  class MacroRulerAction(val iTextEditor: ITextEditor, val iVerticalRulerInfo: IVerticalRulerInfo) extends Action {
    private val editorInput = iTextEditor.getEditorInput
    private val annotationModel: IAnnotationModel = iTextEditor.getDocumentProvider.getAnnotationModel(editorInput)
    private val document = iTextEditor.getDocumentProvider.getDocument(editorInput)

    private def findAnnotationsOnLine(line: Int, annotationType: String) = {
      val annotationIterator = for {
        annotationNoType <- annotationModel.getAnnotationIterator
        annotation = annotationNoType.asInstanceOf[Annotation]
        if annotation.getType == annotationType
        pos = annotationModel.getPosition(annotation)
        if document.getLineOfOffset(pos.offset) == line
      } yield annotation
      annotationIterator.toList
    }

    override def run {
      val line = iVerticalRulerInfo.getLineOfLastMouseButtonActivity

      val annotations2Expand = findAnnotationsOnLine(line, "scala.tools.eclipse.semantichighlighting.implicits.MacroExpansionAnnotation")

      annotations2Expand.foreach(annotation => if (!annotation.isMarkedDeleted) {
        val position = annotationModel.getPosition(annotation)
        val (pOffset, pLength) = (position.offset, position.length)
        val macroExpandee = document.get(pOffset, pLength)

        val macroExpansion = annotation.getText

        val macroLineStartPos = document.getLineOffset(document.getLineOfOffset(pOffset))
        val prefix = document.get(macroLineStartPos, pOffset - macroLineStartPos).takeWhile(_ == ' ')

        val splittedMacroExpansion = macroExpansion.split("\n")
        val indentedMacroExpansion = (splittedMacroExpansion.head +:
          splittedMacroExpansion.tail.map(prefix + _)).mkString("\n")

        if(!annotationModel.getAnnotationIterator.toList.map(_.asInstanceOf[Annotation]).exists(annotation =>{
          val pos = annotationModel.getPosition(annotation)
          annotation.getType == "scala.tools.eclipse.macroMarkerId" &&
          pos.offset <= pOffset &&
          pOffset + pLength <= pos.offset + pos.length
        })){
          val marker = editorInput.asInstanceOf[FileEditorInput].getFile.createMarker("scala.tools.eclipse.macroMarkerId")
          marker.setAttribute(IMarker.CHAR_START, pOffset)
          marker.setAttribute(IMarker.CHAR_END, pOffset + pLength)
          marker.setAttribute("macroExpandee", macroExpandee)
          marker.setAttribute("macroExpansion", indentedMacroExpansion)
        }

        annotationModel.removeAnnotation(annotation)
        document.replace(pOffset, pLength, indentedMacroExpansion)
      })

      if (annotations2Expand.isEmpty) {
        val annotations2Collapse = findAnnotationsOnLine(line, "scala.tools.eclipse.macroMarkerId")

        annotations2Collapse.foreach(annotation => {
          val position = annotationModel.getPosition(annotation)

          val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker
          val macroExpandee = marker.getAttribute("macroExpandee").asInstanceOf[String]

          val macroLineStartPos = document.getLineOffset(document.getLineOfOffset(position.offset))
          val prefix = document.get(macroLineStartPos, position.offset - macroLineStartPos).takeWhile(_ == ' ')

          document.replace(position.offset, position.length, macroExpandee)

          marker.delete
          annotationModel.removeAnnotation(annotation)
        })
      }
//      iTextEditor.asInstanceOf[ScalaMacroLineNumbers].correspondingLineNumbers = Some(new Array[Int](document.getNumberOfLines))
      iTextEditor.asInstanceOf[ScalaMacroLineNumbers].computeCorrespondingLineNumbers
    }
  }

  import org.eclipse.swt.events.MouseEvent
  override def mouseDown(mouseEventm: MouseEvent) {
    macroRulerAction.map(_.run())
  }
  def createAction(editor: ITextEditor, rulerInfo: IVerticalRulerInfo): IAction = {
    val t = new MacroRulerAction(editor, rulerInfo)
    macroRulerAction = Some(t)
    t
  }
}

trait ScalaMacroEditor extends CompilationUnitEditor with ScalaMacroLineNumbers { self =>
  class Test extends IAnnotationModelListener {
    override def modelChanged(model: IAnnotationModel) {
      val annotations = annotationModel.map(_.getAnnotationIterator)
      for {
        doc <- document
        annotationModel <- annotationModel
        annotations <- annotations
        annotationsList = annotations.toList
        annotationNoType <- annotationsList
      } {
        val annotation = annotationNoType.asInstanceOf[Annotation]
        if (annotation.getType == "scala.tools.eclipse.macro2expand") {
          val pos = annotationModel.getPosition(annotation)

          val newAnnotations = annotationModel.getAnnotationIterator.toList

          val y = newAnnotations.map(_.asInstanceOf[Annotation]).filter(annotation => {
            val posT = annotationModel.getPosition(annotation)
            annotation.getType == MacroExpansionAnnotation.ID &&
              posT.getOffset == pos.getOffset &&
              posT.getLength == pos.getLength
          })

          if (y.length == 1) {
            val t = y.head
            val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker
            val macroExpandee = doc.get(pos.offset, pos.length)

            val macroExpansion = t.getText

            val macroLineStartPos = doc.getLineOffset(doc.getLineOfOffset(pos.offset))
            val prefix = doc.get(macroLineStartPos, pos.offset - macroLineStartPos).takeWhile(_ == ' ')
            val splittedMacroExpansion = macroExpansion.split("\n")
            val indentedMacroExpansion = (splittedMacroExpansion.head +:
              splittedMacroExpansion.tail.map(prefix + _)).mkString("\n")

            marker.delete
            annotationModel.removeAnnotation(annotation)
            annotationModel.removeAnnotation(t)

            doc.replace(pos.offset, pos.length, indentedMacroExpansion)

            iEditor.map(editorInput => {
              val marker2 = editorInput.asInstanceOf[FileEditorInput].getFile.createMarker("scala.tools.eclipse.macroMarkerId")
              marker2.setAttribute(IMarker.CHAR_START, pos.offset)
              marker2.setAttribute(IMarker.CHAR_END, pos.offset + indentedMacroExpansion.length)
              marker2.setAttribute("macroExpandee", macroExpandee)
              marker2.setAttribute("macroExpansion", indentedMacroExpansion)
            })
          }
        }
      }
    }
  }

  protected var iEditor: Option[IEditorInput] = None
  def document: Option[IDocument] = iEditor.map(getDocumentProvider.getDocument(_))
  protected def annotationModel: Option[IAnnotationModel] = iEditor.map(getDocumentProvider.getAnnotationModel(_))

  override def performSave(overwrite: Boolean, progressMonitor: IProgressMonitor) {
    removeMacroExpansions
    super.performSave(overwrite, progressMonitor)
  }

  override def doSetInput(iEditorInput: IEditorInput) {
    iEditor = Option(iEditorInput)
    super.doSetInput(iEditorInput)
    computeCorrespondingLineNumbers
    annotationModel.map(_.addAnnotationModelListener(new Test))
  }

  private def removeMacroExpansions {
    val annotations = annotationModel.map(_.getAnnotationIterator)
    for {
      doc <- document
      iEditor <- iEditor
      annotationModel <- annotationModel
      annotations <- annotations
      annotationNoType <- annotations
    } {
      val annotation = annotationNoType.asInstanceOf[Annotation]
      if (annotation.getType == "scala.tools.eclipse.macroMarkerId") {
        val pos = annotationModel.getPosition(annotation)

        val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker
        val macroExpandee = marker.getAttribute("macroExpandee").asInstanceOf[String]
        marker.delete
        annotationModel.removeAnnotation(annotation)

        val marker2expand = iEditor.asInstanceOf[FileEditorInput].getFile.createMarker("scala.tools.eclipse.macro2expand")
        marker2expand.setAttribute(IMarker.CHAR_START, pos.offset)
        marker2expand.setAttribute(IMarker.CHAR_END, pos.offset + pos.length)

        doc.replace(pos.offset, pos.length, macroExpandee)
      }
    }
  }
}