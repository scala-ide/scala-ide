package scala.tools.eclipse.launching

import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import scala.tools.nsc.MissingRequirementError
import org.eclipse.jdt.core.IType
import scala.tools.eclipse.util.Utils.any2optionable

/** Given a Scala compilation unit, finds all top level class definition that can be run as JUnit4 test classes.
  *
  * If a source contains error, the `JUnit4TestFinder` will likely still be able to find executable JUnit test
  * classes in the passed source. If we wanted to be smart, we could check if the passed source (or even the 
  * enclosing project) has any compile-time error, and return an empty set of runnable test classes.
  * However, this turns out to be a bad idea, because the user may not understand that the reason why it can't run 
  * the test class is because he has to fix all compilation error. Therefore, it is better to always return the 
  * set of executable JUnit4 test classes and let the user figure out the cause why the test class cannot be run.
  */
object JUnit4TestFinder {

  def findTestClasses(scu: ScalaSourceFile): List[IType] = scu.withSourceFile { (source, comp) =>
    import comp.{ ClassDef, Response, Tree }
    val response = new Response[Tree]
    comp.askParsedEntered(source, keepLoaded = false, response)

    object JUnit4TestClasses extends JUnit4TestClassesCollector {
      val global: comp.type = comp
    }

    val trees = response.get.left.getOrElse(comp.EmptyTree)
    for {
      cdef <- JUnit4TestClasses.collect(trees)
      jdtElement <- comp.getJavaElement(cdef.symbol, scu.getJavaProject)
      jdtType <- jdtElement.asInstanceOfOpt[IType]
    } yield jdtType
  }()
}

/** Given the Scala AST of any compilation unit, traverse the AST and collect all top level class
  * definitions that can be executed by the JUnit4 runtime.
  *
  * Note: This implementation assumes that the passed tree corresponds to the whole compilation unit.
  * The reason is that it looks for top level class declaration to determine if there are
  * any "runnable" JUnit test classes. This assumption is of course enforced in the code,
  * have a look at the companion object.
  */
private abstract class JUnit4TestClassesCollector {
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
    import JUnit4TestClassesTraverser._

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

  private object JUnit4TestClassesTraverser extends HasLogger {
    private lazy val TestAnnotationOpt = getClassSafe("org.junit.Test")
    private lazy val RunWithAnnotationOpt = getClassSafe("org.junit.runner.RunWith")

    /** Don't crash if the class is not on the classpath. */
    private def getClassSafe(fullName: String): Option[Symbol] = {
      try {
        Option(definitions.getClass(newTypeName(fullName)))
      } catch {
        case _: MissingRequirementError =>
          logger.info("Type `" + fullName + "` is not available in the project's classpath.")
          None
      }
    }
  }
}