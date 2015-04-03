package org.scalaide.core.internal.statistics

import java.io.File
import java.io.FileWriter

import scala.pickling.FastTypeTag
import scala.pickling.PBuilder
import scala.pickling.PReader
import scala.pickling.Pickler
import scala.pickling.Unpickler
import scala.pickling.pickler.AllPicklers

import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.core.internal.statistics.Features.CopyQualifiedName
import org.scalaide.core.internal.statistics.Features.ExplicitReturnType
import org.scalaide.core.ScalaIdeDataStore
import Features._

class Statistics {
  import scala.pickling.Defaults._
  import scala.pickling.json._

  private var firstStat = 0L
  private var cache = Map[Feature, FeatureData]()

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
      import Picklers._
      val stats = read[StatData](file)
      firstStat = stats.firstStat
      cache = stats.featureData.map(stat ⇒ stat.feature → stat)(collection.breakOut)
    }
  }

  private def writeStats(): Unit = {
    if (firstStat == 0) firstStat = System.currentTimeMillis
    val stats = StatData(firstStat, cache.map(_._2)(collection.breakOut))

    ScalaIdeDataStore.write(ScalaIdeDataStore.statisticsLocation) { file ⇒
      import Picklers._
      write(file, stats)
    }
  }

  private def write[A : Pickler : Unpickler](file: File, value: A): Unit = {
    val pickled = value.pickle
    val json = pickled.value

    new FileWriter(file).append(json).close()
  }

  private def read[A : Pickler : Unpickler](file: File): A = {
    val readJson = io.Source.fromFile(file).mkString
    val readPickled = pickling.json.JSONPickle(readJson)
    readPickled.unpickle[A]
  }
}

object Groups {
  sealed abstract class Group(val description: String)
  object Uncategorized extends Group("Uncategorized")
  object QuickAssist extends Group("Quick Assist")
  object Refactoring extends Group("Refactoring")
  object Editing extends Group("Editing")
  object SaveAction extends Group("Save Action")
  object AutoEdit extends Group("Auto Edit")
  object Wizard extends Group("Wizard")
}

object Features {
  import Groups._

  sealed abstract class Feature(val description: String, val group: Group)
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
  object CopyQualifiedName extends Feature("Copy qualified name", Uncategorized)
  object RestartPresentationCompiler extends Feature("Restart Presentation Compiler", Uncategorized)
  /** Exists for backward compatibility with previous versions of the IDE. */
  object NotSpecified extends Feature("<not specified>", Uncategorized)
  object CodeAssist extends Feature("Code completion", Editing)
  object CharactersSaved extends Feature("Number of typed characters saved thanks to code completion", Editing)
}

final case class StatData(firstStat: Long, featureData: Seq[FeatureData])
final case class FeatureData(feature: Feature, nrOfUses: Int, lastUsed: Long)

private object Picklers {
  object fqn {
    val fExplicitReturnType = FastTypeTag[ExplicitReturnType.type].key
    val fInlineLocalValue = FastTypeTag[InlineLocalValue.type].key
    val fExpandCaseClassBinding = FastTypeTag[ExpandCaseClassBinding.type].key
    val fExpandImplicitConversion = FastTypeTag[ExpandImplicitConversion.type].key
    val fExpandImplicitArgument = FastTypeTag[ExpandImplicitArgument.type].key
    val fFixTypeMismatch = FastTypeTag[FixTypeMismatch.type].key
    val fImportMissingMember = FastTypeTag[ImportMissingMember.type].key
    val fCreateClass = FastTypeTag[CreateClass.type].key
    val fFixSpellingMistake = FastTypeTag[FixSpellingMistake.type].key
    val fCreateMethod = FastTypeTag[CreateMethod.type].key
    val fExtractCode = FastTypeTag[ExtractCode.type].key
    val fCopyQualifiedName = FastTypeTag[CopyQualifiedName.type].key
    val fRestartPresentationCompiler = FastTypeTag[RestartPresentationCompiler.type].key
    val fNotSpecified = FastTypeTag[NotSpecified.type].key
    val fCodeAssist = FastTypeTag[CodeAssist.type].key
    val fCharactersSaved = FastTypeTag[CharactersSaved.type].key

