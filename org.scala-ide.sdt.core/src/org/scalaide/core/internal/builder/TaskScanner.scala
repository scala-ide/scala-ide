package org.scalaide.core.internal.builder

import java.{ util => ju }
import scala.collection.mutable.ArrayBuffer
import org.eclipse.jdt.core.JavaCore
import scala.reflect.internal.util.Position
import scala.reflect.internal.util.RangePosition
import org.scalaide.core.internal.project.ScalaProject

class TaskScanner(project : ScalaProject) {
  import TaskScanner._

  def getJavaOptions(key: String): Array[String] = {
    val options = project.javaProject.getOptions(true).asInstanceOf[ju.Map[String, String]]
    options.get(key) match {
      case "" => Array()
      case o => o.split(",").map(_.trim)
    }
  }

  lazy val taskTags = getJavaOptions(JavaCore.COMPILER_TASK_TAGS)
  lazy val tagPriority = {
    val taskPriorities = getJavaOptions(JavaCore.COMPILER_TASK_PRIORITIES)
    Map() ++ (taskTags zip taskPriorities)
  }

  def extractTasks(comment : String, pos : Position) : List[Task] = {
    def extractTasksFromLine(line : String, offset : Int) : List[Task] = {
      val tags = new ArrayBuffer[(Int, String)]
      for (tag <- taskTags) {
        var i = 0
        val tagLen = tag.length
        val limit = line.length-tagLen
        val checkStart = Character.isJavaIdentifierPart(tag.charAt(0))
        val checkEnd = Character.isJavaIdentifierPart(tag.charAt(tagLen-1))

        while (i >= 0 && i < limit) {
          i = line.indexOf(tag, i)
          if (i >= 0) {
            if ((!checkStart || i == 0 || !Character.isJavaIdentifierPart(line(i-1))) &&
                (!checkEnd || i+tagLen >= line.length || !Character.isJavaIdentifierPart(line(i+tagLen))))
              tags += ((i, tag))

            i += tagLen
          }
        }
      }

      val orderedTags = tags.sortBy(_._1)

      val starts = orderedTags.map(t => t._1+t._2.length)
      val ends = orderedTags.drop(1).map(_._1) += line.length
      val msgs = (starts zip ends).map({ case (start, end) => (start, line.substring(start, end).trim)}).filter(_._2.length != 0)

      (for((start, tag) <- orderedTags) yield {
        val (point, msg) = msgs.find(_._1 > start).getOrElse((start+tag.length, ""))
        Task(tag, msg, tagPriority(tag), new RangePosition(pos.source, offset+start, offset+point, offset+point+msg.length))
      }).toList
    }

    val body = if (comment.startsWith("/*")) comment.substring(0, comment.length-2) else comment
    val lines = new ArrayBuffer[(Int, String)]
    var i = 0
    var prev = 0
    val limit = body.length
    while (i <= limit) {
      val ch = if (i < limit) body(i) else '\n'
      if (ch == '\n' || ch == '\r') {
        if (i > prev) {
          val line = body.substring(prev, i)
          if (line.length > 0)
            lines += ((prev, line))
        }
        prev = i+1
      }
      i += 1
    }

    lines.flatMap({ case (offset, line) => extractTasksFromLine(line, pos.start+offset)}).toList
  }
}

object TaskScanner {
  case class Task(tag : String, msg : String, priority : String, pos : Position)
}
