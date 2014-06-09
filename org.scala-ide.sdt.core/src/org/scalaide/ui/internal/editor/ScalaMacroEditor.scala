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

object myLog { //TODO: remove
  import java.io.PrintWriter
  import java.io.File
  val writer = new PrintWriter(new File("/home/nikiforo/logger.log"))
  def log(s: String) {
    writer.write(s + "\n")
    writer.flush
  }
  def log(any: Any) {
    log(any.toString + "\n")
  }
}

trait ScalaMacroLineNumbers { self: ScalaMacroEditor =>
  import org.eclipse.jface.text.source.LineNumberChangeRulerColumn
  import org.eclipse.jface.text.source.ISharedTextColors

  class MyRange(val startLine: Int, val endLine: Int) {}

  var macroExpansionLines: List[MyRange] = Nil

  var correspondingLineNumbers = new Array[Int](10000)   // FIXME: get length of document.
  
  def computeCorrespondingLineNumbers{
    val ranges = getMacroExpansionNotCountedLines
    def containedInMacroExpansion(lineNum: Int) = {
      val rangesThatContain = ranges.filter(x=> x.startLine <= lineNum && lineNum <= x.endLine)
      !rangesThatContain.isEmpty
    }
    correspondingLineNumbers(0) = 0
    val l = correspondingLineNumbers.length
    for (lineNum <- 1 to correspondingLineNumbers.length - 1) {      
      if (containedInMacroExpansion(lineNum)) correspondingLineNumbers(lineNum) = correspondingLineNumbers(lineNum - 1)
      else correspondingLineNumbers(lineNum) = correspondingLineNumbers(lineNum - 1) + 1
    }
  }  

  class LineNumberChangeRulerColumnWithMacro(sharedColors: ISharedTextColors)
    extends LineNumberChangeRulerColumn(sharedColors) {
    override def createDisplayString(line: Int): String = {
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

        document.replace(pOffset, pLength, indentedMacroExpansion)

        val marker = editorInput.asInstanceOf[FileEditorInput].getFile.createMarker("scala.tools.eclipse.macroMarkerId")
        marker.setAttribute(IMarker.CHAR_START, pOffset)
        marker.setAttribute(IMarker.CHAR_END, pOffset + indentedMacroExpansion.length)
        marker.setAttribute("macroExpandee", macroExpandee)
        marker.setAttribute("macroExpansion", indentedMacroExpansion)

        annotationModel.removeAnnotation(annotation)
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
      for(doc <- iTextEditor.asInstanceOf[ScalaMacroEditor].document){
        iTextEditor.asInstanceOf[ScalaMacroLineNumbers].correspondingLineNumbers = new Array[Int](doc.getNumberOfLines)
        iTextEditor.asInstanceOf[ScalaMacroLineNumbers].computeCorrespondingLineNumbers 
      } 
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

trait ScalaMacroEditor extends CompilationUnitEditor with ScalaMacroLineNumbers {
  //TODO: out of sync(press F5) doesn't work
  //TODO: maybe add listener to macroApplication's change? If changed replace macro expansion
  //TODO: macroexpansions do not change untill save command triggered
  protected var iEditor: Option[IEditorInput] = None
  def document: Option[IDocument] = iEditor.map(getDocumentProvider.getDocument(_))
  protected def annotationModel: Option[IAnnotationModel] = iEditor.map(getDocumentProvider.getAnnotationModel(_))

  override def performSave(overwrite: Boolean, progressMonitor: IProgressMonitor) {
    removeMacroExpansions
    super.performSave(overwrite, progressMonitor)    
    expandMacroExpansions
  }

  override def doSetInput(iEditorInput: IEditorInput) {
    iEditor = Option(iEditorInput)
    super.doSetInput(iEditorInput)
    for(doc <- document){
//      correspondingLineNumbers = new Array[Int](doc.getNumberOfLines) FIXME: get number of lines. Here document is uninitialized
      computeCorrespondingLineNumbers
    }
  }

  private def expandMacroExpansions {
    val annotations = annotationModel.map(_.getAnnotationIterator)
    for {
      doc <- document
      annotationModel <- annotationModel
      annotations <- annotations
      annotationNoType <- annotations
    } {
      val annotation = annotationNoType.asInstanceOf[Annotation]
      if (annotation.getType == "scala.tools.eclipse.macroMarkerId") {
        val pos = annotationModel.getPosition(annotation)

        val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker

        doc.replace(pos.offset, pos.length, marker.getAttribute("macroExpansion").asInstanceOf[String])
      }
    }
  }
  
  private def removeMacroExpansions {
    val annotations = annotationModel.map(_.getAnnotationIterator)
    for {
      doc <- document
      annotationModel <- annotationModel
      annotations <- annotations
      annotationNoType <- annotations
    } {
      val annotation = annotationNoType.asInstanceOf[Annotation]
      if (annotation.getType == "scala.tools.eclipse.macroMarkerId") {
        val pos = annotationModel.getPosition(annotation)

        val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker

        doc.replace(pos.offset, pos.length, marker.getAttribute("macroExpandee").asInstanceOf[String])
      }
    }
  }
}