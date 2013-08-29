/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ HashMap => JHashMap }
import java.util.{ Map => JMap }
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.IBuffer
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.core.util.HandleFactory
import org.eclipse.jdt.internal.core.BufferManager
import org.eclipse.jdt.internal.core.{ CompilationUnit => JDTCompilationUnit }
import org.eclipse.jdt.internal.core.OpenableElementInfo
import org.eclipse.jdt.internal.core.PackageFragment
import org.eclipse.jdt.internal.corext.util.JavaModelUtil
import org.eclipse.swt.widgets.Display
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.io.VirtualFile
import scala.tools.eclipse.contribution.weaving.jdt.IScalaSourceFile
import scala.tools.eclipse.util.EclipseFile
import org.eclipse.jdt.core.compiler.CharOperation
import scala.tools.nsc.interactive.Response
import scala.tools.eclipse.reconciliation.ReconciliationParticipantsExtensionPoint
import org.eclipse.jdt.core.JavaModelException
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.sourcefileprovider.SourceFileProvider
import org.eclipse.core.runtime.IPath


class ScalaSourceFileProvider extends SourceFileProvider {
  override def createFrom(path: IPath): Option[InteractiveCompilationUnit] =
    ScalaSourceFile.createFromPath(path.toString)
}

object ScalaSourceFile {

  /** Considering [[org.eclipse.jdt.internal.core.util.HandleFactory]] isn't thread-safe, and because
   *  `ScalaSourceFile#createFromPath` can be called concurrently from different threads, using a
   *  `ThreadLocal` ensures that a `HandleFactory` instance is never shared across threads.
   */
  private val handleFactory: ThreadLocal[HandleFactory] = new ThreadLocal[HandleFactory] {
    override protected def initialValue(): HandleFactory = new HandleFactory
  }

  /** Creates a Scala source file handle if the given resource path points to a scala source.
   *  The resource path is a path to a Scala source file in the workbench (e.g. /Proj/a/b/c/Foo.scala).
   *
   *  @note This assumes that the resource path is the toString() of an `IPath`.
   *
   *  @param path Is a path to a Scala source file in the workbench.
   */
  def createFromPath(path: String) : Option[ScalaSourceFile] = {
    if (!path.endsWith(".scala"))
      None
    else {
      // Always `null` because `handleFactory.createOpenable` is only called to open source files, and the `scope` is not needed for this.
      val unusedScope = null
      val source = handleFactory.get().createOpenable(path, unusedScope)
      source match {
        case ssf : ScalaSourceFile => Some(ssf)
        case _ => None
      }
    }
  }
}

class ScalaSourceFile(fragment : PackageFragment, elementName: String, workingCopyOwner : WorkingCopyOwner)
  extends JDTCompilationUnit(fragment, elementName, workingCopyOwner) with ScalaCompilationUnit with IScalaSourceFile {

  override def getMainTypeName : Array[Char] =
    getElementName.substring(0, getElementName.length - ".scala".length).toCharArray()

  /** Schedule this source file for reconciliation. Add the file to
   *  the loaded files managed by the presentation compiler.
   */
  override def scheduleReconcile(): Response[Unit] = {
    // askReload first
    val res = scalaProject.withSourceFile(this) { (sf, compiler) =>
      compiler.askReload(this, getContents)
    } ()

    this.reconcile(
        ICompilationUnit.NO_AST,
        false /* don't force problem detection */,
        null /* use primary owner */,
        null /* no progress monitor */);

    res
  }

  /* getProblems should be reserved for a Java context, @see getProblems */
  override def reconcile(newContents: String): List[IProblem] =
    currentProblems.toList

  override def reconcile(
      astLevel : Int,
      reconcileFlags : Int,
      workingCopyOwner : WorkingCopyOwner,
      monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = {
    ReconciliationParticipantsExtensionPoint.runBefore(this, monitor, workingCopyOwner)
    val result = super.reconcile(ICompilationUnit.NO_AST, reconcileFlags, workingCopyOwner, monitor)
    ReconciliationParticipantsExtensionPoint.runAfter(this, monitor, workingCopyOwner)
    result
  }

  override def makeConsistent(
    astLevel : Int,
    resolveBindings : Boolean,
    reconcileFlags : Int,
    problems : JHashMap[_,_],
    monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = {
    val info = createElementInfo.asInstanceOf[OpenableElementInfo]
    openWhenClosed(info, true, monitor)
    null
  }

  override def codeSelect(offset : Int, length : Int, workingCopyOwner : WorkingCopyOwner) : Array[IJavaElement] =
    codeSelect(this, offset, length, workingCopyOwner)

  override lazy val file : AbstractFile = {
    val res = try { getCorrespondingResource } catch { case _: JavaModelException => null }
    res match {
      case f : IFile => new EclipseFile(f)
      case _ => new VirtualFile(getElementName, getPath.toString)
    }
  }

  /** Implementing the weaving interface requires to return `null` for an empty array. */
  override def getProblems: Array[IProblem] = {
    val probs = currentProblems()
    if (probs.isEmpty) null else probs.toArray
  }

  override def currentProblems(): List[IProblem] = withSourceFile { (src, compiler) =>
    compiler.problemsOf(this)
  } (List())

  override def getType(name : String) : IType = new LazyToplevelClass(this, name)

  override def getContents() : Array[Char] = {
    // in the following case, super#getContents() logs an exception for no good reason
    if (getBufferManager().getBuffer(this) == null && getResource().getLocation() == null && getResource().getLocationURI() == null) {
      return CharOperation.NO_CHAR
    }
    return super.getContents()
  }

  /** Makes sure {{{this}}} source is not in the ignore buffer of the compiler and ask the compiler to reload it. */
  final def forceReload(): Unit = scalaProject.doWithPresentationCompiler { compiler =>
    compiler.askToDoFirst(this)
    reload()
  }

  /** Ask the compiler to reload {{{this}}} source. */
  final def reload(): Unit = scalaProject.doWithPresentationCompiler { _.askReload(this, getContents) }

  /** Ask the compiler to discard {{{this}}} source. */
  final def discard(): Unit = scalaProject.doWithPresentationCompiler { compiler =>
    compiler.discardSourceFile(this)
  }
}
