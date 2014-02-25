package org.scalaide.core.internal.jdt.model

import java.io.ByteArrayInputStream
import scala.collection.mutable.WeakHashMap
import org.scalaide.core.ScalaPlugin
import scala.tools.eclipse.contribution.weaving.jdt.cfprovider.IClassFileProvider
import org.scalaide.logging.HasLogger

import org.eclipse.jdt.core.IClassFile
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.internal.core.ClassFile
import org.eclipse.jdt.internal.core.PackageFragment

class ScalaClassFileProvider extends IClassFileProvider with HasLogger {

  /** @return a ScalaClassFile implementation if bytes represent a Scala classfile, or `null`
   *          if the default JDT implementation should be used.
   */
  override def create(contents: Array[Byte], parent: PackageFragment, name: String): ClassFile = {
    def updateCache(isScalaClassfile: Boolean) {
      val pfr = parent.getPackageFragmentRoot()
      if (pfr ne null)
        scalaPackageFragments.synchronized {
          if (!scalaPackageFragments.isDefinedAt(pfr)) {
            logger.debug(s"Setting ${pfr.getElementName} (because of class $name) to be ${if (isScalaClassfile) "Scala" else "Java"}")
            scalaPackageFragments += pfr -> isScalaClassfile
          }
        }
    }

    val scalaCF = ScalaClassFileDescriber.isScala(new ByteArrayInputStream(contents)) match {
      case Some(sourcePath) => new ScalaClassFile(parent, name, sourcePath)
      case _                => null
    }
    updateCache(scalaCF ne null)
    scalaCF
  }

  /** Return `true` if the classfile could be a Scala classfile.
   *
   *  @note This method caches the result of the first classfile read from a package fragment (usually a jar).
   *        This heuristic might fail if a single jar mixes Java and Scala classfiles, and if the first classfile
   *        is comes from Java, a plain Java classfile editor and icon would be used for all classfiles in that jar.
   */
  override def isInteresting(classFile: IClassFile): Boolean = {
    if (ScalaPlugin.plugin.isScalaProject(classFile.getJavaProject)) {
      val pfr = ancestorFragmentRoot(classFile)
      // synchronized needed for visibility
      scalaPackageFragments.synchronized {
        pfr.map(scalaPackageFragments.getOrElse(_, true)).getOrElse(false)
      }
    } else
      false
  }

  private def ancestorFragmentRoot(classFile: IClassFile): Option[IPackageFragmentRoot] =
    classFile.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT) match {
      case pfr: IPackageFragmentRoot => Some(pfr)
      case _                         => None
    }

  private val scalaPackageFragments: WeakHashMap[IPackageFragmentRoot, Boolean] = WeakHashMap.empty
}
