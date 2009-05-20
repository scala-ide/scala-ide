/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import java.{ lang => jl, util => ju }

import org.eclipse.core.resources.{ IFile, IncrementalProjectBuilder, IProject, IResource, IResourceDelta, IResourceDeltaVisitor, IResourceVisitor }
import org.eclipse.core.runtime.{ IProgressMonitor, IPath }
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.builder.{ JavaBuilder, State }

import scala.tools.eclipse.contribution.weaving.jdt.builderoptions.ScalaJavaBuilder
import scala.tools.eclipse.javaelements.JDTUtils
import scala.tools.eclipse.util.ReflectionUtils
import scala.collection.mutable.HashSet

class Builder extends IncrementalProjectBuilder {
  def plugin = ScalaPlugin.plugin

  private val scalaJavaBuilder = new ScalaJavaBuilder
  
  override def clean(monitor : IProgressMonitor) {
    super.clean(monitor)
    val project = plugin.projectSafe(getProject).get
    project.clean(monitor)
    
    ensureProject
    scalaJavaBuilder.clean(monitor)
    JDTUtils.refreshPackageExplorer
  }
  
  override def build(kind : Int, ignored : ju.Map[_, _], monitor : IProgressMonitor) : Array[IProject] = {
    import IncrementalProjectBuilder._

    val project = plugin.projectSafe(getProject).get
    val buildSet = new HashSet[ScalaFile]
    kind match {
      case INCREMENTAL_BUILD | AUTO_BUILD =>
        getDelta(project.underlying).accept(new IResourceDeltaVisitor {
          def visit(delta : IResourceDelta) = {
            delta.getResource match {
              case file : IFile
                if (delta.getKind != IResourceDelta.REMOVED) &&
                   (project.sourceFolders.exists(_.getLocation.isPrefixOf(file.getLocation))) &&
                   plugin.isBuildable(file) =>
                buildSet += new ScalaFile(file)
              case _ =>
            }
            true
          }
        })
      case CLEAN_BUILD | FULL_BUILD => 
        val sourceFolders = project.sourceFolders
        sourceFolders.foreach(_.accept(new IResourceVisitor {
          def visit(resource : IResource) = {
            resource match {
              case file : IFile if plugin.isBuildable(file) => buildSet += new ScalaFile(file)
              case _ =>
            }
            true
          }
        }))
    }
    
    // everything that needs to be recompiled is in toBuild now
    val toBuild = buildSet.toList
    
    if (monitor != null)
      monitor.beginTask("build all", 100)
      
    project.build(toBuild, monitor)
    
    val depends = project.externalDepends.toList.toArray
    if (toBuild.exists(_.hasBuildErrors))
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
  private val setProjectMethod = getDeclaredMethod(ibClazz, "setProject", classOf[IProject])
  private val jbClazz = Class.forName("org.eclipse.jdt.internal.core.builder.JavaBuilder")
  private val initializeBuilderMethod = getDeclaredMethod(jbClazz, "initializeBuilder", classOf[Int], classOf[Boolean])
  
  def setProject(builder : ScalaJavaBuilder, project : IProject) = setProjectMethod.invoke(builder, project)
  def initializeBuilder(builder : ScalaJavaBuilder, kind : Int, forBuild : Boolean) = initializeBuilderMethod.invoke(builder, int2Integer(kind), boolean2Boolean(forBuild))
}

object StateUtils extends ReflectionUtils {
  private val stateClazz = Class.forName("org.eclipse.jdt.internal.core.builder.State").asInstanceOf[Class[State]]
  private val stateCtor = getDeclaredConstructor(stateClazz, classOf[JavaBuilder])
  private val tagAsStructurallyChangedMethod = getDeclaredMethod(stateClazz, "tagAsStructurallyChanged")
  private val structurallyChangedTypesField = getDeclaredField(stateClazz, "structurallyChangedTypes")
  
  def newState(b : JavaBuilder) = stateCtor.newInstance(b)
  
  def tagAsStructurallyChanged(s : State) = tagAsStructurallyChangedMethod.invoke(s)
  
  def resetStructurallyChangedTypes(s : State) = structurallyChangedTypesField.set(s, null)
}
