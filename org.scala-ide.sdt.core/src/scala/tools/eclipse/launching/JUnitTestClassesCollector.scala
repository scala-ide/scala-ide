package scala.tools.eclipse.launching

import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.reflect.internal.MissingRequirementError
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.jdt.core.IType

object JUnitTestClassesCollector {
  /** Given a Scala compilation unit, collect all top level class definition that can be run as JUnit test classes.*/
  def from(scu: ScalaSourceFile): List[IType] = {
    // If `scu` was typechecked, fail fast if there is any error (because a test class with errors is never runnable).
    if (scu.currentProblems.nonEmpty) Nil
    else collectJUnitTestClass(scu)
  }

  private def collectJUnitTestClass(scu: ScalaSourceFile): List[IType] = scu.withSourceFile { (source, comp) =>
    import comp.{ Response, Tree }
    val response = new Response[Tree]
    comp.askParsedEntered(source, keepLoaded = false, response)

    object JUnitTestClasses extends JUnitTestClassesCollector {
      val global: comp.type = comp
    }

    val trees = response.get.left.getOrElse(comp.EmptyTree)
    for {
      cdef <- JUnitTestClasses.collect(trees)
      javaElement <- comp.getJavaElement(cdef.symbol, scu.getJavaProject)
    } yield javaElement.asInstanceOf[IType]
  }()
}

/** Given the Scala AST of any compilation unit, collect all top level class definition that can
  * be run as JUnit test classes.
  *
  * Note: This implementation assumes that the passed tree represents the whole compilation unit.
  * The reason is that it looks for top level class declaration to determine if there are
  * any "runnable" JUnit test classes. This assumption is of course enforced in the code,
  * have a look at the companion object.
  */
private trait JUnitTestClassesCollector {
  val global: ScalaPresentationCompiler

  import global._

  /** Collect all top level class definition that can be run as JUnit test classes.
    *
    * @param tree The compilation unit's Scala AST.
    */
  def collect(tree: Tree): List[ClassDef] = {
    val collector = new JUnitTestClassesCollector
    collector.traverse(tree)
    collector.hits.toList
  }

  /** Traverse the passed `Tree` and collect all Class definition that can be run
    * as a JUnit test class.
    */
  private class JUnitTestClassesCollector extends global.Traverser {
    import JUnitTestClassesCollector._

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
        // abstract classes cannot be run by definition, so they should be ignored. 
        csym.isClass && !csym.isTrait && !csym.isAbstractClass
      }
      isConcreteClass && cdef.symbol.owner.isPackageClass
    }

    private def isTestClass(cdef: Tree): Boolean =
      hasJUnitTestMethod(cdef) || hasScalaTestAssertionMixedIn(cdef)

    private def hasJUnitTestMethod(cdef: Tree): Boolean = {
      JUnitTestAnnotationOpt.exists { ta =>
        // For a typecheked tree, check the `annotations` symbol's member.
        cdef.symbol.info.members.exists { _.hasAnnotation(ta) } ||
          // While for a parse tree, check if the annotation is in the AST.
          hasJUnitTestMethod(cdef, ta)
      }
    }

    private def hasJUnitTestMethod(cdef: Tree, jUnitTestAnnotation: Symbol): Boolean = {
      val t = new JUnitTestMethodFinder(jUnitTestAnnotation)
      t.traverse(cdef)
      t.found
    }

    private def hasScalaTestAssertionMixedIn(cdef: Tree): Boolean = {
      ScalaTestAssertionsOpt exists { cdef.symbol.info.baseClasses.contains(_) }
    }
  }

  /** Companion object */
  private object JUnitTestClassesCollector {
    private lazy val JUnitTestAnnotationOpt = getClassSafe("org.junit.Test")
    private lazy val ScalaTestAssertionsOpt = getClassSafe("org.scalatest.junit.AssertionsForJUnit")

    /** Don't crash if the class is not on the classpath. */
    private def getClassSafe(fullName: String): Option[Symbol] = try {
      Some(definitions.getClass(newTypeName(fullName)))
    } catch {
      case _: MissingRequirementError => None
    }
  }

  /** Traverse the passed `tree` and stops as soon as it finds a method that
    * has the `JUnitTestAnnotation`.
    * Only top-level method's declarations (in top-level classes) are considered.
    */
  private class JUnitTestMethodFinder(jUnitTestAnnotation: Symbol) extends global.Traverser {
    var found: Boolean = false
    override def traverse(tree: Tree): Unit = tree match {
      case _: PackageDef  => super.traverse(tree)
      case cdef: ClassDef => traverseTrees(cdef.impl.body)
      case ddef: DefDef if !found =>
        found = ddef.mods.hasAnnotationNamed(jUnitTestAnnotation.name.toTypeName)
      case _ => ()
    }
  }
}