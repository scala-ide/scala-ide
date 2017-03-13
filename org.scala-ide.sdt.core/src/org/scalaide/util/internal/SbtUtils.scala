package org.scalaide.util.internal

import java.io.File

import sbt.internal.inc.Analysis
import sbt.internal.inc.FileBasedStore
import xsbti._
import xsbti.compile.MiniSetup
import java.util.Optional

object SbtUtils {
  def toOption[S](opt: Maybe[S]): Option[S] = {
    if (opt.isEmpty) None else Some(opt.get)
  }

  def toOption[T](optional: Optional[T]): Option[T] = {
    if (optional.isPresent()) Some(optional.get)
    else None
  }

  def readCache(cacheFile: File): Option[(Analysis, MiniSetup)] =
    FileBasedStore(cacheFile).get().map(_ match {
      case (a: Analysis, i) => (a, i)
      case (a, _) => throw new RuntimeException(s"Expected that sbt analysis for $cacheFile is of type ${classOf[Analysis]} but was ${a.getClass}.")
    })

  def readAnalysis(cacheFile: File): Analysis =
    // zinc requires name hashing to be enabled
    readCache(cacheFile).map(_._1).getOrElse(Analysis.empty(nameHashing = true))

  object NoPosition extends xsbti.Position {
    def line(): Optional[Integer] = Optional.empty()
    def lineContent(): String = ""
    def offset(): Optional[Integer] = Optional.empty()
    def pointer(): Optional[Integer] = Optional.empty()
    def pointerSpace(): Optional[String] = Optional.empty()
    def sourceFile(): Optional[File] = Optional.empty()
    def sourcePath(): Optional[String] = Optional.empty()
  }
}
