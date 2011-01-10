/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable.HashSet

import java.{ lang => jl, util => ju }

import org.eclipse.core.resources.{ IFile, IncrementalProjectBuilder, IProject, IResource, IResourceDelta, IResourceDeltaVisitor, IResourceVisitor }
import org.eclipse.core.runtime.{ IProgressMonitor, IPath }
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.builder.{ JavaBuilder, NameEnvironment, State }

import scala.tools.eclipse.contribution.weaving.jdt.builderoptions.ScalaJavaBuilder
import scala.tools.eclipse.javaelements.JDTUtils
import scala.tools.eclipse.util.{ FileUtils, ReflectionUtils }
import scala.tools.eclipse.internal.logging.Tracer
import scala.tools.eclipse.util.IDESettings

class ScalaBuilder extends IncrementalProjectBuilder {
  def plugin = ScalaPlugin.plugin

  private val scalaJavaBuilder = new ScalaJavaBuilder
  
  override def clean(monitor : IProgressMonitor) {
    super.clean(monitor)
    Tracer.println("ScalaBuilder clean")
    val project = plugin.getScalaProject(getProject)
    project.clean(monitor)
    
    ensureProject
    scalaJavaBuilder.clean(monitor)
    JDTUtils.refreshPackageExplorer
  }
  
  override def build(kind : Int, ignored : ju.Map[_, _], monitor : IProgressMonitor) : Array[IProject] = Tracer.timeOf("ScalaBuilder build :" + kindToString(kind)){
    import IncrementalProjectBuilder._

    val project = plugin.getScalaProject(getProject)
    
    val allSourceFiles = project.allSourceFiles()
    val dependeeProjectChanged =
      project.externalDepends.exists(
        x => { val delta = getDelta(x); delta == null || delta.getKind != IResourceDelta.NO_CHANGE})
    val cleanBuildForced = IDESettings.alwaysCleanBuild.value || dependeeProjectChanged
    val (addedOrUpdated, removed) = cleanBuildForced match {
      case true => {
        Tracer.println("clean+build forced")
        clean(monitor)
        (allSourceFiles, Set.empty[IFile])
      }
      case false => kind match {
        case INCREMENTAL_BUILD | AUTO_BUILD => {
          val addedOrUpdated0 = new HashSet[IFile] ++ allSourceFiles.filter(FileUtils.hasBuildErrors(_))
          val removed0 = new HashSet[IFile]
          val sourceFolders = project.sourceFolders.map{ _.getLocation }
          getDelta(project.underlying).accept(new IResourceDeltaVisitor {
            def visit(delta : IResourceDelta) = {
              delta.getResource match {
                case file : IFile if plugin.isBuildable(file) && sourceFolders.exists(_.isPrefixOf(file.getLocation)) =>
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
          (addedOrUpdated0.toSet, removed0.toSet)
        }
        case CLEAN_BUILD | FULL_BUILD => {
          (allSourceFiles, Set.empty[IFile])
        }
      }
    }
    
    project.build(addedOrUpdated, removed, monitor)
    //TODO trigger rebuild of depends only if no error in current build
    val depends = project.externalDepends
    val back = ((allSourceFiles.exists(_.getFileExtension == "java")) match {
      case false => {
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
        (depends ++ javaDepends).toArray
      }
      case true => depends
    }).distinct
    
    // reset classpath of depend project to force reload of newly generated class in the current thread (using events can raised event too late)
    for( p <- back if plugin.isScalaProject(p)) {
      plugin.getScalaProject(p).resetCompilers(monitor)
    }
    back
  }
  
  def ensureProject = {
    if (scalaJavaBuilder.getProject == null)
      scalaJavaBuilder.setProject0(getProject)
  }
  
  private def kindToString(k : Int) = k match {
    case IncrementalProjectBuilder.AUTO_BUILD  => "AUTO_BUILD"
    case IncrementalProjectBuilder.INCREMENTAL_BUILD => "INCREMENTAL_BUILD"
    case IncrementalProjectBuilder.CLEAN_BUILD => "CLEAN_BUILD"
    case IncrementalProjectBuilder.FULL_BUILD => "FULL_BUILD"
    case x => x.toString
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
