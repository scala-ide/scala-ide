package scala.tools.eclipse.testsetup

import java.io._

object FileUtils {

  def read(file: File): Array[Byte] = {
    val len = file.length.toInt
    val bytes = new Array[Byte](len)
    val stream = new java.io.FileInputStream(file)
    var bytesRead = 0
    var lastReadSize = 0

    try {
      while ((lastReadSize != -1) && (bytesRead != len)) {
        lastReadSize = stream.read(bytes, bytesRead, len - bytesRead)
        bytesRead += lastReadSize
      }
    } finally {
      stream.close()
    }
    bytes
  }

  /**
   * Copy file from src (path to the original file) to dest (path to the destination file).
   */
  def copy(src: File, dest: File) {
    val srcBytes = read(src)

    val out = new FileOutputStream(dest)
    try {
      out.write(srcBytes)
    } finally {
      out.close()
    }
  }

  /**
   * Copy the given source directory (and all its contents) to the given target directory.
   */
  def copyDirectory(source: File, target: File) {
    def shouldSkip(name: String) = name match {
      case "CVS" | ".svn" | ".git" => true
      case _ => false
    }

    if (!target.exists) target.mkdirs()

    val files = source.listFiles();
    if (files == null) return;

    for (src <- files; name = src.getName; if !shouldSkip(src.getName)) {
      val targetChild = new File(target, name)
      if (src.isDirectory)
        copyDirectory(src, targetChild)
      else
        copy(src, targetChild)
    }
  }

}