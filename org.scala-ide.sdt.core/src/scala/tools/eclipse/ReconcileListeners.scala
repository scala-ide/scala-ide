package scala.tools.eclipse

import scala.tools.eclipse.util.Defensive
import java.lang.ref.WeakReference
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.WorkingCopyOwner

class ReconcileListeners {
  private var _beforeListeners : List[WeakReference[(ScalaSourceFile, IProgressMonitor, WorkingCopyOwner) => Unit]] = List.empty
  private var _afterListeners  : List[WeakReference[(ScalaSourceFile, IProgressMonitor, WorkingCopyOwner) => Unit]] = List.empty
  
  def before_+(op : (ScalaSourceFile, IProgressMonitor, WorkingCopyOwner) => Unit) = {
    _beforeListeners = new WeakReference(op) :: _beforeListeners.filter(_.get ne null)
  }
  
  def after_+ (op : (ScalaSourceFile, IProgressMonitor, WorkingCopyOwner) => Unit) = {
    _afterListeners = new WeakReference(op) :: _afterListeners.filter(_.get ne null)
  }

  def triggerBeforeReconcile(sourceFile : ScalaSourceFile, monitor : IProgressMonitor, workingCopyOwner : WorkingCopyOwner) {
    val ls = _beforeListeners
    ls.foreach {l =>
      l.get match {
        case null => () // ignore
        case op => Defensive.tryOrLog(op(sourceFile, monitor, workingCopyOwner))
      }
    }
  }
  
  def triggerAfterReconcile(sourceFile : ScalaSourceFile, monitor : IProgressMonitor, workingCopyOwner : WorkingCopyOwner) {
    val ls = _afterListeners
    if (!ls.isEmpty) Defensive.askRunOutOfMain("after reconcile :" + sourceFile.file){
      ls.foreach{l =>
        l.get match {
          case null => () //ignore
          case op => Defensive.tryOrLog(op(sourceFile, monitor, workingCopyOwner))
        }
      }
    }
  }
}
