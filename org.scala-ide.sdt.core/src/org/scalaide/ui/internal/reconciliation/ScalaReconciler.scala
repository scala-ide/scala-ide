package org.scalaide.ui.internal.reconciliation

import org.eclipse.jface.text.reconciler.MonoReconciler
import org.scalaide.ui.internal.actions.PartAdapter
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.jface.text.reconciler.IReconcilingStrategy
import org.eclipse.jface.text.ITextViewer
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor
import org.eclipse.ui.IPartService
import org.eclipse.ui.texteditor.ITextEditor
import scala.collection.mutable.Subscriber
import org.scalaide.core.internal.compiler.PresentationCompilerActivity
import org.scalaide.core.internal.compiler.PresentationCompilerProxy
import org.scalaide.logging.HasLogger
import org.scalaide.core.compiler.IPresentationCompilerProxy
import org.scalaide.util.Utils._
import org.eclipse.swt.events.ShellAdapter
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.events.ShellEvent
import org.scalaide.util.eclipse.SWTUtils
import org.scalaide.core.internal.compiler.Restart
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.core.IScalaPlugin

/** A Scala reconciler that forces reconciliation on various events:
 *   - the editor is shown through tab navigation
 *   - Eclipse regains focus
 *   - the presentation compiler restarted
 *
 *  The implementation follows the one form [[JavaReconciler]], except for listening to resource/element
 *  change events. I couldn't find a good use-case where it was needed.
 */
class ScalaReconciler(editor: InteractiveCompilationUnitEditor,
  strategy: IReconcilingStrategy,
  isIncremental: Boolean) extends MonoReconciler(strategy, isIncremental) with HasLogger {

  // both these fields are set during `install`, and should be non-null afterwards
  @volatile private var compilerProxy: IPresentationCompilerProxy = _
  @volatile private var activationListener: ActivationListener = _

  /** Listen for events regarding tab-switching. */
  object partListener extends PartAdapter {
    override def partActivated(part: IWorkbenchPart): Unit = {
      if (part == editor) {
        forceReconciling()
      }
    }
  }

  /** Listen for presentation-compiler restart events. */
  object compilerListener extends Subscriber[PresentationCompilerActivity, PresentationCompilerProxy] {
    override def notify(pub: PresentationCompilerProxy, event: PresentationCompilerActivity): Unit = event match {
      case Restart =>
        if (pub == compilerProxy) {
          logger.debug(s"Reconciling ${editor.getTitle} due to restart")
          forceReconciling()
        }
      case _ =>
    }
  }

  /** Listen for events regarding Eclipse getting focus. */
  class ActivationListener(control: Control) extends ShellAdapter {
    def restartPc() = {
      EditorUtils.resourceOfActiveEditor.foreach { r ⇒
        logger.debug(s"Restarting presentation compiler for ${r.getProject.getName} because Eclipse gained focus.")
        val scalaProject = IScalaPlugin().asScalaProject(r.getProject)
        scalaProject foreach (_.presentationCompiler.askRestart())
      }
    }

    override def shellActivated(event: ShellEvent): Unit = {
      if (!control.isDisposed() && control.isVisible()) {
        restartPc()
        forceReconciling()
      }
    }
  }

  override def install(textViewer: ITextViewer): Unit = {
    super.install(textViewer)

    compilerProxy = editor.getInteractiveCompilationUnit().scalaProject.presentationCompiler
    compilerProxy match {
      case proxy: PresentationCompilerProxy =>
        proxy.subscribe(compilerListener)
      case p =>
        logger.info(s"Could not register activity listener on compiler. $p is not a PresentationCompilerProxy")
    }

    activationListener = new ActivationListener(textViewer.getTextWidget)
    partService(editor).map(_.addPartListener(partListener))
    Option(SWTUtils.getShell).map(_.addShellListener(activationListener))
  }

  override def uninstall(): Unit = {
    super.uninstall()

    compilerProxy.asInstanceOfOpt[PresentationCompilerProxy].map(_.removeSubscription(compilerListener))
    partService(editor).map(_.removePartListener(partListener))
    Option(SWTUtils.getShell).map(_.removeShellListener(activationListener))
  }

  private def partService(editor: ITextEditor): Option[IPartService] =
    for {
      site <- Option(editor.getSite)
      window <- Option(site.getWorkbenchWindow)
      service <- Option(window.getPartService)
    } yield service
}
