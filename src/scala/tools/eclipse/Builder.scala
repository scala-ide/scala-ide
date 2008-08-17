/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import java.util.{ Map => JMap }

import org.eclipse.core.resources.{ IncrementalProjectBuilder, IProject }
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.builder.{ JavaBuilder, State }

import lampion.util.ReflectionUtils

class Builder extends lampion.eclipse.Builder {
  def plugin = ScalaPlugin.plugin

  private val scalaJavaBuilder = new ScalaJavaBuilder
  
  override def clean(monitor : IProgressMonitor) {
    super.clean(monitor)
    ensureProject
    scalaJavaBuilder.clean(monitor)
  }
  
  override def build(kind : Int, ignored : JMap[_, _], monitor : IProgressMonitor) : Array[IProject] = {
    val depends = super.build(kind, ignored, monitor)
    ensureProject
    val javaDepends = scalaJavaBuilder.build(IncrementalProjectBuilder.FULL_BUILD, ignored, monitor)
    val modelManager = JavaModelManager.getJavaModelManager
    val state = modelManager.getLastBuiltState(getProject, null).asInstanceOf[State]
    val newState = if (state == null) StateUtils.newState(scalaJavaBuilder) else state
    StateUtils.tagAsStructurallyChanged(state)
    StateUtils.resetStructurallyChangedTypes(state)
    modelManager.setLastBuiltState(getProject, newState)
    (Set.empty ++ depends ++ javaDepends).toArray
  }
  
  def ensureProject = {
    if (scalaJavaBuilder.getProject == null)
      scalaJavaBuilder.setProject0(getProject)
  }
}

class ScalaJavaBuilder extends JavaBuilder {
  import ContentTypeUtils._
  import ScalaJavaBuilder._  
  
  def setProject0(project : IProject) = setProject(this, project)

  override def clean(monitor : IProgressMonitor) {
    withoutJavaLikeExtension { super.clean(monitor) }
  }
  
  override def build(kind : Int, ignored : JMap[_, _], monitor : IProgressMonitor) : Array[IProject] = {
    withoutJavaLikeExtension { super.build(kind, ignored, monitor) }
  }
}

object ScalaJavaBuilder extends ReflectionUtils {
  private val ibClazz = Class.forName("org.eclipse.core.internal.events.InternalBuilder")
  private val setProjectMethod = getMethod(ibClazz, "setProject", classOf[IProject])
  
  def setProject(builder : ScalaJavaBuilder, project : IProject) = setProjectMethod.invoke(builder, project)  
}

object StateUtils extends ReflectionUtils {
  private val stateClazz = Class.forName("org.eclipse.jdt.internal.core.builder.State")
  private val stateCtor = getConstructor(classOf[JavaBuilder])
  private val tagAsStructurallyChangedMethod = getMethod(stateClazz, "tagAsStructurallyChanged")
  private val structurallyChangedTypesField = getField(stateClazz, "structurallyChangedTypes")
  
  def newState(b : JavaBuilder) = stateCtor.newInstance(b)
  
  def tagAsStructurallyChanged(s : State) = tagAsStructurallyChangedMethod.invoke(s)
  
  def resetStructurallyChangedTypes(s : State) = structurallyChangedTypesField.set(s, null)
}
