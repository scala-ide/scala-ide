/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import java.{ lang => jl, util => ju }

import org.eclipse.core.resources.{ IncrementalProjectBuilder, IProject }
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.builder.{ JavaBuilder, State }

import scala.tools.eclipse.contribution.weaving.jdt.builderoptions.ScalaJavaBuilder
import lampion.util.ReflectionUtils
import scala.tools.eclipse.javaelements.JDTUtils

class Builder extends lampion.eclipse.Builder {
  def plugin = ScalaPlugin.plugin

  private val scalaJavaBuilder = new ScalaJavaBuilder
  
  override def clean(monitor : IProgressMonitor) {
    super.clean(monitor)
    ensureProject
    scalaJavaBuilder.clean(monitor)
    JDTUtils.refreshPackageExplorer
  }
  
  override def build(kind : Int, ignored : ju.Map[_, _], monitor : IProgressMonitor) : Array[IProject] = {
    val depends = super.build(kind, ignored, monitor)
    if (plugin.projectSafe(getProject).get.lastBuildHadBuildErrors)
      depends
    else {
      ensureProject
      val javaDepends = scalaJavaBuilder.build(kind, ignored, monitor)
      val modelManager = JavaModelManager.getJavaModelManager
      val state = modelManager.getLastBuiltState(getProject, null).asInstanceOf[State]
      val newState = if (state ne null) state
        else {
          ScalaJavaBuilderUtils.initializeBuilder(scalaJavaBuilder, 0, false)
          StateUtils.newState(scalaJavaBuilder)
        }
      StateUtils.tagAsStructurallyChanged(newState)
      StateUtils.resetStructurallyChangedTypes(newState)
      modelManager.setLastBuiltState(getProject, newState)
      JDTUtils.refreshPackageExplorer
      (Set.empty ++ depends ++ javaDepends).toArray
    }
  }
  
  def ensureProject = {
    if (scalaJavaBuilder.getProject == null)
      scalaJavaBuilder.setProject0(getProject)
  }
}

object ScalaJavaBuilderUtils extends ReflectionUtils {
  private val ibClazz = Class.forName("org.eclipse.core.internal.events.InternalBuilder")
  private val setProjectMethod = getMethod(ibClazz, "setProject", classOf[IProject])
  private val jbClazz = Class.forName("org.eclipse.jdt.internal.core.builder.JavaBuilder")
  private val initializeBuilderMethod = getMethod(jbClazz, "initializeBuilder", classOf[Int], classOf[Boolean])
  
  def setProject(builder : ScalaJavaBuilder, project : IProject) = setProjectMethod.invoke(builder, project)
  def initializeBuilder(builder : ScalaJavaBuilder, kind : Int, forBuild : Boolean) = initializeBuilderMethod.invoke(builder, int2Integer(kind), boolean2Boolean(forBuild))
}

object StateUtils extends ReflectionUtils {
  private val stateClazz = Class.forName("org.eclipse.jdt.internal.core.builder.State").asInstanceOf[Class[State]]
  private val stateCtor = getConstructor(stateClazz, classOf[JavaBuilder])
  private val tagAsStructurallyChangedMethod = getMethod(stateClazz, "tagAsStructurallyChanged")
  private val structurallyChangedTypesField = getField(stateClazz, "structurallyChangedTypes")
  
  def newState(b : JavaBuilder) = stateCtor.newInstance(b)
  
  def tagAsStructurallyChanged(s : State) = tagAsStructurallyChangedMethod.invoke(s)
  
  def resetStructurallyChangedTypes(s : State) = structurallyChangedTypesField.set(s, null)
}
