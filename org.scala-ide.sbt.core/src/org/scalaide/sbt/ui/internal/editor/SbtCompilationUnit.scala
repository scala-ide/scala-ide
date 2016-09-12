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
import org.scalaide.sbt.core.SbtBuild
import org.scalaide.sbt.core.SbtRemotePlugin
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.scalaide.logging.HasLogger

class SbtSourceFileProvider extends SourceFileProvider with HasLogger {
  override def createFrom(path: IPath): Option[InteractiveCompilationUnit] = {
    val root = ResourcesPlugin.getWorkspace().getRoot()
    val file = Option(root.getFile(path))

    file map SbtCompilationUnit.fromFile
  }
}

object SbtCompilationUnit extends HasLogger {
  private var units = Map[String, SbtCompilationUnit]()

  def fromEditor(scriptEditor: SbtEditor): SbtCompilationUnit = {
    val input = scriptEditor.getEditorInput
    if (input == null)
      throw new NullPointerException(s"No editor input for editor $scriptEditor. Hint: Maybe the editor isn't yet fully initialized?")
    else {
      val file = getFile(input)
      val path = file.getFullPath.toOSString()
      units.get(path) match {
        case None ⇒
          val doc = scriptEditor.getDocumentProvider().getDocument(input)
          val unit = new SbtCompilationUnit(file, Some(doc))
          units += path → unit
          logger.debug(s"Successfully created a new sbt compilation unit for file `$path`.")
          unit
        case Some(unit) ⇒
          unit
      }
    }
  }

  def fromFile(file: IFile): SbtCompilationUnit = {
    val path = file.getFullPath.toOSString()
    units.get(path) match {
      case None ⇒
        val unit = new SbtCompilationUnit(file)
        units += path → unit
        logger.debug(s"Successfully created a new sbt compilation unit for file `$path`.")
        unit
      case Some(unit) ⇒
        unit
    }
  }

  private def getFile(editorInput: IEditorInput): IFile =
    editorInput match {
      case fileEditorInput: FileEditorInput if fileEditorInput.getName.endsWith("sbt") =>
        fileEditorInput.getFile
      case _ =>
        throw new IllegalArgumentException(s"Editor input of file `${editorInput.getName}` is not a sbt file.")
    }
}

class SbtSourceInfo(file: AbstractFile, override val originalSource: Array[Char], imports: Seq[String]) extends ISourceMap {
  private val prefix = s"""
    |${imports.mkString("\n")}
    |object $$container {
    |  def $$meth {
    |""".stripMargin.trim
  private val prefixLen = prefix.count(_ == '\n')

  override val scalaSource = s"""
    |$prefix
    |${originalSource.mkString("")}
    |}}""".stripMargin.trim.toCharArray()

  override lazy val sourceFile = new BatchSourceFile(file, scalaSource)

  override val scalaPos = ScalaPosition
  override val originalPos = OriginalPosition

  override def scalaLine(line: Int): Int = line + prefixLen
  override def originalLine(line: Int): Int = math.max(1, line - prefixLen)

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
    document: Option[IDocument] = None)
      extends InteractiveCompilationUnit with HasLogger {

  @volatile private var cachedSourceMap: ISourceMap = _

  private lazy val pc = {
    logger.debug(s"About to create presentation compiler for sbt project `$scalaProject`.")
    val build = Await.result(SbtBuild.buildFor(scalaProject.underlying.getLocation.toFile)(SbtRemotePlugin.system), Duration.Inf)
    val c = new SbtPresentationCompiler(scalaProject, build)
    logger.debug(s"Presentation compiler for sbt project `$scalaProject` successfully created.")
    c
  }

  /** Return the source info for the given contents. */
  override def sourceMap(contents: Array[Char]): ISourceMap = {
    cachedSourceMap = new SbtSourceInfo(file, contents, pc.sbtFileImports)
    cachedSourceMap
  }

  /** Return the most recent available source map for the current contents. */
  override def lastSourceMap(): ISourceMap = {
    if (cachedSourceMap == null)
      sourceMap(getContents())
    else
      cachedSourceMap
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

  override def presentationCompiler = pc.compiler

  /** Does this unit exist in the workspace? */
  override def exists(): Boolean = workspaceFile.exists()
}
