package org.scalaide.core
package structurebuilder

import org.junit._
import testsetup.SDTTestUtils._
import testsetup.TestProjectSetup
import org.eclipse.core.resources.IFile
import java.util.NoSuchElementException
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._

object ScalaJavaMapperTest extends TestProjectSetup("javamapper") {
  val unit = scalaCompilationUnit("/pack/Target.scala")
}

class ScalaJavaMapperTest {
  import ScalaJavaMapperTest._

  @Test
  def intDescriptor(): Unit = {
    withTargetTree("abstract class Target { val target: Int }") ("I")
  }

  @Test
  def listDescriptor(): Unit = {
    withTargetTree("abstract class Target { val target: List[Int] }") ("Lscala/collection/immutable/List;")
  }

  @Test
  def primitiveArrayDescriptor(): Unit = {
    withTargetTree("abstract class Target { val target: Array[Array[Char]] }") ("[[C")
  }

  @Test
  def refArrayDescriptor(): Unit = {
    withTargetTree("abstract class Target { val target: Array[Object] }") ("[Ljava/lang/Object;")
  }

  @Test
  def innerClassDescriptor(): Unit = {
    withTargetTree("abstract class Target { class Inner; val target: Inner }") ("LTarget/Inner;")
  }

  @Test
  def typeVarClassDescriptor(): Unit = {
    withTargetTree("abstract class Target[T] { val target: T }") ("Ljava/lang/Object;")
  }

  @Test
  def errorClassDescriptor(): Unit = {
    withTargetTree("abstract class Target { val target: NotFount }") ("Ljava/lang/Object;")
  }

  /** Retrieve the `target` type from the given source, extract the Java descriptor for it,
   *  and compare it to the given expected descriptor.
   *
   *  The `src` is supposed to contain one abstract val called `target`, whose type
   *  is retrieved and passed to the type test.
   *
   *  This method reloads `src` in the presentation compiler and waits for the source
   *  to be fully-typechecked, before traversing the tree to find the `target` definition.
   */
  def withTargetTree(src: String)(expectedDescriptor: String) = {
    changeContentOfFile(unit.getResource().asInstanceOf[IFile], src)

    val srcFile = unit.sourceMap(src.toCharArray()).sourceFile
    unit.scalaProject.presentationCompiler{ compiler =>
      compiler.askReload(unit, srcFile)
      val targets = compiler.askLoadedTyped(srcFile, keepLoaded = false).get match {
        case Left(loadedType) =>
          loadedType.collect {
            case t: compiler.ValDef if t.name.toString startsWith "target" => t
          }
        case Right(e) =>
          throw e
      }
      val (tpe, actualDescriptor) = compiler.asyncExec {
        val tpe = targets.head.symbol.info.finalResultType
        val descriptorString = compiler.javaDescriptor(tpe)
        (tpe.toString, descriptorString)
      }.getOrElse (throw new NoSuchElementException(s"Could not find target element in $src"))()

      Assert.assertEquals(s"wrong descriptor of $tpe", expectedDescriptor, actualDescriptor)
    }
  }
}

/** This class is a function of 2 parameters, compiler and a compiler.Type.
 *  It is needed because path dependent type as parameters are not allowed
 *  in anonymous functions.
 */
trait TypeTest {
  def apply(compiler: IScalaPresentationCompiler)(tree: compiler.Type): Unit
}
