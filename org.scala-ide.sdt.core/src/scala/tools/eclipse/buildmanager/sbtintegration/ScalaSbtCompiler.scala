package scala.tools.eclipse.buildmanager.sbtintegration

import scala.tools.nsc.Settings

import sbt.{ScalaInstance, LoggerReporter, ClasspathOptions}
import xsbti.{AnalysisCallback, Reporter, Logger, Controller}
import xsbt.Log


object SettingsCleanup {
  def apply(s: Settings, log: Logger): Settings = {
    val toDefault = Set(s.d, s.Ybuildmanagerdebug, s.Ybuilderdebug, s.sourcepath, s.sourcedir,
                         s.YpresentationDebug, s.YpresentationDelay, s.YpresentationLog,
                         s.YpresentationReplay, s.YpresentationVerbose,
                         s.classpath, s.bootclasspath)
    val s1 = new Settings(Log.settingsError(log))
    val xs = (s.userSetSettings -- toDefault).toList flatMap (_.unparse)

    s1.processArguments(xs.toList, true)
    // Needs to preserve output directories
    s.outputDirs.outputs.foreach(v => s1.outputDirs.add(v._1, v._2))
    s1
  }
}

/*
 * In comparison to sbt.compile.AnalyzingCompiler we only need a single version of the scala compiler to support ATM.
 * So this is a simplified interface for the compiler that doesn't need dual loader.
 * But in the near future this will use a dual loader.
 */
class ScalaSbtCompiler(val scalaInstance: ScalaInstance, reporter: Reporter) {
  def compile(args: Seq[String], callback: AnalysisCallback, maxErrors:Int, log: Logger, contr: Controller, s: Settings) {
    val cInterface = new xsbt.CompilerInterface
    val properSettingsWithErrorReporting = SettingsCleanup(s, log)
    cInterface.run(args.toArray[String], callback, log, reporter, contr, properSettingsWithErrorReporting)
  }
}