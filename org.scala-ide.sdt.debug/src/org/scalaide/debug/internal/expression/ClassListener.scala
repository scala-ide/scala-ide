/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import java.io.File
import scala.io.Source

/** Listens for new classes on request. To work options must be passed to toolbox that is used. */
object ClassListener {

  // tmp dir for compiled classes
  private lazy val dir = {
    val tempFile = File.createTempFile("tmp_classes", "")
    tempFile.delete()
    tempFile.mkdirs()
    tempFile.getAbsolutePath
  }

  /** Pass this to toolbox as arguments to enable listening for new classes. */
  def options = s"-d $dir"

  case class NewClassContext(newClassName: String, newClassCode: Array[Byte])

  /**
   * Listens for new class created during call of given function
   * Check output dir of toolbox for new directory and search there class file contains given string
   * synchronized method
   *
   * @param className part of class name that should define required class
   * @param function function - during call there should be create new class
   * @return NewClassContext
   */
  def listenForClasses(className: String)(compile: () => Any): NewClassContext = synchronized {
    val parentDirFile = new File(dir)

    def findNewClassDirectory() = {
      val filesBeforeCompilation = parentDirFile.list().toSet
      compile()
      val filesAfterCompilation = parentDirFile.list().toSet
      val Seq(newClassDir) = (filesAfterCompilation diff filesBeforeCompilation).toSeq
      newClassDir
    }

    def findNewClassFile(newClassDir: String) = {
      val Array(requiredClassFile) = new File(parentDirFile, newClassDir).listFiles()
        .filter(_.getName.contains("$" + className))
      requiredClassFile
    }

    def newClassName(newClassDir: String, classFile: File) = {
      val generatedClassName = classFile.getName.replace(".class", "")
      s"$newClassDir.$generatedClassName"
    }

    def newClassBytes(classFile: File) = {
      val codec = "ISO-8859-1"
      Source.fromFile(classFile, codec).map(_.toByte).toArray
    }

    val newClassDir = findNewClassDirectory()
    val requiredClassFile = findNewClassFile(newClassDir)

    NewClassContext(
      newClassName = newClassName(newClassDir, requiredClassFile),
      newClassCode = newClassBytes(requiredClassFile))
  }

}
