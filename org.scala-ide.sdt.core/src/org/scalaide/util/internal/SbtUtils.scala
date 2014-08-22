package org.scalaide.util.internal

import xsbti._
import java.io.File

object SbtUtils {
  def m2o[S](opt: Maybe[S]): Option[S] = if (opt.isEmpty) None else Some(opt.get)

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