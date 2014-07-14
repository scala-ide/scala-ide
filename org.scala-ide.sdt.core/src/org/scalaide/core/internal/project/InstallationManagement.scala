package org.scalaide.core.internal.project

import language.implicitConversions

import org.scalaide.core.internal.jdt.util.ClasspathContainerSetter
import org.eclipse.jface.preference.IPreferenceStore
import org.scalaide.util.internal.CompilerUtils
import scala.util.Try
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.eclipse.jface.util.IPropertyChangeListener
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.core.IScalaPlugin
import org.eclipse.core.resources.IMarker
import scala.tools.nsc.settings.ScalaVersion
import scala.util.Failure
import org.eclipse.jface.util.PropertyChangeEvent
import scala.util.Success
import org.scalaide.core.resources.MarkerFactory
import org.eclipse.core.runtime.Path
import scala.tools.nsc.Settings
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.ScalaInstallationChange
import org.scalaide.core.SdtConstants
import org.scalaide.core.compiler.ScalaPresentationCompiler

trait InstallationManagement { this: ScalaProject =>

  case class WithValidation[A, B](isValid: A => Boolean, unsafeGetter: A => B, registerDefault: (A, B) => WithValidation[A,B]) {
    def get(key: A)(implicit default: B): B = {
      if (!isValid(key)) registerDefault(key, default).unsafeGetter(key)
      else unsafeGetter(key)
    }
  }

  implicit private def validatedProjectPrefStore(p:IPreferenceStore): WithValidation[String, String] =
    WithValidation(
        p.contains,
        p.getString,
        { (key:String, default:String) => eclipseLog.warn(s"Preference ${key} was uninitialized, setting default to ${default}.")
          p.setDefault(key, default); validatedProjectPrefStore(p) }
    )

  // this is technically generic and could apply to any A => Option[B]
  // except for its logged message
  implicit private def validatedScalaInstallationChoice(parse: String => Option[ScalaInstallationChoice]): WithValidation[String, ScalaInstallationChoice] =
    WithValidation(
        ((str: String) => parse(str).isDefined),
        ((str: String) => parse(str).get),
        { (key: String, default: ScalaInstallationChoice) =>
          eclipseLog.warn(s"Found an unparseable preference set for ${key}, resetting to ${default.toString}.")
          validatedScalaInstallationChoice({ (str: String) => if (str equals key) Some(default) else parse(str) }) }
    )

  implicit private def validatedLabeledScalaInstallation(resolve: ScalaInstallationChoice => Option[LabeledScalaInstallation]): WithValidation[ScalaInstallationChoice, LabeledScalaInstallation] =
    WithValidation(
        ((choice:ScalaInstallationChoice) => resolve(choice).isDefined),
        ((choice:ScalaInstallationChoice) => resolve(choice).get),
        { (key: ScalaInstallationChoice, default: LabeledScalaInstallation) =>
          val displayChoice: String = key.marker match {
          case Left(version) => s"Latest ${CompilerUtils.shortString(version)} bundle (dynamic)"
          case Right(hash) => s"Fixed Scala Installation with hash ${hash}"
          }
          val msg = s"The specified installation choice for this project ($displayChoice) could not be found. Please configure a Scala Installation for this specific project."
          object svMarkerFactory extends MarkerFactory(SdtConstants.ScalaVersionProblemMarkerId)
          svMarkerFactory.create(underlying, IMarker.SEVERITY_ERROR, msg)
          validatedLabeledScalaInstallation({ (choice: ScalaInstallationChoice) => if (choice equals key) Some(default) else resolve(choice) }) }
   )

  /** Which Scala source level is this project configured to work with ? */
  def desiredSourceLevel(): String = {
    implicit val sourceLevelDefault = IScalaPlugin().shortScalaVersion
    val sourceLevelPrefName = SettingConverterUtil.SCALA_DESIRED_SOURCELEVEL
    if (!usesProjectSettings) {
      logger.warn(s"Project ${this.underlying.getName()} has platform default sourceLevel.")
      sourceLevelDefault
    }
    else projectSpecificStorage.get(sourceLevelPrefName)
  }

  /** Which Scala installation is this project wished to work with ? - always returns a valid choice, but it may or not resolve */
  def desiredinstallationChoice(): ScalaInstallationChoice = {
    implicit val desiredinstallationChoiceDefault: ScalaInstallationChoice = ScalaInstallationChoice(ScalaVersion(desiredSourceLevel()))
    implicit val desiredinstallationChoicePrefDefault: String = desiredinstallationChoiceDefault.toString()
    val desiredinstallationChoicePrefName = SettingConverterUtil.SCALA_DESIRED_INSTALLATION
    if (!usesProjectSettings) {
      logger.warn(s"Project ${this.underlying.getName()} runs on platform default installation.")
      desiredinstallationChoiceDefault
    }
    else {
      (parseScalaInstallationChoice _ ).get(projectSpecificStorage.get(desiredinstallationChoicePrefName))
    }
  }

