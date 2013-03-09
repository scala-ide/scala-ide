package scala.tools.eclipse.leaks

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Test
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jdt.core.ICompilationUnit
import java.io.PrintWriter
import java.io.FileOutputStream
import java.util.Calendar
import org.junit.Assert

object compilerProject extends TestProjectSetup("scala-compiler")


/** This test runs the IDE on the Scala compiler project itself and records memory consumption.
 *
 * The test scenario is to open Typers, Trees and Types, then repeatedly add and remove one character
 * in Typers.scala. Each step causes the reconciler to run, build the structure and run the indexer,
 * and whatever else the Eclipse and JDT decide is necessary to do.
 *
 * At each step we record the memory usage after the GC has run. At the end of the test,
 * simple linear regression is used to compute the straight line that best fits the
 * curve, and if the slope is higher than 1 (meaning a leak of 1MB/run), we fail the test.
 *
 * The Scala compiler sources are assumed to be under 'test-workspace/scala-compiler'. They
 * are not part of the repository, but the build-toolchain script unpacks the source for the
 * corresponding scala-compiler dependency (meaning this test runs with any version of the Scala
 * compiler).
 *
 * The individual data points are saved under 'usedMem-<date>.txt', under the test project
 * directory. Use the cool graph-it.R script to see the memory curve for the given test run.
 */
class MemoryLeaksTest extends HasLogger {
  final val mega = 1024 * 1024

  @Test def memoryConsumptionTest() {
    import compilerProject._
    import logger._

    val N = 50
    val filename = "usedmem-%tF.txt".format(Calendar.getInstance.getTime)


    val typerUnit = scalaCompilationUnit("scala/tools/nsc/typechecker/Typers.scala")
    val implicitsUnit = scalaCompilationUnit("scala/tools/nsc/typechecker/Implicits.scala")
    val typesUnit = scalaCompilationUnit("scala/reflect/internal/Types.scala")
    val treesUnit = scalaCompilationUnit("scala/reflect/internal/Trees.scala")

    typeCheckWith(treesUnit, new String(treesUnit.getContents))
    typeCheckWith(typesUnit, new String(typesUnit.getContents))

    val originalTyper = new String(typerUnit.getContents)

    val (prefix, postfix) = originalTyper.splitAt(originalTyper.indexOf("import global._"))
    val changedTyper = prefix + " a\n " + postfix

    info("sleeping..")
    Thread.sleep(20000) // let some indexing be done

    info("Waking up.")

    val usedMem = for (i <- 1 to N) yield {
      val src = if (i % 2 == 0) originalTyper else changedTyper

      val usedMem = withGC {
        typeCheckWith(typerUnit, src)
        typeCheckWith(implicitsUnit, new String(implicitsUnit.getContents))
      }

      info("UsedMem:\t%d\t%d".format(i, usedMem / mega))
      usedMem / mega // report size in MB
    }

    info("=" * 80)

    val outputFile = new PrintWriter(new FileOutputStream(filename))
    outputFile.println("\tusedMem")
    for ((dataPoint, i) <- usedMem.zipWithIndex) {
      outputFile.println("%d\t%d".format(i, dataPoint))
    }
    outputFile.close()
    // drop the first two measurements, since the compiler needs some memory when initializing
    val (a, b) = linearModel((3L to N).toSeq, usedMem.drop(2))
    info("LinearModel: alfa: %.4f\tbeta:%.4f".format(a, b))

    Assert.assertTrue("Rate of memory consumption is alarming! %.4f".format(b), b < 1.0)
  }

  private def typeCheckWith(unit: ScalaCompilationUnit with ICompilationUnit, src: String) = {
    unit.becomeWorkingCopy(null)
    unit.getBuffer().setContents(src)
    unit.commitWorkingCopy(true, null) // trigger indexing and structure builder
    unit.discardWorkingCopy()

    logger.debug("Problems: " + unit.asInstanceOf[ScalaSourceFile].getProblems)

    // then
    compilerProject.project.withSourceFile(unit) { (sourceFile, compiler) =>
      try {
        compiler.withStructure(sourceFile, keepLoaded = true) { tree =>
          compiler.askOption { () =>
            val overrideIndicatorBuilder = new compiler.OverrideIndicatorBuilderTraverser(unit, new java.util.HashMap)

            overrideIndicatorBuilder.traverse(tree)
          }
        }
      }
    }()

  }


  /** Return the linear model of these values, (a, b). First value is the constant factor,
   *  second value is the slope, i.e. `y = a + bx`
   *
   *  The linear model of a set of points is a straight line that minimizes the square distance
   *  between the each point and the line.
   *
   *  See: http://en.wikipedia.org/wiki/Simple_linear_regression
   */
  def linearModel(xs: Seq[Long], ys: Seq[Long]): (Double, Double) = {
    require(xs.length == ys.length)

    def mean(v: Seq[Long]): Double = v.sum.toDouble / v.length

    val meanXs = mean(xs)
    val meanYs = mean(ys)

    val beta = (mean((xs, ys).zipped.map(_ * _)) - meanXs * meanYs) / (mean(xs.map(x => x * x)) - meanXs * meanXs)
    val alfa = meanYs - beta * meanXs

    (alfa, beta)
  }

  /** Run the given closure and return the amount of used memory at the end of its execution.
   *
   *  Runs the GC before and after the execution of `f'.
   */
  def withGC(f: => Unit): Long = {
    val r = Runtime.getRuntime
    System.gc()

    f;

    System.gc()

    r.totalMemory() - r.freeMemory()
  }

}