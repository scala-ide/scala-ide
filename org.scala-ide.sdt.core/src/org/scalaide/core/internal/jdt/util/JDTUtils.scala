package org.scalaide.core.internal.jdt.util

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IFolder
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.JavaModelException
import org.eclipse.jdt.internal.core.ImportContainerInfo
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.NameLookup
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart
import org.eclipse.ui.progress.UIJob
import org.scalaide.util.internal.ReflectionUtils
import org.scalaide.core.internal.project.ScalaProject

object JDTUtils {
  private var refreshPending = false
  private val lock = new Object

  def refreshPackageExplorer() = {
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

  def resolveType(nameLookup : NameLookup, packageName : String, typeName : String, acceptFlags : Int) : Option[IType] = {
    val pkgs = nameLookup.findPackageFragments(packageName, false)
    for(p <- pkgs) {
      val tpe = nameLookup.findType(typeName, p, false, acceptFlags, true, true)
      if (tpe != null)
        return Some(tpe)
    }

    return None
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
      if (!ScalaProject.isScalaProject(project))
        return Iterator.empty

      val jp = JavaCore.create(project)
      jp.getRawClasspath.filter(_.getEntryKind == IClasspathEntry.CPE_SOURCE).
        iterator.flatMap(entry => flatten(ResourcesPlugin.getWorkspace.getRoot.findMember(entry.getPath)))
    } catch {
      case _ : JavaModelException => Iterator.empty
    }
  }

  def flatten(r : IResource) : Iterator[IFile] = {
    try {
      r match {
        case r if r == null || !r.exists => Iterator.empty
        case folder : IFolder if folder.getType == IResource.FOLDER => folder.members.iterator.flatMap{flatten _}
        case file : IFile if file.getType == IResource.FILE && file.getFileExtension == "scala" => Iterator.single(file)
        case _ => Iterator.empty
      }
    } catch {
      case _ : CoreException => Iterator.empty
    }
  }
}

object SourceRefElementInfoUtils extends ReflectionUtils {
  private val sreiClazz = Class.forName("org.eclipse.jdt.internal.core.SourceRefElementInfo")
  private val setSourceRangeStartMethod = getDeclaredMethod(sreiClazz, "setSourceRangeStart", classOf[Int])
  private val setSourceRangeEndMethod = getDeclaredMethod(sreiClazz, "setSourceRangeEnd", classOf[Int])

  def setSourceRangeStart(srei : AnyRef, pos : Int) = setSourceRangeStartMethod.invoke(srei, new Integer(pos))
  def setSourceRangeEnd(srei : AnyRef, pos : Int) = setSourceRangeEndMethod.invoke(srei, new Integer(pos))
}

object ImportContainerInfoUtils extends ReflectionUtils {
  private val iciClazz = classOf[ImportContainerInfo]
  private val childrenField = getDeclaredField(iciClazz, "children")

  def setChildren(ic : ImportContainerInfo, children : Array[IJavaElement]): Unit = { childrenField.set(ic, children) }
  def getChildren(ic : ImportContainerInfo) = childrenField.get(ic).asInstanceOf[Array[IJavaElement]]
}
