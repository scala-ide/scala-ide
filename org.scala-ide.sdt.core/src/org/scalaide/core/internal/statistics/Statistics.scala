package org.scalaide.core.internal.statistics

import java.io.File
import java.io.FileWriter

import scala.collection.JavaConverters._

import org.scalaide.core.ScalaIdeDataStore
import org.scalaide.core.internal.ScalaPlugin

import com.cedarsoftware.util.io.JsonReader
import com.cedarsoftware.util.io.JsonWriter

import Features._

/**
 * Contains all definitions that belong to the statistics tracker.
 */
class Statistics {

  private var firstStat = 0L
  private var cache = Map[Feature, FeatureData]()
  private val jsonArgs = Map[String, AnyRef](JsonWriter.PRETTY_PRINT → "true").asJava

  readStats()

  /** Returns the internal data structure of the statistics tracker. */
  private[scalaide] def data: Seq[FeatureData] = cache.values.toList
  /** Returns the timestamp where statistics tracking has been started. */
  private[scalaide] def startOfStats: Long = firstStat

  /**
   * Increments the usage counter of `feature` by `numToInc`. The usage counter
   * is the number of times the feature has already been used.
   */
  def incUsageCounter(feature: Feature, numToInc: Int = 1): Unit = {
    if (numToInc > 0) {
      val stat = cache.getOrElse(feature, FeatureData(feature, 0, System.currentTimeMillis))
      cache += feature → stat.copy(nrOfUses = stat.nrOfUses + numToInc, lastUsed = System.currentTimeMillis)

      writeStats()
    }
  }

  private def readStats(): Unit = {
    ScalaIdeDataStore.validate(ScalaIdeDataStore.statisticsLocation) { file ⇒
      val stats = read(file)
      firstStat = stats.firstStat
      cache = stats.featureData.map(stat ⇒ stat.feature → stat)(collection.breakOut)
    }
  }

  private def writeStats(): Unit = {
    if (firstStat == 0) firstStat = System.currentTimeMillis
    val stats = StatData(firstStat, cache.map(_._2)(collection.breakOut))

    ScalaIdeDataStore.validate(ScalaIdeDataStore.statisticsLocation) { file ⇒
      write(file, stats)
    }
  }

  private def write(file: File, value: StatData): Unit = {
    val json = JsonWriter.objectToJson(value, jsonArgs)
    new FileWriter(file).append(json).close()
  }

  private def read(file: File): StatData = {
    val json = io.Source.fromFile(file).mkString
    JsonReader.jsonToJava(json).asInstanceOf[StatData]
  }
}

object Groups {
  /** Specifies to which group a [[Features.Feature]] belongs to. */
  abstract class Group(val description: String)
  object Miscellaneous extends Group("Miscellaneous")
  object QuickAssist extends Group("Quick Assist")
  object Refactoring extends Group("Refactoring")
  object Editing extends Group("Editing")
  object SaveAction extends Group("Save Action")
  object AutoEdit extends Group("Auto Edit")
  object Wizard extends Group("Wizard")
}

object Features {
  import Groups._

  /**
   * Every feature of the IDE that should be tracked by the statistics tracker
   * needs to be represented by this class. `id` is a unique identifier for the
   * feature, `description` is a short text that can be shown to users to
   * explain what exactly a feature is doing and `group` specifies to which
   * group a feature belongs.
   */
  case class Feature(id: String)(val description: String, val group: Group) {

    /**
     * Increments the usage counter of this feature by `numToInc`. The usage
     * counter is the number of times the feature has already been used.
     */
    def incUsageCounter(numToInc: Int = 1): Unit =
      ScalaPlugin().statistics.incUsageCounter(this, numToInc)
  }

