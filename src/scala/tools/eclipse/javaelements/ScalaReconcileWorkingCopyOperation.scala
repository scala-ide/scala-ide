/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.jdt.core.{ IJavaElement, IJavaModelStatus, IJavaModelStatusConstants, WorkingCopyOwner }
import org.eclipse.jdt.internal.core.{ CompilationUnit, JavaElementDelta, JavaElementDeltaBuilder, JavaModelOperation, JavaModelStatus }
import org.eclipse.jdt.internal.core.util.Messages

import scala.tools.eclipse.util.ReflectionUtils

class ScalaReconcileWorkingCopyOperation(
  workingCopy : IJavaElement,
  createAST : Boolean,
  astLevel : Int,
  forceProblemDetection : Boolean,
  workingCopyOwner : WorkingCopyOwner)
  extends JavaModelOperation(Array(workingCopy)) with ReflectionUtils {

  var ast : org.eclipse.jdt.core.dom.CompilationUnit = _
    
  protected def executeOperation() {
		if (this.progressMonitor != null) {
			if (this.progressMonitor.isCanceled()) return
			this.progressMonitor.beginTask(Messages.element_reconciling, 2)
		}
	
		val workingCopy = getWorkingCopy
		val wasConsistent = workingCopy.isConsistent
		try {
			if (!wasConsistent) {
				// create the delta builder (this remembers the current content of the cu)
				val deltaBuilder = new JavaElementDeltaBuilder(workingCopy);
				
				// update the element infos with the content of the working copy
				ast = workingCopy.makeConsistent(this.astLevel, false, 0, null, this.progressMonitor)
				deltaBuilder.buildDeltas
	
				if (progressMonitor != null) progressMonitor.worked(2);
			
        //val delta = getDelta(deltaBuilder)
        val delta = deltaBuilder.delta
				if (delta != null) {
          println("delta: "+delta)    
					addReconcileDelta(workingCopy, delta)
        }
			}
		} finally {
			if (progressMonitor != null) progressMonitor.done
		}
	}
	
	private def getDelta(deltaBuilder : JavaElementDeltaBuilder) : JavaElementDelta = {
		try {
      privileged {  
  			val deltaField = classOf[JavaElementDeltaBuilder].getDeclaredField("delta")
  			deltaField.setAccessible(true)
  			deltaField.get(deltaBuilder).asInstanceOf[JavaElementDelta]
      }
		} catch {
      case e : SecurityException =>
      case e : NoSuchFieldException =>
      case e : IllegalArgumentException =>
      case e : IllegalAccessException =>
		}
		null
	}
	
	protected def getWorkingCopy =
		getElementToProcess.asInstanceOf[CompilationUnit]

	override def isReadOnly = true

  protected override def verify : IJavaModelStatus = {
		val status = super.verify
		if (!status.isOK)
			return status

    val workingCopy = getWorkingCopy
		if (!workingCopy.isWorkingCopy)
			return new JavaModelStatus(IJavaModelStatusConstants.ELEMENT_DOES_NOT_EXIST, workingCopy) //was destroyed

    status
	}
}
