/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.core.resources.{ IFile, IFolder, IProject, IResource, ResourcesPlugin }
import org.eclipse.core.runtime.{ CoreException, IProgressMonitor, IStatus, Status }
import org.eclipse.jdt.core.{ IClasspathEntry, IJavaElement, IPackageFragment, JavaCore, JavaModelException }
import org.eclipse.jdt.internal.core.{ JavaModelManager, OpenableElementInfo }
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart
import org.eclipse.ui.progress.UIJob

import scala.tools.eclipse.util.ReflectionUtils

object JDTUtils {
  private var refreshPending = false
  private val lock = new Object
  
  def refreshPackageExplorer = {
    lock.synchronized{
      if (!refreshPending) {
        refreshPending = true
        new UIJob("Refresh package explorer") {
          def runInUIThread(monitor : IProgressMonitor) : IStatus  = {
            lock.synchronized {
              refreshPending = false
            }
            val pep = PackageExplorerPart.getFromActivePerspective
            if (pep != null)
              pep.getTreeViewer.refresh()
            Status.OK_STATUS
          }
        }.schedule
      }
    }
  }

  def getParentPackage(scalaFile : IFile) : IPackageFragment = {
    val jp = JavaCore.create(scalaFile.getProject)
    val pkg = JavaModelManager.determineIfOnClasspath(scalaFile, jp)
    if (pkg != null && pkg.isInstanceOf[IPackageFragment])
      pkg.asInstanceOf[IPackageFragment]
    else {
      // Not on classpath so use the default package
      val root = jp.getPackageFragmentRoot(scalaFile.getParent)
      root.getPackageFragment(IPackageFragment.DEFAULT_PACKAGE_NAME)
    }
  }

  def flattenProject(project : IProject) : Iterator[IFile] = {
    try {
      if (!ScalaPlugin.isScalaProject(project))
        return Iterator.empty
      
      val jp = JavaCore.create(project)
      jp.getRawClasspath.filter(_.getEntryKind == IClasspathEntry.CPE_SOURCE).
        elements.flatMap(entry => flatten(ResourcesPlugin.getWorkspace.getRoot.findMember(entry.getPath)))
    } catch {
      case _ : JavaModelException => Iterator.empty
    }
  }

  def flatten(r : IResource) : Iterator[IFile] = {
    try {
      r match {
        case r if !r.exists => Iterator.empty
        case folder : IFolder if folder.getType == IResource.FOLDER => folder.members.elements.flatMap{flatten _}
        case file : IFile if file.getType == IResource.FILE && file.getFileExtension == "scala" => Iterator.single(file)
        case _ => Iterator.empty
      }
    } catch {
      case _ : CoreException => Iterator.empty
    }
  }
}

object OpenableElementInfoUtils extends ReflectionUtils {
  private val clazz = Class.forName("org.eclipse.jdt.internal.core.OpenableElementInfo")
  private val addChildMethod = getMethod(clazz, "addChild", classOf[IJavaElement])
  private val removeChildMethod = getMethod(clazz, "removeChild", classOf[IJavaElement])
  private val setChildrenMethod = getMethod(clazz, "setChildren", classOf[Array[IJavaElement]])
  
  def addChild(info : OpenableElementInfo, child : IJavaElement) = addChildMethod.invoke(info, child)
  def removeChild(info : OpenableElementInfo, child : IJavaElement) = removeChildMethod.invoke(info, child)
  def setChildren(info : OpenableElementInfo, children : Array[IJavaElement]) = setChildrenMethod.invoke(info, children : AnyRef)
}

object SourceRefElementInfoUtils extends ReflectionUtils {
  import java.lang.Integer

  private val sreiClazz = Class.forName("org.eclipse.jdt.internal.core.SourceRefElementInfo")
  private val setSourceRangeStartMethod = getDeclaredMethod(sreiClazz, "setSourceRangeStart", classOf[Int])
  private val setSourceRangeEndMethod = getDeclaredMethod(sreiClazz, "setSourceRangeEnd", classOf[Int])
  
  def setSourceRangeStart(start : Int) : Unit = setSourceRangeStartMethod.invoke(this, new Integer(start))
  def setSourceRangeEnd(end : Int) : Unit = setSourceRangeEndMethod.invoke(this, new Integer(end))
}