  /** Which Scala installation is this project configured to work with ? - always returns a valid installation that resolves */
  def effectiveScalaInstallation(): LabeledScalaInstallation = {
    implicit val desiredInstallationDefault: LabeledScalaInstallation = ScalaInstallation.resolve(ScalaInstallationChoice(IScalaPlugin().scalaVersion)).get
    (ScalaInstallation.resolve _).get(desiredinstallationChoice())
  }

  private def turnOnProjectSpecificSettings(reason: String){
    if (!usesProjectSettings) {
      val pName = this.toString
      eclipseLog.warn(s"Turning on project-specific settings for $pName because of $reason")
      projectSpecificStorage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)
    }
  }

  private def turnOffProjectSpecificSettings(reason: String){
    if (usesProjectSettings){
      val pName = this.toString
      eclipseLog.warn(s"Turning off project-specific settings for $pName because of $reason")
      projectSpecificStorage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, false)
    }
  }

  private def parseScalaInstallationChoice(str: String): Option[ScalaInstallationChoice] = Try(str.toInt) match {
    case Success(int) => Some(ScalaInstallationChoice(Right(int)))
    case Failure(t) => t match {
      case ex: NumberFormatException => Try(ScalaVersion(str)).toOption map (ScalaInstallationChoice(_))
    }
  }

  def setDesiredInstallation(choice: ScalaInstallationChoice = desiredinstallationChoice()) : Unit = {
    val optsi = ScalaInstallation.resolve(choice) // This shouldn't do anything if the choice doesn't resolve
    val sourceLevel = optsi map {si => CompilerUtils.shortString(si.version)}

    def bundleUpdater(si: ScalaInstallation): () => Unit = {() =>
      val updater = new ClasspathContainerSetter(javaProject)
      updater.updateBundleFromScalaInstallation(new Path(SdtConstants.ScalaLibContId), si)
      updater.updateBundleFromScalaInstallation(new Path(SdtConstants.ScalaCompilerContId), si)
    }

    // This is a precaution against scala installation loss and does not set anything by itself, see `SettingConverterUtil.SCALA_DESIRED_SOURCELEVEL`
    sourceLevel foreach {sl => projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_SOURCELEVEL, sl)}
    optsi foreach {si => setDesiredSourceLevel(si.version, "requested Scala Installation change", Some(bundleUpdater(si)))}
    publish(ScalaInstallationChange())
  }

  def setDesiredSourceLevel(scalaVersion: ScalaVersion = ScalaVersion(desiredSourceLevel()),
      slReason: String = "requested Source Level change",
      customBundleUpdater: Option[() => Unit] = None): Unit = {
    projectSpecificStorage.removePropertyChangeListener(compilerSettingsListener)
    turnOnProjectSpecificSettings(slReason)
    // is the required sourceLevel the bundled scala version ?
    if (isUsingCompatibilityMode()) {
      if (CompilerUtils.isBinarySame(IScalaPlugin().scalaVersion, scalaVersion)) {
        unSetXSourceAndMaybeUntoggleProjectSettings(slReason)
      }
    } else {
      if (CompilerUtils.isBinaryPrevious(IScalaPlugin().scalaVersion, scalaVersion)) {
        toggleProjectSpecificSettingsAndSetXsource(scalaVersion, slReason)
      }
    }
    // The ordering from here until reactivating the listener is important
    projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_SOURCELEVEL, CompilerUtils.shortString(scalaVersion))
    val updater = customBundleUpdater.getOrElse({() =>
      val setter = new ClasspathContainerSetter(javaProject)
      setter.updateBundleFromSourceLevel(new Path(SdtConstants.ScalaLibContId), scalaVersion)
      setter.updateBundleFromSourceLevel(new Path(SdtConstants.ScalaCompilerContId), scalaVersion)
      }
    )
    updater()
    classpathHasChanged()
    projectSpecificStorage.addPropertyChangeListener(compilerSettingsListener)
  }

  private def toggleProjectSpecificSettingsAndSetXsource(scalaVersion: ScalaVersion, reason: String) = {
    turnOnProjectSpecificSettings("requested Xsource change")
    val scalaVersionString = CompilerUtils.shortString(scalaVersion)
    // initial space here is important
    val optionString = s" -Xsource:$scalaVersionString -Ymacro-expand:none"
    eclipseLog.debug(s"Adding $optionString to compiler arguments of ${this.underlying.getName()} because of: $reason")
    val extraArgs = ScalaPresentationCompiler.defaultScalaSettings().splitParams(storage.getString(CompilerSettings.ADDITIONAL_PARAMS))
    val curatedArgs = extraArgs.filter { s => !s.startsWith("-Xsource") && !s.startsWith("-Ymacro-expand") }
    storage.setValue(CompilerSettings.ADDITIONAL_PARAMS, curatedArgs.mkString(" ") + optionString)
  }

  private def unSetXSourceAndMaybeUntoggleProjectSettings(reason: String) = {
    if (usesProjectSettings) { // if no project-specific settings, Xsource is ineffective anyway
      val extraArgs = ScalaPresentationCompiler.defaultScalaSettings().splitParams(storage.getString(CompilerSettings.ADDITIONAL_PARAMS))

      val (superfluousArgs, curatedArgs) = extraArgs.partition { s => s.startsWith("-Xsource") || s.equals("-Ymacro-expand:none") }
      val superfluousString = superfluousArgs.mkString(" ")
      eclipseLog.debug(s"Removing $superfluousString from compiler arguments of ${this.underlying.getName()} because of: $reason")
      storage.setValue(CompilerSettings.ADDITIONAL_PARAMS, curatedArgs.mkString(" "))

      // values in shownSettings are fetched from currentStorage, which here means projectSpecificSettings
      val projectSettingsSameAsWorkSpace = shownSettings(ScalaPresentationCompiler.defaultScalaSettings(), _ => true) forall {
        case (setting, value) => IScalaPlugin().getPreferenceStore().getString(SettingConverterUtil.convertNameToProperty(setting.name)) == value
      }
      val scalaInstallationIsSameAsDefault = {
        val desiredInstallChoice = desiredinstallationChoice()
        desiredInstallChoice.marker match {
          case Left(scalaVersion) => CompilerUtils.isBinarySame(IScalaPlugin().scalaVersion, scalaVersion)
          case Right(_) => false
        }
      }
      if (projectSettingsSameAsWorkSpace && scalaInstallationIsSameAsDefault) {
        turnOffProjectSpecificSettings("Settings are all identical to workspace after Xsource removal.")
      }
    }
  }

  /** This compares the bundled version and the Xsource version found
  * in arguments, and returns false if they are binary-compatible,
  * and true otherwise.  Since this is the final, observable
  * setting on the running presentation Compiler (independently of
  * Eclipse's settings), it's considered to be the reference on
  * whether the PC is in compatibility mode or not.  It's a bad
  * idea to cache this one (desired sourcelevel & al. need to sync
  * on it).
  */
  private def getCompatibilityMode(): Boolean = {
    val versionInArguments = this.scalacArguments filter { _.startsWith("-Xsource:") } map { _.stripPrefix("-Xsource:")}
    val l = versionInArguments.length
    val specdVersion = versionInArguments.headOption

    if (l >= 2)
      eclipseLog.error(s"Found two versions of -Xsource in compiler options, only considering the first! ($specdVersion)")
    if (specdVersion exists (ScalaVersion(_) > IScalaPlugin().scalaVersion))
      eclipseLog.error(s"Incompatible Xsource setting found in Compiler options: $specdVersion")
    if (l < 1 || (specdVersion exists (x => CompilerUtils.isBinarySame(IScalaPlugin().scalaVersion, ScalaVersion(x)))))
      false
    else
      specdVersion exists (x => CompilerUtils.isBinaryPrevious(IScalaPlugin().scalaVersion, ScalaVersion(x)))
  }

  /** TODO: letting this be a workspace-wide setting.
   */
  def isUsingCompatibilityMode(): Boolean = getCompatibilityMode()

  /** Does this project use project-specific compiler settings? */
  def usesProjectSettings: Boolean =
    projectSpecificStorage.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)

  import org.scalaide.util.internal.eclipse.SWTUtils.fnToPropertyChangeListener
  val compilerSettingsListener: IPropertyChangeListener = { (event: PropertyChangeEvent) =>
    {
      import org.scalaide.util.internal.Utils._
      if (event.getProperty() == SettingConverterUtil.SCALA_DESIRED_INSTALLATION) {
        val installString = (event.getNewValue()).asInstanceOfOpt[String]
        val installChoice = installString flatMap (parseScalaInstallationChoice(_))
        // This can't use the default argument of setDesiredInstallation: getDesiredXXX() ...
        // will not turn on the project settings and depends on them being set right beforehand
        installChoice foreach (setDesiredInstallation(_))
      }
      if (event.getProperty() == CompilerSettings.ADDITIONAL_PARAMS || event.getProperty() == SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE) {
        if (isUnderlyingValid) classpathHasChanged()
      }
    }
  }

}