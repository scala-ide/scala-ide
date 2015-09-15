package org.scalaide.core.internal.statistics

import java.io.File
import java.io.FileWriter

import scala.collection.JavaConverters._

import org.scalaide.core.ScalaIdeDataStore

import com.cedarsoftware.util.io.JsonReader
import com.cedarsoftware.util.io.JsonWriter

import Features._

class Statistics {

  private var firstStat = 0L
  private var cache = Map[Feature, FeatureData]()
  private val jsonArgs = Map[String, AnyRef](JsonWriter.PRETTY_PRINT → "true").asJava

  readStats()

  def data: Seq[FeatureData] = cache.values.toList
  def startOfStats: Long = firstStat

  def incUses(feature: Feature, numToInc: Int = 1): Unit = {
    val stat = cache.get(feature).getOrElse(FeatureData(feature, 0, System.currentTimeMillis))
    cache += feature → stat.copy(nrOfUses = stat.nrOfUses + numToInc, lastUsed = System.currentTimeMillis)

    writeStats()
  }

  private def readStats(): Unit = {
    ScalaIdeDataStore.read(ScalaIdeDataStore.statisticsLocation) { file ⇒
      val stats = read(file)
      firstStat = stats.firstStat
      cache = stats.featureData.map(stat ⇒ stat.feature → stat)(collection.breakOut)
    }
  }

  private def writeStats(): Unit = {
    if (firstStat == 0) firstStat = System.currentTimeMillis
    val stats = StatData(firstStat, cache.map(_._2)(collection.breakOut))

    ScalaIdeDataStore.write(ScalaIdeDataStore.statisticsLocation) { file ⇒
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
  sealed abstract class Group(val description: String)
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

  abstract class Feature(val description: String, val group: Group)
  object ExplicitReturnType extends Feature("Add explicit return type", QuickAssist)
  object InlineLocalValue extends Feature("Inline local value", QuickAssist)
  object ExpandCaseClassBinding extends Feature("Expand case class binding", QuickAssist)
  object ExpandImplicitConversion extends Feature("Expand implicit conversion", QuickAssist)
  object ExpandImplicitArgument extends Feature("Expand implicit argument", QuickAssist)
  object FixTypeMismatch extends Feature("Fix type mismatch", QuickAssist)
  object ImportMissingMember extends Feature("Import missing member", QuickAssist)
  object CreateClass extends Feature("Create class", QuickAssist)
  object FixSpellingMistake extends Feature("Fix spelling mistake", QuickAssist)
  object CreateMethod extends Feature("Create method", QuickAssist)
  object ExtractCode extends Feature("Extract code", QuickAssist)
  object CopyQualifiedName extends Feature("Copy qualified name", Miscellaneous)
  object RestartPresentationCompiler extends Feature("Restart Presentation Compiler", Miscellaneous)
  /** Exists for backward compatibility with previous versions of the IDE. */
  object NotSpecified extends Feature("<not specified>", Miscellaneous)
  object CodeAssist extends Feature("Code completion", Editing)
  object CharactersSaved extends Feature("Number of typed characters saved thanks to code completion", Editing)
  object OrganizeImports extends Feature("Organize imports", Refactoring)
  object ExtractMemberToTrait extends Feature("Extract member to trait", Refactoring)
  object MoveConstructorToCompanion extends Feature("Move constructor to companion object", Refactoring)
  object GenerateHashcodeAndEquals extends Feature("Generate hashCode and equals method", Refactoring)
  object IntroduceProductNTrait extends Feature("Introduce ProductN trait", Refactoring)
  object LocalRename extends Feature("Rename local value", Refactoring)
  object GlobalRename extends Feature("Rename global value", Refactoring)
  object MoveClass extends Feature("Move class/object/trait", Refactoring)
  object SplitParameterLists extends Feature("Split parameter lists", Refactoring)
  object MergeParameterLists extends Feature("Merge parameter lists", Refactoring)
  object ChangeParameterOrder extends Feature("Change parameter order", Refactoring)
}

final case class StatData(firstStat: Long, featureData: Array[FeatureData])
final case class FeatureData(feature: Feature, nrOfUses: Int, lastUsed: Long)
