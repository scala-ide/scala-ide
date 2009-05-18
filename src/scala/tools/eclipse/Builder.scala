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
import scala.collection.mutable.LinkedHashSet

class Builder extends IncrementalProjectBuilder {
  def plugin = ScalaPlugin.plugin

  def ifile(that : AnyRef) : IFile = 
    if (that.isInstanceOf[IFile]) (that.asInstanceOf[IFile])
    else null

  def ipath(that : AnyRef) =
    if (that.isInstanceOf[IPath]) (that.asInstanceOf[IPath])
    else null

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
    val plugin = this.plugin 
    val project = plugin.projectSafe(getProject).get
    val toBuild = new LinkedHashSet[project.File]
    kind match {
    case CLEAN_BUILD => return project.externalDepends.toList.toArray
    case INCREMENTAL_BUILD|AUTO_BUILD if (!project.doFullBuild) =>
      getDelta(project.underlying).accept(new IResourceDeltaVisitor {
        def visit(delta : IResourceDelta) = ifile(delta.getResource) match {
          case null => true
          case (file) =>
            if (delta.getKind != IResourceDelta.REMOVED) {
              val file0 = project.fileSafe(file)
              if (!file0.isEmpty && project.sourceFolders.exists(_.getLocation.isPrefixOf(file.getLocation))) {
                toBuild += file0.get
              }
            }
            true
        }
      })
      project.externalDepends.map(getDelta).foreach(f => if (f != null) f.accept(new IResourceDeltaVisitor {
        override def visit(delta : IResourceDelta) : Boolean = {
          val file = ifile(delta.getResource)
          if (delta.getKind == IResourceDelta.REMOVED) return true
          if (file == null) return true
          val paths = plugin.reverseDependencies.get(file.getLocation)
          if (paths.isEmpty) return true
          val i = paths.get.elements 
          while (i.hasNext) {
            val path = ipath(i.next)
            if (project.sourceFolders.exists(_.getLocation.isPrefixOf(path))) {
              val p = project.underlying
              val f = p.getFile(path.removeFirstSegments(path.matchingFirstSegments(p.getLocation)))
              toBuild += project.fileSafe(f).get
            }
          }        
          true
        }
      }))
      true
    case _ => 
      project.doFullBuild = false
      val sourceFolders = project.sourceFolders
      sourceFolders.foreach(_.accept(new IResourceVisitor {
        def visit(resource : IResource) =
          ifile(resource) match {
            case null => true
            case (file) =>           
              project.fileSafe(file) match {
                case Some(file0) => toBuild += file0
                case _ =>
              }
              true
          }}))
    }
    
    // everything that needs to be recompiled is in toBuild now. 
    val built = new LinkedHashSet[project.File] // don't recompile twice.
    var buildAgain = false
    if (monitor != null) monitor.beginTask("build all", 100)
    while (!toBuild.isEmpty) {
      toBuild.foreach(_.clearBuildErrors(null))
      toBuild.foreach(f => Console.println("build " + f))
      val changed = project.build(toBuild)(monitor)
      if (!changed.isEmpty) {
        changed.foreach(f => Console.println("changed " + f))
      }
      assert(!toBuild.isEmpty)
      built ++= toBuild
      assert(!built.isEmpty)
      toBuild.clear
      
      def f(changed : project.File) : Unit = changed.underlying.path match {
        case Some(changed) => plugin.reverseDependencies.get(changed) match {
          case Some(paths) => paths.foreach(path => {
            val file = plugin.workspace.getFileForLocation(path)
            if (file.exists) {
              if (file.getProject == project.underlying) {
                project.fileSafe(file) match {
                  case Some(file) if !built.contains(file) => 
                    if (toBuild put file) {
                      //f(file) // transitive colsure of dependencies...sigh.
                    }
                  case Some(file) => plugin.reverseDependencies(changed) += path
                  case _ => file.touch(monitor)
                }
              } else {
                if (hasBeenBuilt(file.getProject)) buildAgain = true
                file.touch(monitor)
              }
            }
          })
          case None => 
        }
        case None =>
      }
      changed.foreach(f)
    }
    if (buildAgain) needRebuild
    else project.buildDone(built)(monitor)
    
    val depends = project.externalDepends.toList.toArray
    
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
