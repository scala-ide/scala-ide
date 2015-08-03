/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.hcr

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.mutable.Publisher
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.debug.core.DebugEvent
import org.eclipse.debug.core.DebugPlugin
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.preferences.HotCodeReplacePreferences
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.EclipseUtils
import com.sun.jdi.ReferenceType
import ScalaHotCodeReplaceManager.HCRFailed
import ScalaHotCodeReplaceManager.HCRNotSupported
import ScalaHotCodeReplaceManager.HCRResult
import ScalaHotCodeReplaceManager.HCRSucceeded

private[internal] object ScalaHotCodeReplaceManager {

  private[hcr] sealed trait HCRResult
  private[hcr] case class HCRSucceeded(launchName: String) extends HCRResult
  private[hcr] case class HCRNotSupported(launchName: String) extends HCRResult
  private[hcr] case class HCRFailed(launchName: String) extends HCRResult

  def create(hcrExecutor: HotCodeReplaceExecutor): Option[ScalaHotCodeReplaceManager] = {
    if (HotCodeReplacePreferences.hcrEnabled)
      Some(new ScalaHotCodeReplaceManager(hcrExecutor))
    else
      None
  }
}

/**
 * Monitors changed resources and notifies debug target's companion actor, when there are some changed class files
 * which could be replaced in VM.
 *
 * Note: It seems that currently deltas for changed resources work differently when compiling Java and Scala files.
 * When there are errors in one of Scala source files in a project, we don't get in an event deltas related to class
 * files for all classes in this project. We get all such deltas later - when errors are corrected and all sources
 * are correctly compiled.
 *
 * When we have errors only in Java files, we get all deltas - both for Scala and Java files, even for these Java
 * files which contain errors. Then we look for error markers in Java sources to decide, whether related classes
 * should be replaced.
 *
 * If someday it would turn out there are some deltas related also to Scala files containing errors, our logic will
 * handle this case correctly. It shouldn't make the difference whether it's .java or .scala source file - we just
 * have some compilation unit containing (or not) `JAVA_MODEL_PROBLEM_MARKER`.
 *
 * To illustrate how it works in practice with mixed compilation order:
 *
 * Let's assume there are projects A, B and C. A and B are independent. C depends on A and B.
 * Each of them contains Scala and Java source files.
 * A = { A1.scala, A2.scala, ..., Ai.scala, JA1.java, JA2.java, ..., JAj.java }
 * B = { B1.scala, B2.scala, ..., Bk.scala, JB1.java, JB2.java, ..., JBl.java }
 * C = { C1.scala, C2.scala, ..., Cm.scala, JC1.java, JC2.java, ..., JCn.java, MainApp.scala }
 * where i, j, k, l, m, n >= 1
 *
 * We run MainApp which uses all files. After each build we'll drop frames and go to breakpoint once again.
 *
 * Let's modify all files (only some internals of methods; modifying signatures is disallowed) and build all.
 * A1.scala contains an error.
 * Then:
 * a) When `stopBuildOnError` is turned on:
 *  - all classes from B are redefined,
 *  - all classes from A and C are not.
 * b) When `stopBuildOnError` is turned off:
 *  - all classes from B and C are redefined,
 *  - all classes from A are not.
 * After correcting an error in A1.scala and another build, all classes are redefined.
 *
 * Let's modify all files once again and build all. This time JA1.java contains an error.
 * Then it doesn't matter whether `stopBuildOnError` is turned on or not, and:
 * - all classes from B and C are redefined,
 * - all classes except JA1.class (and maybe some its nested classes) from A are redefined.
 *
 * After correcting an error in JA1.java and another build, this class (with aforementioned its possible nested
 * classes) is also redefined.
 */
class ScalaHotCodeReplaceManager private (hcrExecutor: HotCodeReplaceExecutor) extends IResourceChangeListener {

  private[internal] def init(): Unit = {
    ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_BUILD)
  }

  private[internal] def dispose(): Unit = {
    ResourcesPlugin.getWorkspace().removeResourceChangeListener(this)
  }

  override def resourceChanged(event: IResourceChangeEvent): Unit = {
    val changedClasses = getChangedClasses(event)
    if (changedClasses.nonEmpty)
      hcrExecutor.replaceClassesIfVMAllows(changedClasses)
  }

  private def getChangedClasses(event: IResourceChangeEvent): List[ClassFileResource] = {
    val delta = event.getDelta()
    if (delta == null || event.getType() != IResourceChangeEvent.POST_BUILD)
      Nil
    else
      EclipseUtils.withSafeRunner {
        val affectedProjects = delta.getAffectedChildren.map(_.getResource.getName).mkString(", ")
        s"Error occurred while looking for changed classes in projects: $affectedProjects"
      } {
        val visitor = new ChangedClassFilesVisitor
        delta.accept(visitor)
        visitor.getChangedClasses
      }.getOrElse(Nil)
  }
}

