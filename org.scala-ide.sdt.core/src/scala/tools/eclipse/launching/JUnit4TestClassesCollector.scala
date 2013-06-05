package scala.tools.eclipse.launching

import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import scala.tools.nsc.MissingRequirementError
import org.eclipse.jdt.core.IType

/** Given the Scala AST of any compilation unit, traverse the AST and collect all top level class
  * definitions that can be executed by the JUnit4 runtime.
  *
  * Note: This implementation assumes that the passed tree corresponds to the whole compilation unit.
  * The reason is that it looks for top level class declarations to determine if there are
  * any "runnable" JUnit test classes. This assumption is of course enforced in the code,
  * have a look at the companion object.
  */
private[launching] abstract class JUnit4TestClassesCollector extends HasLogger {
  protected val global: ScalaPresentationCompiler

  import global._

  /** Collect all top level class definitions that can be executed by the JUnit4 runtime.
    *
    * @param tree The compilation unit's Scala AST.
    */
  def collect(tree: Tree): List[ClassDef] = {
    val collector = new JUnit4TestClassesTraverser
    collector.traverse(tree)
    collector.hits.toList
  }

  /** Traverse the passed `Tree` and collect all class definitions that can be executed by the JUnit4 runtime. */
  private class JUnit4TestClassesTraverser extends global.Traverser {
    val hits: ListBuffer[ClassDef] = ListBuffer.empty

    override def traverse(tree: Tree): Unit = tree match {
      case _: PackageDef => super.traverse(tree)
      case cdef: ClassDef if isRunnableTestClass(cdef) => hits += cdef
      case _ => ()
    }

    private def isRunnableTestClass(cdef: ClassDef): Boolean = {
      global.askOption { () => isTopLevelClass(cdef) && isTestClass(cdef) } getOrElse false
    }

    private def isTopLevelClass(cdef: ClassDef): Boolean = {
      def isConcreteClass: Boolean = {
        val csym = cdef.symbol
        // Abstract classes cannot be run by definition, so they should be ignored.
        // And trait do have the `isClass` member set to `true`, so we need to check `isTrait` as well.
        csym.isClass && !csym.isAbstractClass && !csym.isTrait
      }
      isConcreteClass && cdef.symbol.owner.isPackageClass
    }

    private def isTestClass(cdef: ClassDef): Boolean = hasRunWithAnnotation(cdef) || hasJUnitTestMethod(cdef)

    private def hasRunWithAnnotation(cdef: ClassDef): Boolean = RunWithAnnotationOpt exists { runWithAnn =>
      cdef.symbol.info.baseClasses exists { hasAnnotation(_, runWithAnn) }
    }

    private def hasJUnitTestMethod(cdef: ClassDef): Boolean = TestAnnotationOpt exists { ta =>
      cdef.symbol.info.members.exists { hasAnnotation(_, ta) }
    }

    private def hasAnnotation(member: Symbol, ann: Symbol): Boolean = {
      if (!member.isInitialized) member.initialize
      member.hasAnnotation(ann)
    }
  }

  lazy val TestAnnotationOpt = getClassSafe("org.junit.Test")
  lazy val RunWithAnnotationOpt = getClassSafe("org.junit.runner.RunWith")

  /** Don't crash if the class is not on the classpath. */
  def getClassSafe(fullName: String): Option[Symbol] = {
    try {
      Option(rootMirror.getClass(newTypeName(fullName)))
    } catch {
      case _: MissingRequirementError =>
        logger.info("Type `" + fullName + "` is not available in the project's classpath.")
        None
    }
  }
}
