/*
 * Copyright 2005-2011 LAMP/EPFL
 */
package scala.tools.eclipse
package reconciliation

import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.{Platform, IProgressMonitor}
import org.eclipse.jdt.core.WorkingCopyOwner
import scala.tools.eclipse.javaelements.ScalaCompilationUnit

/**
 * The implementation for the org.scala-ide.sdt.core.reconciliationParticipants
 * extension points which gets the registered extensions and invokes them.
 * 
 * The runBefore and runAfter methods are themselves invoked by the 
 * ScalaSourceFile.reconcile method.
 * 
 * @author Mirko Stocker
 */
object ReconciliationParticipantsExtensionPoint {
  
  val PARTICIPANTS_ID = "org.scala-ide.sdt.core.reconciliationParticipants"
    
  lazy val extensions: List[ReconciliationParticipant] = {
    val configs = Platform.getExtensionRegistry.getConfigurationElementsFor(PARTICIPANTS_ID).toList

    configs map { e =>
      tryOrLog {
        e.createExecutableExtension("class")
      }
    } collect {
      case Some(p: ReconciliationParticipant) => p
    }
  }
  
  def runBefore(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    extensions foreach { extension =>
      tryOrLog {
        extension.beforeReconciliation(scu, monitor, workingCopyOwner)
      }
    }
  }
  
  def runAfter(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    extensions foreach { extension =>
      tryOrLog {
        extension.afterReconciliation(scu, monitor, workingCopyOwner)
      }
    }
  }
  
  private def tryOrLog[T](f : => T): Option[T] = {
    try {
      Some(f)
    } catch {
      case t => 
        ScalaPlugin.plugin.logError(t)
        None
    }
  }
}
