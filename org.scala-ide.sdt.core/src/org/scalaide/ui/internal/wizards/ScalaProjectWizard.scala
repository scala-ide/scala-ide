package org.scalaide.ui.internal.wizards

import scala.collection.mutable.ArrayBuffer
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.OperationCanceledException
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.SubProgressMonitor
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.ui.wizards.JavaProjectWizard
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathsBlock
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.ui.wizards.JavaCapabilityConfigurationPage
import org.eclipse.jdt.ui.wizards.NewJavaProjectWizardPageOne
import org.eclipse.jdt.ui.wizards.NewJavaProjectWizardPageTwo
import org.scalaide.core.IScalaPlugin
import org.scalaide.ui.ScalaImages
import org.scalaide.util.internal.ReflectionUtils
import org.scalaide.core.SdtConstants

class ScalaProjectWizard extends {
    val pageOne = new NewScalaProjectWizardPageOne
    val pageTwo = new NewScalaProjectWizardPageTwo(pageOne)
  }
  with JavaProjectWizard(pageOne, pageTwo) {
  setWindowTitle("New Scala Project")
  setDefaultPageImageDescriptor(ScalaImages.SCALA_PROJECT_WIZARD);

  pageOne.setTitle("Create a Scala project")
  pageOne.setDescription("Create a Scala project in the workspace or in an external location.")
  pageTwo.setTitle("Scala Settings")
  pageTwo.setDescription("Define the Scala build settings.")
}

class NewScalaProjectWizardPageOne extends NewJavaProjectWizardPageOne {
  override def getDefaultClasspathEntries() : Array[IClasspathEntry] =
    (JavaCore.newContainerEntry(Path.fromPortableString(SdtConstants.ScalaLibContId)) +=: ArrayBuffer(super.getDefaultClasspathEntries : _*)).toArray
}

class NewScalaProjectWizardPageTwo(pageOne : NewJavaProjectWizardPageOne) extends NewJavaProjectWizardPageTwo(pageOne) {
  import NewScalaProjectWizardPageTwoUtils._
  override def configureJavaProject(newProjectCompliance : String, monitor0 : IProgressMonitor) {
    val monitor = if (monitor0 != null) monitor0 else new NullProgressMonitor
    val nSteps = 6
    monitor.beginTask(NewWizardMessages.JavaCapabilityConfigurationPage_op_desc_java, nSteps)

    try {
      addScalaNatures(new SubProgressMonitor(monitor, 1))
      getBuildPathsBlock(this).configureJavaProject(newProjectCompliance, new SubProgressMonitor(monitor, 5))
    } catch {
      case ex : OperationCanceledException => throw new InterruptedException
    } finally {
      monitor.done
    }
  }

  def addScalaNatures(monitor : IProgressMonitor) {
    if (monitor != null && monitor.isCanceled)
      throw new OperationCanceledException
    val project = getJavaProject.getProject
    if (!project.hasNature(JavaCore.NATURE_ID)) {
      val desc = project.getDescription
      val natures = ArrayBuffer(desc.getNatureIds : _*)
      natures += SdtConstants.NatureId
      natures += JavaCore.NATURE_ID
      desc.setNatureIds(natures.toArray)
      project.setDescription(desc, monitor)
    } else {
      if (monitor != null) {
        monitor.worked(1)
      }
    }
  }
}

object NewScalaProjectWizardPageTwoUtils extends ReflectionUtils {
  val jccpClazz = classOf[JavaCapabilityConfigurationPage]
  val getBuildPathsBlockMethod = getDeclaredMethod(jccpClazz, "getBuildPathsBlock")

  def getBuildPathsBlock(jccp : JavaCapabilityConfigurationPage) = getBuildPathsBlockMethod.invoke(jccp).asInstanceOf[BuildPathsBlock]
}
