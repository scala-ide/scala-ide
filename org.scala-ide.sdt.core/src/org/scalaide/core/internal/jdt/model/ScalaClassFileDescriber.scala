package org.scalaide.core.internal.jdt.model

import java.io.InputStream

import scala.io.Source

import org.eclipse.core.runtime.QualifiedName
import org.eclipse.core.runtime.content.IContentDescriber
import org.eclipse.core.runtime.content.IContentDescription
import org.scalaide.logging.HasLogger

object ScalaClassFileDescriber extends HasLogger {

  def isScala(contents: Array[Byte]): Option[String] = {
    import scala.tools.scalap.{ ByteArrayReader, Classfile }
    val classFile = new Classfile(new ByteArrayReader(contents))
    import classFile._
    def scalaAttribute = classFile.attribs.find { _.toString == "Scala" }
    (classFile.scalaSigAttribute orElse scalaAttribute).flatMap { _ =>
      classFile.attribs.collectFirst {
        case atr @ Attribute(_, data) if atr.toString == "SourceFile" =>
          data.map { d => pool(d) }.collectFirst {
            case pool.UTF8(s) =>
              s
          }
      }.flatten
    }
  }

}

class ScalaClassFileDescriber extends IContentDescriber {
  import ScalaClassFileDescriber._
  import org.eclipse.core.runtime.content.IContentDescriber.INVALID
  import org.eclipse.core.runtime.content.IContentDescriber.VALID

  override def describe(contents : InputStream, description : IContentDescription) : Int =
    if (isScala(Source.fromInputStream(contents).map(_.toByte).toArray).isDefined) VALID else INVALID

  override def getSupportedOptions : Array[QualifiedName] = new Array[QualifiedName](0)
}
