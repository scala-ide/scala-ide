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

object SuperCompiler { //TODO: remove
  val showCode = """val t="Hello world" 
					 |t + "!"
					 |println(t)
					 |""".stripMargin
}

trait ScalaMacroLineNumbers { self: ScalaMacroEditor =>
  import org.eclipse.jface.text.source.LineNumberChangeRulerColumn
  import org.eclipse.jface.text.source.ISharedTextColors

  class MyRange(val start: Int, val end: Int) {}

  var macroExpansionLines: List[MyRange] = Nil

  class LineNumberChangeRulerColumnWithMacro(sharedColors: ISharedTextColors)
    extends LineNumberChangeRulerColumn(sharedColors) {
    override def createDisplayString(line: Int): String = {
      getMacroExpansionLines
      macroExpansionLines.flatMap(range =>
        if (range.start <= line && line < range.end) {
          Some(range.start - 1)
        } else None).reduceOption(_ min _).getOrElse(line).toString
    }
  }

  def getCurrentMacroPositions = {
    val annotationsOpt = annotationModel.map(_.getAnnotationIterator)

    var t: List[Position] = Nil
    for {
      doc <- document
      annotationModel <- annotationModel
      annotations <- annotationsOpt
      annotationNoType <- annotations
    } {
      val annotation = annotationNoType.asInstanceOf[Annotation]
      if (annotation.getType == "scala.tools.eclipse.macroMarkerId") {
        t = annotationModel.getPosition(annotation) :: t
      }
    }
    t
  }

  def getMacroExpansionLines() {
    val currentMacroPositions = getCurrentMacroPositions
    macroExpansionLines =
      for {
        currentMacroPosition <- currentMacroPositions
        doc <- document
      } yield new MyRange(
        doc.getLineOfOffset(currentMacroPosition.offset),
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
    
    /* Used because if editing after the annotation eclipse adds extra 
     * annotations with the same positions*/
    private def annotationsUniquePos(annotations: List[Annotation]) = {
      import scala.collection.mutable.Set
      val setToDrop = Set[(Int,Int)]()      
      val t = (for{
         annotation <- annotations
         pos = annotationModel.getPosition(annotation)         
      } yield {
        if(setToDrop.contains(pos.offset,pos.length)) {
          annotationModel.removeAnnotation(annotation)
          None
        } else{
          setToDrop.add((pos.offset,pos.length))
          Some(annotation)
        }        
      })
      t.flatten
    }
    
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
      
      val annotations2Expand = annotationsUniquePos(findAnnotationsOnLine(line, "scala.tools.eclipse.semantichighlighting.implicits.MacroExpansionAnnotation")) 
      
      annotations2Expand.foreach(annotation => if(!annotation.isMarkedDeleted) {
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
  
        annotationModel.removeAnnotation(annotation)
      })
      
      if(annotations2Expand.isEmpty){
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
  protected def document: Option[IDocument] = iEditor.map(getDocumentProvider.getDocument(_))
  protected def annotationModel: Option[IAnnotationModel] = iEditor.map(getDocumentProvider.getAnnotationModel(_))

  override def performSave(overwrite: Boolean, progressMonitor: IProgressMonitor) {
    removeMacroExpansions
    super.performSave(overwrite, progressMonitor)
  }

  override def doSetInput(iEditorInput: IEditorInput) {
    iEditor = Option(iEditorInput)
    super.doSetInput(iEditorInput)
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
        marker.delete
      }
    }
  }
}