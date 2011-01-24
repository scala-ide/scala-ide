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
import org.eclipse.core.resources.IMarker

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
  
  /**
   * @param kind the kind of build being requested. Valid values are
   * <ul>
   * <li>{@link #FULL_BUILD} - indicates a full build.</li>
   * <li>{@link #INCREMENTAL_BUILD}- indicates an incremental build.</li>
   * <li>{@link #AUTO_BUILD} - indicates an automatically triggered
   * incremental build (autobuilding on).</li>
   * </ul>
   * @param args a table of builder-specific arguments keyed by argument name
   * (key type: <code>String</code>, value type: <code>String</code>);
   * <code>null</code> is equivalent to an empty map
   * @param monitor a progress monitor, or <code>null</code> if progress
   * reporting and cancellation are not desired
   * @return the list of projects for which this builder would like deltas the
   * next time it is run or <code>null</code> if none
   */
  override def build(kind : Int, ignored : ju.Map[_, _], monitor : IProgressMonitor) : Array[IProject] = Tracer.timeOf("ScalaBuilder build :" + kindToString(kind)){
    import IncrementalProjectBuilder._

    val project = plugin.getScalaProject(getProject)
    
    val depends = project.externalDepends
    val cleanBuildForced = IDESettings.alwaysCleanBuild.value || dependsChangedWithNoError(depends)
    val newKind = cleanBuildForced match {
      case true => {
        Tracer.println("clean+build forced")
        clean(monitor)
        FULL_BUILD
      }
      case false => kind
    }

    
    val allSourceFiles = project.allSourceFiles()
    val (addedOrUpdated, removed) = newKind match {
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
        //TODO made a less brutal update of PresentationCompiler (+TopLevelMap) / RefinedBuilder when some files are removed/renamed
        removed0.isEmpty match {
          case false => {
            Tracer.println("clean+build forced due to files removed/renamed")
            clean(monitor)
            (allSourceFiles, Set.empty[IFile])
          }
          case true => (addedOrUpdated0.toSet, removed0.toSet) 
        }
      }
      case CLEAN_BUILD | FULL_BUILD => {
        (allSourceFiles, Set.empty[IFile])
      }
    }
    
    (addedOrUpdated.isEmpty && removed.isEmpty) match {
      case true => Tracer.println("no scala/java changes")
      case false => {
        project.build(addedOrUpdated, removed, monitor)
        callJavaBuilder(newKind, ignored, monitor) // ignore returned value, else next time, this should take care in depends definition 
        JDTUtils.refreshPackageExplorer
      }
    }
    depends
  }
  
  private def dependsChangedWithNoError(depends : Array[IProject]) = {
    depends.exists{ x => 
      val delta = getDelta(x)
      (
        x.getProject.isOpen
        && (delta == null || delta.getKind != IResourceDelta.NO_CHANGE)
        //&& !FileUtils.hasBuildErrors(x.getProject)
        && !(x.getProject.findMarkers(null, true, IResource.DEPTH_INFINITE).exists(_.getAttribute(IMarker.SEVERITY) == IMarker.SEVERITY_ERROR))
      )
    }
  }

  private def callJavaBuilder(kind : Int, ignored : ju.Map[_, _], monitor : IProgressMonitor) : Array[IProject] = {
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
    javaDepends
  }
  
  private def ensureProject = {
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
