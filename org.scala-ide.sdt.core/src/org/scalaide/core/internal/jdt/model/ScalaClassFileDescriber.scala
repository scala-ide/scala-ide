package org.scalaide.core.internal.jdt.model

import java.io.InputStream

import scala.io.Source

import org.eclipse.core.runtime.QualifiedName
import org.eclipse.core.runtime.content.IContentDescriber
import org.eclipse.core.runtime.content.IContentDescription
import org.scalaide.logging.HasLogger

object ScalaClassFileDescriber extends HasLogger {
  import scala.util.Try
  def isScala(contents: Array[Byte]): Option[String] = Try {
    import scala.tools.scalap.{ ByteArrayReader, Classfile }
    new Classfile(new ByteArrayReader(contents))
  }.toOption.flatMap { classFile =>
    import classFile._
    def scalaAttribute = classFile.attribs.find { _.toString == "Scala" }
    (classFile.scalaSigAttribute orElse scalaAttribute).flatMap { _ =>
      classFile.attribs.collectFirst {
        case atr @ Attribute(_, data) if atr.toString == "SourceFile" =>
          data
      }
    }.flatMap { data =>
      data.map { d => pool(d) }.collectFirst {
        case pool.UTF8(sourcePath) =>
          sourcePath
      }
    }
  }
}

class ScalaClassFileDescriber extends IContentDescriber {
  import ScalaClassFileDescriber._
  import org.eclipse.core.runtime.content.IContentDescriber.INVALID
  import org.eclipse.core.runtime.content.IContentDescriber.VALID

  override def describe(contents: InputStream, description: IContentDescription): Int =
    isScala(Source.fromInputStream(contents).map(_.toByte).toArray).map(_ => VALID).getOrElse(INVALID)

  override def getSupportedOptions: Array[QualifiedName] = new Array[QualifiedName](0)
}
