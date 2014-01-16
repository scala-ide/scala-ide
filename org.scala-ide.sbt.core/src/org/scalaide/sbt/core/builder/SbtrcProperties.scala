package org.scalaide.sbt.core.builder

import java.io.File
import java.io.OutputStreamWriter
import java.io.FileOutputStream

object SbtrcProperties {

  /** Generate the content of the sbtrc.properties file */
  def content(version: String, resources: List[String]) = s"""
[scala]
  version: auto

[app]
  org: org.scala-sbt
  name: sbt
  version: $version
  class: sbt.xMain
  components: xsbti,extra
  cross-versioned: false
  resources: ${resources.mkString(",")}

[repositories]
  local
  typesafe-ivy-releases: http://repo.typesafe.com/typesafe/ivy-releases/, [organization]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext], bootOnly
  typesafe-ivy-snapshots: http://repo.typesafe.com/typesafe/ivy-snapshots/, [organization]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext], bootOnly
  maven-central
"""

  /** Create a sbtrc.properties file with the right content, and return its location */
  def generateFile(version: String, resources: List[String]): File = {

    val file = File.createTempFile("sbtrc", ".properties")

    val writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")
    try {
      writer.write(content(version, resources))
      writer.flush()
    } finally {
      writer.close()
    }

    file
  }

}