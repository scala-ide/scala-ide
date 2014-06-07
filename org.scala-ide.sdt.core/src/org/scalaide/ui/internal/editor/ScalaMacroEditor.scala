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
    def format(unformattedScala: String): String = {
//      import scalariform.formatter.preferences._
//      import scalariform.formatter.ScalaFormatter
//      import scalariform.parser.ScalaParserException
            
      // Shouldn't use Scalariform, string numbers can change.
//      val preferences = FormattingPreferences().setPreference(IndentSpaces, 3) // Get preferences from a project      
//      val formatted = ScalaFormatter.format(unformattedScala, preferences) // Should always be valid      
      
      val indentationSpaces = unformattedScala.takeWhile(_ == ' ')
      val formatted = unformattedScala.dropWhile(_ == ' ')
      formatted.split("\n").map(indentationSpaces+_).mkString("\n")
    }
    override def run {
      val line = iVerticalRulerInfo.getLineOfLastMouseButtonActivity
      val editorInput = iTextEditor.getEditorInput      
      val annotationModel: IAnnotationModel = iTextEditor.getDocumentProvider.getAnnotationModel(editorInput)
      val document = iTextEditor.getDocumentProvider.getDocument(editorInput)
      
      //Get MacroExpansionAnnotation annotatations on clicked line
      val annotations2Expand = (for {
        annotationNoType <- annotationModel.getAnnotationIterator
        annotation = annotationNoType.asInstanceOf[Annotation]
        if annotation.getType == "scala.tools.eclipse.semantichighlighting.implicits.MacroExpansionAnnotation"
        pos = annotationModel.getPosition(annotation)
        if document.getLineOfOffset(pos.offset) == line/* && line <= document.getLineOfOffset(pos.offset + pos.length)*/ 
      } yield annotation).toList
      
      //CRLF creates another annotation
      
      annotations2Expand.foreach(annotation => {
        val position = annotationModel.getPosition(annotation)
        val macroExpandee = document.get(position.offset, position.length)
      
      val macroExpansion = annotation.getText
      
      val macroLineStartPos = document.getLineOffset(document.getLineOfOffset(position.offset))
      val prefix = document.get(macroLineStartPos, position.offset - macroLineStartPos).takeWhile(_ == ' ')
      
      val splittedMacroExpansion = macroExpansion.split("\n")
      val indentedMacroExpansion = (splittedMacroExpansion.head +:
      splittedMacroExpansion.tail.map(prefix + _)).mkString("\n")
      
      document.replace(position.offset, position.length, indentedMacroExpansion)
      
      val marker = editorInput.asInstanceOf[FileEditorInput].getFile.createMarker("scala.tools.eclipse.macroMarkerId")
      marker.setAttribute(IMarker.CHAR_START, position.offset)
      marker.setAttribute(IMarker.CHAR_END, position.offset + indentedMacroExpansion.length)
      marker.setAttribute("macroExpandee", macroExpandee)

      position.delete
      })
      
      if(!annotations2Expand.isEmpty) return
      
      val annotations2Collapse = for {
        annotationNoType <- annotationModel.getAnnotationIterator
        annotation = annotationNoType.asInstanceOf[Annotation]
        if annotation.getType == "scala.tools.eclipse.macroMarkerId"
        pos = annotationModel.getPosition(annotation)
        if document.getLineOfOffset(pos.offset) == line/* && line <= document.getLineOfOffset(pos.offset + pos.length)*/ 
      } yield annotation
      
      annotations2Collapse.foreach(annotation => {
        val position = annotationModel.getPosition(annotation)
      
        val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker
      val macroExpandee = marker.getAttribute("macroExpandee").asInstanceOf[String]
      
      
      
      val macroLineStartPos = document.getLineOffset(document.getLineOfOffset(position.offset))
      val prefix = document.get(macroLineStartPos, position.offset - macroLineStartPos).takeWhile(_ == ' ')
      
      document.replace(position.offset, position.length, macroExpandee)
      
      marker.delete
      })
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
    expandMacros(iEditor)
  }

  override def doSetInput(iEditorInput: IEditorInput) {
    iEditor = Option(iEditorInput)
    super.doSetInput(iEditorInput)
    expandMacros(iEditor)
  }

  private def expandMacros(iEditor: Option[IEditorInput]) {
    import org.scalaide.util.internal.Utils._
//    for {
//      editorInput <- iEditor
//      compilationUnit <- Option(JavaPlugin.getDefault.getWorkingCopyManager.getWorkingCopy(editorInput))
//      scu <- compilationUnit.asInstanceOfOpt[ScalaCompilationUnit]
//    } {
//      scu.doWithSourceFile { (sourceFile, compiler) =>
//        import compiler.Traverser
//        import compiler.Tree
//
//        var macroExpandeePositions: List[Position] = Nil
//
//        def getMacroExpansionPos(v: Tree) = {
//          val Some(macroExpansionAttachment) = v.attachments.get[compiler.analyzer.MacroExpansionAttachment]
//          val originalTree = macroExpansionAttachment.expandee //original
//
//          val posStart = originalTree.pos.start
//          val posLength = originalTree.pos.end - posStart
//          val pos = new Position(posStart, posLength)
//          pos
//        }
//
//        new Traverser {
//          override def traverse(t: Tree): Unit = {
//            t match {
//              case v if v.attachments.get[compiler.analyzer.MacroExpansionAttachment].isDefined =>
//                val pos = getMacroExpansionPos(v)
//                macroExpandeePositions = pos :: macroExpandeePositions
//              case v =>
//                val t = v.attachments
//                myLog.log(t)
//            }
//            super.traverse(t)
//          }
//        }.traverse(compiler.loadedType(sourceFile).fold(identity, _ => compiler.EmptyTree))
//
//        for {
//          doc <- document
//          position <- macroExpandeePositions
//        } {
//          val lineNumForMacroExpansion = doc.getLineOfOffset(position.getOffset) + 1
//          val offsetForMacroExpansion = doc.getLineOffset(lineNumForMacroExpansion)
//          val macroExpansion = SuperCompiler.showCode
//          doc.replace(offsetForMacroExpansion, 0, macroExpansion)
//
//          //Mark macro expansion lines
//          val marker = editorInput.asInstanceOf[FileEditorInput].getFile.createMarker("scala.tools.eclipse.macroMarkerId")
//          val end = offsetForMacroExpansion + macroExpansion.length //TODO: remove
//          marker.setAttribute(IMarker.CHAR_START, offsetForMacroExpansion)
//          marker.setAttribute(IMarker.CHAR_END, offsetForMacroExpansion + macroExpansion.length)
//        }
//      }
//    }
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
        marker.delete

        doc.replace(pos.offset, pos.length, "")
      }
    }
  }
}