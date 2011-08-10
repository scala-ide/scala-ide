package scala.tools.eclipse.buildmanager.sbtintegration

import scala.tools.nsc.Settings

import sbt.{ScalaInstance, LoggerReporter, ClasspathOptions}
import xsbti.{AnalysisCallback, Reporter, Logger, Controller}

/*
 * In comparison to sbt.compile.AnalyzingCompiler we only need a single version of the scala compiler to support ATM.
 * So this is a simplified interface for the compiler that doesn't need dual loader.
 * But in the near future this will use a dual loader.
 */
class ScalaSbtCompiler(val settings: Settings,
        val scalaInstance: ScalaInstance,
        val cp: ClasspathOptions,
        reporter: Reporter) {
  def compile(args: Seq[String], callback: AnalysisCallback, maxErrors:Int, log: Logger, contr: Controller) {
    val cInterface = new xsbt.CompilerInterface
    cInterface.run(args.toArray[String], callback, log, reporter, contr)
  }
}