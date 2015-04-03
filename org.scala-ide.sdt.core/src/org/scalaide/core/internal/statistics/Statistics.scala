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
import Features._

object Statistics {

  val IdeConfigStore = new File(System.getProperty("user.home") + File.separator + ".scalaide")

  val StatisticsFile = new File(IdeConfigStore.getAbsolutePath + File.separator + "statistics")

}

class Statistics {
  import Statistics._
  import scala.pickling.Defaults._
  import scala.pickling.json._

  private var cache = Map[Feature, Stat]()

  readStats()

  def data: Seq[Stat] = cache.values.toList

  def incUses(feature: Feature): Unit = {
    val stat = cache.get(feature).getOrElse(Stat(feature, 0, System.nanoTime))
    cache += feature → stat.copy(nrOfUses = stat.nrOfUses + 1, lastUsed = System.nanoTime)

    writeStats()
  }

  private def readStats(): Unit = {
    EclipseUtils.withSafeRunner("Error while reading statistics from disk") {
      import Picklers._
      if (StatisticsFile.exists())
        cache = read[Seq[Stat]](StatisticsFile).map(stat ⇒ stat.feature → stat)(collection.breakOut)
      else
        Seq()
    }
  }

  private def writeStats(): Unit = {
    val values: Seq[Stat] = cache.map(_._2)(collection.breakOut)

    EclipseUtils.withSafeRunner("Error while writing statistics to disk") {
      import Picklers._
      if (!StatisticsFile.exists()) {
        IdeConfigStore.mkdirs()
        StatisticsFile.createNewFile()
      }
      write(StatisticsFile, values)
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
  object CodeAssist extends Group("Code Assist")
  object SaveAction extends Group("Save Action")
  object AutoEdit extends Group("Auto Edit")
  object Wizard extends Group("Wizard")
}

object Features {
  import Groups._

  sealed abstract class Feature(val description: String, val group: Group)
  object ExplicitReturnType extends Feature("Add explicit return type", QuickAssist)
  object CopyQualifiedName extends Feature("Copy qualified name", Uncategorized)
  object RestartPresentationCompiler extends Feature("Restart Presentation Compiler", Uncategorized)
}

case class Stat(feature: Feature, nrOfUses: Int, lastUsed: Long)

private object Picklers {
  object fqn {
    val fExplicitReturnType = FastTypeTag[ExplicitReturnType.type].key
    val fCopyQualifiedName = FastTypeTag[CopyQualifiedName.type].key
    val fRestartPresentationCompiler = FastTypeTag[RestartPresentationCompiler.type].key

    def asString(c: Feature): String = c match {
      case ExplicitReturnType ⇒ fExplicitReturnType
      case CopyQualifiedName ⇒ fCopyQualifiedName
      case RestartPresentationCompiler ⇒ fRestartPresentationCompiler
    }

    def fromString(s: String): Feature = s match {
      case `fExplicitReturnType` ⇒ ExplicitReturnType
      case `fCopyQualifiedName` ⇒ CopyQualifiedName
      case `fRestartPresentationCompiler` ⇒ RestartPresentationCompiler
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
  implicit val statPickler: Pickler[Stat] with Unpickler[Stat] = new Pickler[Stat] with Unpickler[Stat] {
    override val tag = FastTypeTag[Stat]

    override def pickle(s: Stat, b: PBuilder): Unit = {
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
      Stat(feature, nrOfUses, lastUsed)
    }

  }
}
