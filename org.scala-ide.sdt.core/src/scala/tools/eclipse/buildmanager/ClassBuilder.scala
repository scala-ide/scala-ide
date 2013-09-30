package scala.tools.eclipse.buildmanager

import scala.tools.eclipse.GeneralScalaJavaBuilder
import scala.tools.eclipse.ScalaJavaBuilderUtils
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.StateUtils
import scala.tools.eclipse.javaelements.JDTUtils

import org.eclipse.core.resources.IProject
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.builder.State

/** Holds common behavior for a builder that has to interop with SDT. */
trait JDTBuilderFacade {

  protected val scalaJavaBuilder = new GeneralScalaJavaBuilder

  protected def plugin = ScalaPlugin.plugin

  /** The underlying project. */
  protected def project: IProject

  protected def refresh() {
    val modelManager = JavaModelManager.getJavaModelManager
    val state = modelManager.getLastBuiltState(project, null).asInstanceOf[State]
    val newState =
      if (state ne null)
        state
      else {
        ScalaJavaBuilderUtils.initializeBuilder(scalaJavaBuilder, 0, false)
        StateUtils.newState(scalaJavaBuilder)
      }
    StateUtils.tagAsStructurallyChanged(newState)
    StateUtils.resetStructurallyChangedTypes(newState)
    modelManager.setLastBuiltState(project, newState)
    JDTUtils.refreshPackageExplorer
  }

  protected def ensureProject() {
    if (scalaJavaBuilder.getProject == null)
      scalaJavaBuilder.setProject0(project)
  }
}