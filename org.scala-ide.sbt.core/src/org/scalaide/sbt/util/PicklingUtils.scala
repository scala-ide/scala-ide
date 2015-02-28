package org.scalaide.sbt.util

import sbt.serialization.pickler.JavaExtraPicklers
import sbt.serialization.pickler.TravPickler
import sbt.protocol.Attributed
import java.io.File
import scala.pickling.Unpickler
import scala.pickling.Pickler

object PicklingUtils extends JavaExtraPicklers {

  implicit val SeqAttPickler: Pickler[Seq[Attributed[File]]] with Unpickler[Seq[Attributed[File]]] =
    TravPickler[Attributed[File], Seq[Attributed[File]]]
}