    def asString(c: Feature): String = c match {
      case ExplicitReturnType          ⇒ fExplicitReturnType
      case InlineLocalValue            ⇒ fInlineLocalValue
      case ExpandCaseClassBinding      ⇒ fExpandCaseClassBinding
      case ExpandImplicitConversion    ⇒ fExpandImplicitConversion
      case ExpandImplicitArgument      ⇒ fExpandImplicitArgument
      case FixTypeMismatch             ⇒ fFixTypeMismatch
      case ImportMissingMember         ⇒ fImportMissingMember
      case CreateClass                 ⇒ fCreateClass
      case FixSpellingMistake          ⇒ fFixSpellingMistake
      case CreateMethod                ⇒ fCreateMethod
      case ExtractCode                 ⇒ fExtractCode
      case CopyQualifiedName           ⇒ fCopyQualifiedName
      case RestartPresentationCompiler ⇒ fRestartPresentationCompiler
      case NotSpecified                ⇒ fNotSpecified
      case CodeAssist                  ⇒ fCodeAssist
      case CharactersSaved             ⇒ fCharactersSaved
    }

    def fromString(s: String): Feature = s match {
      case `fExplicitReturnType`          ⇒ ExplicitReturnType
      case `fInlineLocalValue`            ⇒ InlineLocalValue
      case `fExpandCaseClassBinding`      ⇒ ExpandCaseClassBinding
      case `fExpandImplicitConversion`    ⇒ ExpandImplicitConversion
      case `fExpandImplicitArgument`      ⇒ ExpandImplicitArgument
      case `fFixTypeMismatch`             ⇒ FixTypeMismatch
      case `fImportMissingMember`         ⇒ ImportMissingMember
      case `fCreateClass`                 ⇒ CreateClass
      case `fFixSpellingMistake`          ⇒ FixSpellingMistake
      case `fCreateMethod`                ⇒ CreateMethod
      case `fExtractCode`                 ⇒ ExtractCode
      case `fCopyQualifiedName`           ⇒ CopyQualifiedName
      case `fRestartPresentationCompiler` ⇒ RestartPresentationCompiler
      case `fNotSpecified`                ⇒ NotSpecified
      case `fCodeAssist`                  ⇒ CodeAssist
      case `fCharactersSaved`             ⇒ CharactersSaved
    }
  }

  implicit class RichPReader(private val reader: PReader) extends AnyVal {
    def readTypedField[A](name: String, reader: PReader ⇒ A): A =
      reader(this.reader.readField(name))
  }

  /*
   * scala-pickling should actually generate the implementation of this pickler
   * but because its macros fall flat on their faces while doing this the
   * implementation was written manually. One related issue is at least SI-7588.
   */
  implicit val statPickler: Pickler[FeatureData] with Unpickler[FeatureData] = new Pickler[FeatureData] with Unpickler[FeatureData] {
    override val tag = FastTypeTag[FeatureData]

    override def pickle(s: FeatureData, b: PBuilder): Unit = {
      b.pushHints()
      b.hintTag(tag)

      b.beginEntry(s)
      b.putField("feature", b ⇒ {
        b.pushHints()
        b.hintTag(FastTypeTag.String)
        b.hintStaticallyElidedType()
        AllPicklers.stringPickler.pickle(fqn.asString(s.feature), b)
        b.popHints()
      })
      b.putField("nrOfUses", b ⇒ {
        b.pushHints()
        b.hintTag(FastTypeTag.Int)
        b.hintStaticallyElidedType()
        AllPicklers.intPickler.pickle(s.nrOfUses, b)
        b.popHints()
      })
      b.putField("lastUsed", b ⇒ {
        b.pushHints()
        b.hintTag(FastTypeTag.Long)
        b.hintStaticallyElidedType()
        AllPicklers.longPickler.pickle(s.lastUsed, b)
        b.popHints()
      })
      b.endEntry()

      b.popHints()
    }

    override def unpickle(tag: String, r: PReader): Any = {
      r.pushHints()
      r.hintTag(this.tag)
      r.beginEntry()

      val feature = r.readTypedField("feature", r ⇒ {
        r.pushHints()
        r.hintTag(FastTypeTag.String)
        r.hintStaticallyElidedType()
        val s = AllPicklers.stringPickler.unpickleEntry(r).asInstanceOf[String]
        r.popHints()
        fqn.fromString(s)
      })
      val nrOfUses = r.readTypedField("nrOfUses", r ⇒ {
        r.pushHints()
        r.hintTag(FastTypeTag.Int)
        r.hintStaticallyElidedType()
        val i = AllPicklers.intPickler.unpickleEntry(r).asInstanceOf[Int]
        r.popHints()
        i
      })
      val lastUsed = r.readTypedField("lastUsed", r ⇒ {
        r.pushHints()
        r.hintTag(FastTypeTag.Long)
        r.hintStaticallyElidedType()
        val l = AllPicklers.longPickler.unpickleEntry(r).asInstanceOf[Long]
        r.popHints()
        l
      })

      r.endEntry()
      r.popHints()
      FeatureData(feature, nrOfUses, lastUsed)
    }

  }
}
