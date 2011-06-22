/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable.HashSet

import java.{ lang => jl, util => ju }

import org.eclipse.core.resources.{ IFile, IncrementalProjectBuilder, IProject, IResource, IResourceDelta, IResourceDeltaVisitor, IResourceVisitor }
import org.eclipse.core.runtime.{ IProgressMonitor, IPath, SubMonitor }
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.builder.{ JavaBuilder, NameEnvironment, State }

import scala.tools.eclipse.javaelements.JDTUtils
import scala.tools.eclipse.util.{ FileUtils, ReflectionUtils }

class ScalaBuilder extends IncrementalProjectBuilder {
  def plugin = ScalaPlugin.plugin

  private val scalaJavaBuilder = new GeneralScalaJavaBuilder
  
  override def clean(monitor : IProgressMonitor) {
    super.clean(monitor)
    val project = plugin.getScalaProject(getProject)
    project.clean(monitor)
    
    ensureProject
    scalaJavaBuilder.clean(monitor)
    JDTUtils.refreshPackageExplorer
  }
  
  override def build(kind : Int, ignored : ju.Map[_, _], monitor : IProgressMonitor) : Array[IProject] = {
    import IncrementalProjectBuilder._

    val project = plugin.getScalaProject(getProject)
    
    val allSourceFiles = project.allSourceFiles()
    val dependeeProjectChanged = false
//      project.externalDepends.exists(
//        x => { val delta = getDelta(x); delta == null || delta.getKind != IResourceDelta.NO_CHANGE})
    
    val (addedOrUpdated, removed) = if (project.prepareBuild() || dependeeProjectChanged)
      (allSourceFiles, Set.empty[IFile])
    else {
      kind match {
        case INCREMENTAL_BUILD | AUTO_BUILD =>
          val addedOrUpdated0 = new HashSet[IFile] ++ allSourceFiles.filter(FileUtils.hasBuildErrors(_))
          val removed0 = new HashSet[IFile]
                                          
          getDelta(project.underlying).accept(new IResourceDeltaVisitor {
            def visit(delta : IResourceDelta) = {
              delta.getResource match {
                case file : IFile if plugin.isBuildable(file) && project.sourceFolders.exists(_.isPrefixOf(file.getLocation)) =>
                  delta.getKind match {
                    case IResourceDelta.ADDED | IResourceDelta.CHANGED =>
                      addedOrUpdated0 += file
                    case IResourceDelta.REMOVED =>
                      removed0 += file
                    case _ =>
                  }
                case _ =>
              }
              true
            }
          })
          (Set.empty ++ addedOrUpdated0, Set.empty ++ removed0)
        case CLEAN_BUILD | FULL_BUILD =>
          (allSourceFiles, Set.empty[IFile])
      }
    }

    val subMonitor = SubMonitor.convert(monitor, 100).newChild(100, SubMonitor.SUPPRESS_NONE)
    subMonitor.beginTask("Running Scala Builder", 100)
      
    project.build(addedOrUpdated, removed, subMonitor)
    
    val depends = project.externalDepends.toList.toArray
    if (allSourceFiles.exists(FileUtils.hasBuildErrors(_)))
      depends
    else {
      ensureProject
      val javaDepends = scalaJavaBuilder.build(kind, ignored, subMonitor) 
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

object StateUtils extends ReflectionUtils {
  private val stateClazz = Class.forName("org.eclipse.jdt.internal.core.builder.State").asInstanceOf[Class[State]]
  private val stateCtor = getDeclaredConstructor(stateClazz, classOf[JavaBuilder])
  private val tagAsStructurallyChangedMethod = getDeclaredMethod(stateClazz, "tagAsStructurallyChanged")
  private val structurallyChangedTypesField = getDeclaredField(stateClazz, "structurallyChangedTypes")
  
  def newState(b : JavaBuilder) = stateCtor.newInstance(b)
  
  def tagAsStructurallyChanged(s : State) = tagAsStructurallyChangedMethod.invoke(s)
  
  def resetStructurallyChangedTypes(s : State) = structurallyChangedTypesField.set(s, null)
}
