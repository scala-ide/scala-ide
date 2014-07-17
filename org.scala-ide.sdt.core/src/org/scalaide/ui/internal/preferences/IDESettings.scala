package org.scalaide.ui.internal.preferences

import scala.tools.nsc.Settings

object IDESettings {

  import ScalaPluginSettings._
  case class Box(name: String, userSettings: List[Settings#Setting])

  def shownSettings(s : Settings) : List[Box] = {
    import s._

    List(
      Box("Standard",
        List(lint, deprecation, feature, g, optimise, target, unchecked,
             pluginOptions, nospecialization, verbose, explaintypes, nowarn)),
      Box("Advanced",
      List(checkInit, elidebelow,
             Xexperimental, future, XlogImplicits,
             noassertions, nouescape, plugin, disable,
             require, pluginsDir, fatalWarnings)),
      Box("Presentation Compiler",
        List(YpresentationDebug, YpresentationVerbose, YpresentationLog, YpresentationReplay, YpresentationDelay)))
  }

  def buildManagerSettings: List[Box] =
    List(Box("Build manager",
      List(buildManager,
        compileOrder,
        stopBuildOnErrors,
        relationsDebug,
        apiDiff,
        withVersionClasspathValidator,
        recompileOnMacroDef,
        nameHashing)))
}

object ScalaPluginSettings extends Settings {
  val buildManager = ChoiceSetting("-buildmanager", "which", "Build manager to use", List("refined", "sbt"), "sbt")
  val compileOrder = ChoiceSetting("-compileorder", "which", "Compilation order",
      List("Mixed", "JavaThenScala", "ScalaThenJava"), "Mixed")
  val stopBuildOnErrors = new BooleanSettingWithDefault("-stopBuildOnError", "Stop build if dependent projects have errors.", true)
  val relationsDebug = BooleanSetting("-relationsDebug", "Log very detailed information about relations, such as dependencies between source files.")
  val withVersionClasspathValidator = new BooleanSettingWithDefault("-withVersionClasspathValidator", "Check Scala compatibility of jars in classpath", true)
  val apiDiff = BooleanSetting("-apiDiff", "Log type diffs that trigger additional compilation (slows down builder)")
  val recompileOnMacroDef = BooleanSetting("-recompileOnMacroDef", "Always recompile all dependencies of a macro def")
  val nameHashing = BooleanSetting("-nameHashing", "Enable improved (experimental) incremental compilation algorithm")

  /** A setting represented by a boolean flag, with a custom default */
  // original code from MutableSettings#BooleanSetting
  class BooleanSettingWithDefault(
    name: String,
    descr: String,
    val default: Boolean)
    extends Setting(name, descr) {
    type T = Boolean
    protected var v: Boolean = default
    override def value: Boolean = v

    def tryToSet(args: List[String]) = { value = true; Some(args) }
    def unparse: List[String] = if (value) List(name) else Nil
    override def tryToSetFromPropertyValue(s: String) { // used from ide
      value = s.equalsIgnoreCase("true")
    }
    override def tryToSetColon(args: List[String]) = args match {
      case Nil => tryToSet(Nil)
      case List(x) =>
        if (x.equalsIgnoreCase("true")) {
          value = true
          Some(Nil)
        } else if (x.equalsIgnoreCase("false")) {
          value = false
          Some(Nil)
        } else errorAndValue("'" + x + "' is not a valid choice for '" + name + "'", None)
    }

  }

    implicit def booleanSettingOfDefault(b: BooleanSettingWithDefault): Settings#BooleanSetting = {
    val v = b.value
    val s = BooleanSetting(b.name, b.helpDescription)
    if (v) s.tryToSet(Nil)
    s
  }
}
