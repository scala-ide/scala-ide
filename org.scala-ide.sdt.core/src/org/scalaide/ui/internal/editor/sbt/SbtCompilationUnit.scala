package org.scalaide.ui.internal.editor.sbt

import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.AbstractFile

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.jface.text.IDocument
import org.eclipse.ui.IEditorInput
import org.eclipse.ui.part.FileEditorInput
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.compiler.IPositionInformation
import org.scalaide.core.compiler.ISourceMap
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.extensions.SourceFileProvider
import org.scalaide.core.resources.EclipseResource

class SbtSourceFileProvider extends SourceFileProvider {
  override def createFrom(path: IPath): Option[InteractiveCompilationUnit] = {
    val root = ResourcesPlugin.getWorkspace().getRoot()
    val file = Option(root.getFile(path))

    file map (new SbtCompilationUnit(_))
  }
}

object SbtCompilationUnit {
  def fromEditor(scriptEditor: SbtEditor): SbtCompilationUnit = {
    val input = scriptEditor.getEditorInput
    if (input == null) throw new NullPointerException(s"No editor input for editor $scriptEditor. Hint: Maybe the editor isn't yet fully initialized?")
    else {
      val doc = scriptEditor.getDocumentProvider().getDocument(input)
      fromEditorInput(input, doc)
    }
  }

  private def getFile(editorInput: IEditorInput): IFile =
    editorInput match {
      case fileEditorInput: FileEditorInput if fileEditorInput.getName.endsWith("sbt") =>
        fileEditorInput.getFile
      case _ =>
        throw new IllegalArgumentException(s"Editor input of file `${editorInput.getName}` is not a sbt file.")
    }

  private def fromEditorInput(editorInput: IEditorInput, doc: IDocument): SbtCompilationUnit =
    new SbtCompilationUnit(getFile(editorInput), Some(doc))
}

class SbtSourceInfo(file: AbstractFile, override val originalSource: Array[Char]) extends ISourceMap {
  def baseImports = "import sbt._, Keys._, dsl._"

  val prefix = s"""|$baseImports
                   |object $$container {
                   |  def $$meth {
                   |""".stripMargin

  override val scalaSource =
    s"""|$prefix
        |${originalSource.mkString("")} }}""".stripMargin.toCharArray()

  override lazy val sourceFile = new BatchSourceFile(file, scalaSource)

  override val scalaPos = ScalaPosition
  override val originalPos = OriginalPosition

  override def scalaLine(line: Int): Int = line + 3
  override def originalLine(line: Int): Int = math.max(1, line - 3)

  object ScalaPosition extends IPositionInformation {
    /** Map the given offset to the target offset. */
    override def apply(offset: Int): Int = {
      offset + prefix.length() + "\n".length
    }

    /** Return the line number corresponding to this offset. */
    override def offsetToLine(offset: Int): Int = sourceFile.offsetToLine(offset)

    /** Return the offset corresponding to this line number. */
    override def lineToOffset(line: Int): Int = sourceFile.lineToOffset(line)
  }

  object OriginalPosition extends IPositionInformation {
    /** Map the given offset to the target offset. */
    override def apply(offset: Int): Int = {
      offset - prefix.length() - "\n".length
    }

    /** Return the line number corresponding to this offset. */
    override def offsetToLine(offset: Int): Int = sourceFile.offsetToLine(offset)

    /** Return the offset corresponding to this line number. */
    override def lineToOffset(line: Int): Int = sourceFile.lineToOffset(line)
  }
}

case class SbtCompilationUnit(
    override val workspaceFile: IFile,
    document: Option[IDocument] = None) extends InteractiveCompilationUnit {

  /** Return the source info for the given contents. */
  override def sourceMap(contents: Array[Char]): ISourceMap = {
    new SbtSourceInfo(file, contents)
  }

  @volatile private var lastInfo: ISourceMap = _

  /** Return the most recent available source map for the current contents. */
  override def lastSourceMap(): ISourceMap = {
    if (lastInfo eq null)
      lastInfo = sourceMap(getContents())
    lastInfo
  }

  /** Return the current contents of this compilation unit. This is the 'original' contents, that may be
   *  translated to a Scala source using `sourceMap`.
   *
   *  If we take Play templates as an example, this method would return HTML interspersed with Scala snippets. If
   *  one wanted the translated Scala source, he'd have to call `lastSourceMap().scalaSource`
   */
  override def getContents(): Array[Char] =
    document.map(_.get.toCharArray).getOrElse(file.toCharArray)

  /** The `AbstractFile` that the Scala compiler uses to read this compilation unit. It should not change through the lifetime of this unit. */
  override def file: AbstractFile = EclipseResource(workspaceFile)

  override lazy val scalaProject = IScalaPlugin().asScalaProject(workspaceFile.getProject).get

  override def presentationCompiler = SbtPresentationCompiler.compiler

  /** Does this unit exist in the workspace? */
  override def exists(): Boolean = workspaceFile.exists()
}
