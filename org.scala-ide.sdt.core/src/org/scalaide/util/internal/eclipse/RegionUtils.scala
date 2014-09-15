package org.scalaide.util.internal.eclipse

import scala.reflect.internal.util.RangePosition
import scala.reflect.internal.util.SourceFile

import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.Region
import org.eclipse.text.edits.TextEdit

object RegionUtils {
  implicit class RichProblem(private val problem: IProblem) extends AnyVal {
    def toRegion: IRegion =
      new Region(problem.getSourceStart(), problem.getSourceEnd() - problem.getSourceStart())

    def length: Int = problem.getSourceEnd() - problem.getSourceStart()
    def start: Int = problem.getSourceStart()
    def end: Int = problem.getSourceEnd()
  }

  implicit class RichRegion(private val region: IRegion) extends AnyVal {
    def intersects(other: IRegion): Boolean =
      !(other.getOffset >= region.getOffset + region.getLength || other.getOffset + other.getLength - 1 < region.getOffset)

    def of(s: Array[Char]): String =
      s.slice(region.getOffset, region.getOffset + region.getLength).mkString

    def of(s: String): String =
      s.slice(region.getOffset, region.getOffset + region.getLength)

    def toRangePos(sourceFile: SourceFile): RangePosition = {
      val offset = region.getOffset()
      new RangePosition(sourceFile, offset, offset, offset + region.getLength())
    }

    def length: Int = region.getLength()
    def start: Int = region.getOffset()
    def end: Int = start+length
  }

  def regionOf(start: Int, end: Int): IRegion =
    new Region(start, end - start)

  implicit class RichSelection(private val sel: ITextSelection) extends AnyVal {
    def length: Int = sel.getLength()
    def start: Int = sel.getOffset()
    def end: Int = start+length
  }

  implicit class RichTextEdit(private val edit: TextEdit) extends AnyVal {
    def length: Int = edit.getLength()
    def start: Int = edit.getOffset()
    def end: Int = start+length
  }

  implicit class RichDocument(private val doc: IDocument) extends AnyVal {
    def length: Int = doc.getLength()
  }
}