private[internal] trait HotCodeReplaceExecutor extends Publisher[HCRResult] with HasLogger {
  import scala.collection.JavaConverters._

  protected val debugTarget: ScalaDebugTarget

  /**
   * If VM supports HCR, it replaces classes already loaded to VM using new class file versions.
   */
  def replaceClassesIfVMAllows(changedClasses: Seq[ClassFileResource]): Unit = {
    val typesToReplace = changedClasses filter isLoadedToVM
    if (typesToReplace.nonEmpty) {
      val launchName = currentLaunchName

      // we check this at the end to don't disturb user, if it's not really needed
      if (supportsHcr) doHotCodeReplace(launchName, typesToReplace)
      else runAsynchronously {
        () => publish(HCRNotSupported(launchName))
      }
    }
  }

  private def doHotCodeReplace(launchName: String, typesToReplace: Seq[ClassFileResource]): Unit = {
    try {
      logger.debug(s"Performing Hot Code Replace for debug configuration '$launchName'")
      debugTarget.isPerformingHotCodeReplace.getAndSet(true)
      // FIXME We need the automatic semantic dropping frames BEFORE HCR to prevent VM crashes.
      // We should drop possibly affected frames in this place (remember to wait for the end of such
      // an operation) and run Step Into after HCR. If no frames will be dropped, we'll just refresh
      // current stack frames.
      redefineTypes(typesToReplace)
      updateScalaDebugEnv(typesToReplace)
      logger.debug(s"Performing Hot Code Replace for debug configuration '$launchName' succeeded")
      runAsynchronously {
        () => publish(HCRSucceeded(launchName))
      }
    } catch {
      case e: Exception =>
        eclipseLog.error(s"Error occurred while redefining classes in VM for debug configuration '$launchName'.", e)
        runAsynchronously {
          () => publish(HCRFailed(launchName))
        }
    } finally {
      debugTarget.isPerformingHotCodeReplace.getAndSet(false)
      debugTarget.fireChangeEvent(DebugEvent.CONTENT)
    }
  }

  private def supportsHcr = debugTarget.virtualMachine.canRedefineClasses()

  private def redefineTypes(changedClasses: Seq[ClassFileResource]): Unit = {
    val bytesForClasses = getTypesToBytes(changedClasses)
    debugTarget.virtualMachine.redefineClasses(bytesForClasses.asJava)
  }

  private def updateScalaDebugEnv(changedClasses: Seq[ClassFileResource]): Unit = {
    debugTarget.updateStackFramesAfterHcr(HotCodeReplacePreferences.dropObsoleteFramesAutomatically)
    debugTarget.breakpointManager.reenableBreakpointsInClasses(changedClasses.map(_.fullyQualifiedName))
  }

  // it has no sense to try to replace classes which are not loaded to VM
  private def isLoadedToVM(changedClass: ClassFileResource) = !classesByName(changedClass.fullyQualifiedName).isEmpty()

  private def getTypesToBytes(changedClasses: Seq[ClassFileResource]): Map[ReferenceType, Array[Byte]] =
    changedClasses.flatMap { changedClass =>
      val classes: Seq[ReferenceType] = classesByName(changedClass.fullyQualifiedName).asScala
      val bytes = org.eclipse.jdt.internal.core.util.Util.getResourceContentsAsByteArray(changedClass.classFile)
      classes.map(_ -> bytes)
    }(collection.breakOut)

  private def classesByName(name: String) = debugTarget.virtualMachine.classesByName(name)

  private def runAsynchronously(fun: () => Unit): Unit = {
    val runnable = new Runnable() {
      override def run(): Unit = fun()
    }
    DebugPlugin.getDefault().asyncExec(runnable)
  }

  /**
   * Returns an actual name of a launch configuration used to run this debug session.
   * If user changed the name or removed the launch config in the meantime, this will be taken into account.
   */
  private def currentLaunchName: String = {
    val config = Option(debugTarget.getLaunch().getLaunchConfiguration())
    val launchName = config.map(_.getName)
    launchName.getOrElse("<unknown>")
  }
}
