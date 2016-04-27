package org.scalaide.core.internal.jdt.model

import java.io.ByteArrayInputStream
import scala.collection.mutable.WeakHashMap
import scala.tools.eclipse.contribution.weaving.jdt.cfprovider.IClassFileProvider
import org.scalaide.logging.HasLogger
import org.eclipse.jdt.core.IClassFile
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.internal.core.ClassFile
import org.eclipse.jdt.internal.core.PackageFragment
import org.scalaide.core.internal.project.ScalaProject

/**
 * Provides a `ScalaClassFile` implementation for classfiles that belong to
 * Scala sources.
 *
 * This class caches the result of the association between package fragments
 * (usually a jar) and classfiles. The cache is based on a heuristic because
 * scanning the entire classpath may be expensive. The heuristic that finds out
 * if a library contains Scala classifles works as follows:
 *
 * 1. Look at the first classfile of a JAR.
 * 2. Check if the classfile was created by scalac.
 * 3. If 2. succeeds, mark the classfile and the library as Scala.
 * 4  If 2. fails, check if the name of the JAR file contains a Scala version
 *    (except for the stdlib, this should always be true for Scala libraries).
 * 5. If 4. fails, all classfiles in the JAR and the JAR itself are determined
 *    to be Java.
 * 6. If 4. succeeds, the current classfile is marked as Java and we continue at
 *    2. by selecting the next classfile.
 *
 * The goal of the above heuristic is to avoid to scan all classfiles of JARs
 * that are created by javac. For Scala libraries we have to scan the entire JAR
 * file anyway, since we need to create a `ScalaClassFile` for each classfile.
 * Since mixed Scala/Java libraries produce mixed classfiles, we can not simply
 * mark a library as Java if we find a Java classfile in a JAR whose name
 * contains a Scala version.
 */
class ScalaClassFileProvider extends IClassFileProvider with HasLogger {

  /**
   * Returns a `ScalaClassFile` implementation if `contents` represent a Scala
   * classfile or `null` if the default JDT implementation should be used.
   */
  override def create(contents: Array[Byte], parent: PackageFragment, name: String): ClassFile = {
    def updateCache(isScalaClassfile: Boolean): Unit = {
      val pfr = parent.getPackageFragmentRoot()
      if (pfr ne null)
        scalaPackageFragments.synchronized {
          if (!scalaPackageFragments.isDefinedAt(pfr)) {
            val jarName = pfr.getElementName
            val isProbablyScalaArtifact = jarName matches """.*_2\.\d+.*"""
            val ignoreClassfile = !isScalaClassfile && isProbablyScalaArtifact

            if (ignoreClassfile)
              logger.debug(s"Do not set $jarName (because of class $name) to be Java because it seems to be a Scala library.")
            else {
              logger.debug(s"Setting $jarName (because of class $name) to be ${if (isScalaClassfile) "Scala" else "Java"}.")
              scalaPackageFragments += pfr -> isScalaClassfile
            }
          }
        }
    }

    val scalaCF = ScalaClassFileDescriber.isScala(new ByteArrayInputStream(contents)) match {
      case Some(sourcePath) => new ScalaClassFile(parent, name, sourcePath)
      case None                => null
    }
    updateCache(scalaCF ne null)
    scalaCF
  }

  /**
   * Returns `true` if the classfile could be a Scala classfile. See Scaladoc
   * for [[ScalaClassFileProvider]] for an explanation about how we find out if
   * classfiles could be Scala classfiles.
   */
  override def isInteresting(classFile: IClassFile): Boolean = {
    if (ScalaProject.isScalaProject(classFile.getJavaProject)) {
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
