package org.scalaide.core.internal.jdt.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IAccessRule
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IClasspathAttribute
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.JavaCore

/**
 * ScalaContainer Save Helper
 *
 * cribbed from org.eclipse.m2e.jdt.internal.MavenClasspathContainerSaveHelper
 */
object ClasspathContainerSaveHelper {

  def readContainer(input: InputStream): IClasspathContainer = {
    val is = new ObjectInputStream(new BufferedInputStream(input)) {
      enableResolveObject(true)

      override protected def resolveObject(o: Object): Object = o match {
        case i: ClasspathContainerReplace => i.getClasspathContainer()
        case i: ProjectEntryReplace => i.getEntry()
        case i: LibraryEntryReplace => i.getEntry()
        case i: ClasspathAttributeReplace => i.getClasspathAttribute()
        case i: AccessRuleReplace => i.getAccessRule()
        case i: PathReplace => i.getPath()
        case _ => super.resolveObject(o)
      }
    }

    is.readObject().asInstanceOf[IClasspathContainer]
  }

  def writeContainer(container: IClasspathContainer, output: OutputStream): Unit = {
    val os = new ObjectOutputStream(new BufferedOutputStream(output)) {
      enableReplaceObject(true)

      override protected def replaceObject(o: Object): Object = o match {
        case e: IClasspathContainer => new ClasspathContainerReplace(e)
        case e: IClasspathEntry if (e.getEntryKind() == IClasspathEntry.CPE_PROJECT) => new ProjectEntryReplace(e)
        case e: IClasspathEntry if (e.getEntryKind() == IClasspathEntry.CPE_LIBRARY) => new LibraryEntryReplace(e)
        case e: IClasspathAttribute => new ClasspathAttributeReplace(e)
        case e: IAccessRule => new AccessRuleReplace(e)
        case e: IPath => new PathReplace(e)
        case _ => super.replaceObject(o)
      }
    }

    os.writeObject(container)
    os.flush()
  }

  /**
   * A ClasspathContainer replacement used for object serialization
   * (missing from original m2e)
   */
  class ClasspathContainerReplace(val entries: Array[IClasspathEntry], val desc:String, val kind: Int, val containerPath: IPath) extends Serializable {
    def this(cpC: IClasspathContainer) = this(cpC.getClasspathEntries(), cpC.getDescription(), cpC.getKind(), cpC.getPath())

    def getClasspathContainer(): IClasspathContainer = new IClasspathContainer() {
      override def getClasspathEntries() = entries
      override def getDescription(): String = desc
      override def getKind(): Int = kind
      override def getPath(): IPath = containerPath
    }
  }

  /**
   * A library IClasspathEntry replacement used for object serialization
   */
  class LibraryEntryReplace(val path: IPath, val sourceAttachmentPath: IPath, val sourceAttachmentRootPath: IPath, val extraAttributes: Array[IClasspathAttribute], val exported: Boolean, val accessRules: Array[IAccessRule]) extends Serializable {
    private val serialVersionUID = 3901667379326978799L

    def this(entry: IClasspathEntry) = this(entry.getPath(), entry.getSourceAttachmentPath(), entry.getSourceAttachmentRootPath(), entry.getExtraAttributes(), entry.isExported(), entry.getAccessRules())

    def getEntry(): IClasspathEntry = JavaCore.newLibraryEntry(path, sourceAttachmentPath, sourceAttachmentRootPath, accessRules, extraAttributes, exported)
  }

  /**
   * A project IClasspathEntry replacement used for object serialization
   */
  class ProjectEntryReplace(val path: IPath, val extraAttributes: Array[IClasspathAttribute], val accessRules: Array[IAccessRule], val exported: Boolean, val combineAccessRules: Boolean) extends Serializable {
    private val serialVersionUID = -2397483865904288762L

    def this(entry: IClasspathEntry) = this(entry.getPath(), entry.getExtraAttributes(), entry.getAccessRules() , entry.isExported(), entry.combineAccessRules())

    def getEntry(): IClasspathEntry = JavaCore.newProjectEntry(path, accessRules, combineAccessRules, extraAttributes, exported)
  }

  /**
   * An IClasspathAttribute replacement used for object serialization
   */
   class ClasspathAttributeReplace(val name:String, val value:String) extends Serializable {
    private val serialVersionUID = 6370039352012628029L

    def this(attribute: IClasspathAttribute) = this(attribute.getName(), attribute.getValue())
    def getClasspathAttribute(): IClasspathAttribute = JavaCore.newClasspathAttribute(name, value)
  }

  /**
   * An IAccessRule replacement used for object serialization
   */
  class AccessRuleReplace(val pattern: IPath, val kind: Int) extends Serializable {
    private val serialVersionUID = 7315582893941374715L

    def this(accessRule: IAccessRule) = this(accessRule.getPattern(),accessRule.getKind())
    def getAccessRule(): IAccessRule = JavaCore.newAccessRule(pattern, kind)
  }

  /**
   * An IPath replacement used for object serialization
   */
  class PathReplace(val path: String) extends Serializable {
    private val serialVersionUID = -2361259525684491181L
    def this(ip:IPath) = this(ip.toPortableString())
    def getPath() = Path.fromPortableString(path)
  }

}