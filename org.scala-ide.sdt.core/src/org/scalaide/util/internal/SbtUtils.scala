package org.scalaide.util.internal

import java.io.File

import sbt.internal.inc.Analysis
import sbt.internal.inc.FileBasedStore
import xsbti._
import xsbti.compile.MiniSetup
import java.util.Optional

object SbtUtils {
  def m2o[S](opt: Maybe[S]): Option[S] = if (opt.isEmpty) None else Some(opt.get)
  def o2m[S](opt: Option[S]): Maybe[S] = if (opt.isEmpty) Maybe.nothing() else Maybe.just(opt.get)
  def jo2m[S](opt: Optional[S]): Maybe[S] = if (opt.isPresent()) Maybe.just(opt.get) else Maybe.nothing()
  def jo2o[S](opt: Optional[S]): Option[S] = if (opt.isPresent()) Some(opt.get) else None
  def o2jo[S](opt: Option[S]): Optional[S] = if (opt.isEmpty) Optional.empty() else Optional.of(opt.get)
  def m2jo[S](opt: Maybe[S]): Optional[S] = if (opt.isEmpty) Optional.empty() else Optional.of(opt.get)

  def readCache(cacheFile: File): Option[(Analysis, MiniSetup)] =
    FileBasedStore(cacheFile).get().map(_ match {
      case (a: Analysis, i) => (a, i)
      case (a, _) => throw new RuntimeException(s"Expected that sbt analysis for $cacheFile is of type ${classOf[Analysis]} but was ${a.getClass}.")
    })

  def readAnalysis(cacheFile: File): Analysis =
    readCache(cacheFile).map(_._1).getOrElse(Analysis.empty)

  object NoPosition extends xsbti.Position {
    def line(): Optional[Integer] = Optional.empty()
    def lineContent(): String = ""
    def offset(): Optional[Integer] = Optional.empty()
    def pointer(): Optional[Integer] = Optional.empty()
    def pointerSpace(): Optional[String] = Optional.empty()
    def sourceFile(): Optional[File] = Optional.empty()
    def sourcePath(): Optional[String] =  Optional.empty()
  }
}