  object ExplicitReturnType extends Feature("ExplicitReturnType")("Add explicit return type", QuickAssist)
  object InlineLocalValue extends Feature("InlineLocalValue")("Inline local value", QuickAssist)
  object ExpandCaseClassBinding extends Feature("ExpandCaseClassBinding")("Expand case class binding", QuickAssist)
  object ExpandImplicitConversion extends Feature("ExpandImplicitConversion")("Expand implicit conversion", QuickAssist)
  object ExpandImplicitArgument extends Feature("ExpandImplicitArgument")("Expand implicit argument", QuickAssist)
  object FixTypeMismatch extends Feature("FixTypeMismatch")("Fix type mismatch", QuickAssist)
  object ImportMissingMember extends Feature("ImportMissingMember")("Import missing member", QuickAssist)
  object CreateClass extends Feature("CreateClass")("Create class", QuickAssist)
  object FixSpellingMistake extends Feature("FixSpellingMistake")("Fix spelling mistake", QuickAssist)
  object CreateMethod extends Feature("CreateMethod")("Create method", QuickAssist)
  object ExtractCode extends Feature("ExtractCode")("Extract code", QuickAssist)
  object CopyQualifiedName extends Feature("CopyQualifiedName")("Copy qualified name", Miscellaneous)
  object RestartPresentationCompiler extends Feature("RestartPresentationCompiler")("Restart Presentation Compiler", Miscellaneous)
  /** Exists for backward compatibility with previous versions of the IDE. */
  object NotSpecified extends Feature("NotSpecified")("<not specified>", Miscellaneous)
  object CodeAssist extends Feature("CodeAssist")("Code completion", Editing)
  object CharactersSaved extends Feature("CharactersSaved")("Number of typed characters saved thanks to code completion", Editing)
  object OrganizeImports extends Feature("OrganizeImports")("Organize imports", Refactoring)
  object ExtractMemberToTrait extends Feature("ExtractMemberToTrait")("Extract member to trait", Refactoring)
  object MoveConstructorToCompanion extends Feature("MoveConstructorToCompanion")("Move constructor to companion object", Refactoring)
  object GenerateHashcodeAndEquals extends Feature("GenerateHashcodeAndEquals")("Generate hashCode and equals method", Refactoring)
  object IntroduceProductNTrait extends Feature("IntroduceProductNTrait")("Introduce ProductN trait", Refactoring)
  object LocalRename extends Feature("LocalRename")("Rename local value", Refactoring)
  object GlobalRename extends Feature("GlobalRename")("Rename global value", Refactoring)
  object MoveClass extends Feature("MoveClass")("Move class/object/trait", Refactoring)
  object SplitParameterLists extends Feature("SplitParameterLists")("Split parameter lists", Refactoring)
  object MergeParameterLists extends Feature("MergeParameterLists")("Merge parameter lists", Refactoring)
  object ChangeParameterOrder extends Feature("ChangeParameterOrder")("Change parameter order", Refactoring)
  object AutoClosingComments extends Feature("AutoClosingComments")("Automatically close multi line comments and Scaladoc", Editing)
  object AutoEscapeLiterals extends Feature("AutoEscapeLiterals")("Automatically escape \" signs in string literals", Editing)
  object AutoEscapeBackslashes extends Feature("AutoEscapeBackslashes")("Automatically escape \\ signs in string and character literals", Editing)
  object AutoRemoveEscapedSign extends Feature("AutoRemoveEscapedSign")("Automatically remove complete escaped sign in string and character literals", Editing)
  object AutoBreakComments extends Feature("AutoBreakComments")("Automatically break multi-line comments and Scaladoc after the Print Margin", Editing)
  object AutoIndentOnTab extends Feature("AutoIndentOnTab")("Automatically indent when tab is pressed", Editing)
  object AutoIndentMultiLineStrings extends Feature("AutoIndentMultiLineStrings")("Automatically indent in multi line string literals", Editing)
  object AutoAddStripMargin extends Feature("AutoAddStripMargin")("Automatically add strip margins when multi line string starts with a |", Editing)
}

private[scalaide] final case class StatData(firstStat: Long, featureData: Array[FeatureData])
private[scalaide] final case class FeatureData(feature: Feature, nrOfUses: Int, lastUsed: Long)
