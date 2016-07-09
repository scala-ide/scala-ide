package org.scalaide.util.internal

import xsbti._
import java.io.File
import sbt.internal.inc.Analysis
import sbt.internal.inc.FileBasedStore
import xsbti.compile.MiniSetup
import xsbti.compile.IncOptions

object SbtUtils {
  def m2o[S](opt: Maybe[S]): Option[S] = if (opt.isEmpty) None else Some(opt.get)

  def readCache(cacheFile: File): Option[(Analysis, MiniSetup)] =
    FileBasedStore(cacheFile).get().map(_ match {
      case (a: Analysis, i) => (a, i)
      case (a, _) => throw new RuntimeException(s"Expected that sbt analysis for $cacheFile is of type ${classOf[Analysis]} but was ${a.getClass}.")
    })

  def readAnalysis(cacheFile: File, incOptions: => IncOptions): Analysis =
    readCache(cacheFile).map(_._1).getOrElse(Analysis.empty(nameHashing = incOptions.nameHashing()))

  object NoPosition extends xsbti.Position {
    def line(): Maybe[Integer] = Maybe.nothing()
    def lineContent(): String = ""
    def offset(): Maybe[Integer] = Maybe.nothing[Integer]
    def pointer(): Maybe[Integer] = Maybe.nothing[Integer]
    def pointerSpace(): Maybe[String] = Maybe.nothing[String]
    def sourceFile(): Maybe[File] = Maybe.nothing[File]
    def sourcePath(): Maybe[String] = Maybe.nothing[String]

  }
}
