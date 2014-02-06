package org.scalaide.core.compiler

import scala.collection.mutable.ArrayBuffer
import scala.tools.nsc.ast.parser.Tokens
import scala.reflect.internal.util.SourceFile
import scala.collection.immutable

/** Additional compiler APIs. It should eventually migrate in the presentation compiler.
 */
trait CompilerApiExtensions { this: ScalaPresentationCompiler =>

  /** Locate the smallest tree that encloses position.
   *
   *  @param tree The tree in which to search `pos`
   *  @param pos  The position to look for
   *  @param p    An additional condition to be satisfied by the resulting tree
   *  @return     The innermost enclosing tree for which p is true, or `EmptyTree`
   *              if the position could not be found.
   */
  def locateIn(tree: Tree, pos: Position, p: Tree => Boolean = t => true): Tree =
    new FilteringLocator(pos, p) locateIn tree

  private class FilteringLocator(pos: Position, p: Tree => Boolean) extends Locator(pos) {
    override def isEligible(t: Tree) = super.isEligible(t) && p(t)
  }

  /** Return the smallest enclosing method.
   *
   *  @return a parse tree of the innermost enclosing method.
   *  @note   This method parses the whole sourcefile
   */
  def enclosingMethd(src: SourceFile, offset: Int): Tree = {
    locateIn(parseTree(src), rangePos(src, offset, offset, offset), t => t.isInstanceOf[DefDef])
  }

  /** Returns the smallest enclosing class or trait.
   *
   *  @return a parse tree of the innermost enclosing method.
   *  @note   This method parses the whole sourcefile
   */
  def enclosingClass(src: SourceFile, offset: Int) = {
    locateIn(parseTree(src), rangePos(src, offset, offset, offset), _.isInstanceOf[ClassDef])
  }

  /** Return the smallest enclosing method of value definition
   *
   *  @return a parse tree of the innermost enclosing {{{DefDef}}} or {{{ValDef}}}
   *  @note   This method parses the whole sourcefile
   */
  def enclosingValOrDef(src: SourceFile, offset: Int): Tree = {
    locateIn(parseTree(src), rangePos(src, offset, offset, offset), t => t.isInstanceOf[ValOrDefDef])
  }

  /** A helper class to access the lexical tokens of `source`.
   *
   *  Once constructed, instances of this class are thread-safe.
   */
  class LexicalStructure(source: SourceFile) {
    private val token = new ArrayBuffer[Int]
    private val startOffset = new ArrayBuffer[Int]
    private val endOffset = new ArrayBuffer[Int]
    private val scanner = new syntaxAnalyzer.UnitScanner(new CompilationUnit(source))
    scanner.init()

    while (scanner.token != Tokens.EOF) {
      startOffset += scanner.offset
      token += scanner.token
      scanner.nextToken
      endOffset += scanner.lastOffset
    }

    /** Return the index of the token that covers `offset`.
     */
    private def locateIndex(offset: Int): Int = {
      var lo = 0
      var hi = token.length - 1
      while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (startOffset(mid) <= offset) lo = mid
        else hi = mid - 1
      }
      lo
    }

    /** Return all tokens between start and end offsets.
     *
     *  The first token may start before `start` and the last token may span after `end`.
     */
    def tokensBetween(start: Int, end: Int): immutable.Seq[Token] = {
      val startIndex = locateIndex(start)
      val endIndex = locateIndex(end)

      val tmp = for (i <- startIndex to endIndex)
        yield Token(token(i), startOffset(i), endOffset(i))

      tmp.toSeq
    }
  }
}

/** A Scala token covering [start, end)
 *
 *  @param tokenId one of scala.tools.nsc.ast.parser.Tokens identifiers
 *  @param start   the offset of the first character in this token
 *  @param end     the offset of the first character after this token
 */
case class Token(tokenId: Int, start: Int, end: Int)
