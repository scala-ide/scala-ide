package scala.tools.eclipse.buildmanager.sbtintegration

import scala.tools.nsc.Settings
import sbt.{ScalaInstance, LoggerReporter, ClasspathOptions}
import xsbti.{AnalysisCallback, Reporter, Logger, Controller}
import xsbt.Log
import scala.tools.eclipse.ScalaPlugin


object SettingsCleanup {
  def apply(s: Settings, log: Logger): Settings = {
    val toDefault = Set(s.d, s.Ybuildmanagerdebug, s.Ybuilderdebug, s.sourcepath, s.sourcedir,
                         s.YpresentationDebug, s.YpresentationDelay, s.YpresentationLog,
                         s.YpresentationReplay, s.YpresentationVerbose,
                         s.classpath, s.bootclasspath)
    val s1 = ScalaPlugin.defaultScalaSettings(Log.settingsError(log))
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
  
  /** Compile the given sources.
   *  
   *  @param args The compiler command line arguments
   *  @param s    compiler Settings. They are used as a baseline on which `args` are added. This means
   *              `args' take precedence when there are conflicts.
   *              
   *  This method instantiates `CompilerInterface` directly, meaning that `ScalaInstance` and its class loader
   *  isn't used here. That's necessary, since we need to pass a `Settings` object, which the usual Sbt machinery
   *  does not allow.
   */
  def compile(args: Seq[String], callback: AnalysisCallback, maxErrors:Int, log: Logger, contr: Controller, s: Settings) {
    val cInterface = new xsbt.CompilerInterface
    val properSettingsWithErrorReporting = SettingsCleanup(s, log)
    cInterface.run(args.toArray[String], callback, log, reporter, contr, properSettingsWithErrorReporting)
  }
}