/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.editor
import scala.tools.nsc.ast.parser
import parser.Tokens._

// TODO: handle braces inside quotes

trait Matchers extends Tokenizers {
  private implicit def m2m(m : OpenMatch) : MatchAnswer = MatchOpen(m)
  type File <: FileImpl
  trait FileImpl extends super.FileImpl {selfX : File => 
    def self : File
    override def defaultMatch = DefaultSegment
    override def specialPrev(offset : Int) : Option[Token] = {
      val useEnd = offset
      def tryit(code : Int, offset : Int)(f : OpenMatch => Boolean) = adjacent(offset) match {
      case Some(Match(kind,begin,end)) if offset == end && f(kind) => 
        Some(Token(begin, content.slice(begin, useEnd), code))
      case _ => None
      }
      if (offset > 0 && content(offset-1) == '\"') tryit(STRINGLIT, offset)(_.open(0) == '"')
      else if (offset > 0 && content(offset-1) == '`') tryit(BACKQUOTED_IDENT, offset)(_.open(0) == '`')
      else if (offset > 1 && content.slice(offset - 2, offset).mkString == "*/") tryit(COMMENT, offset)(_.isInstanceOf[Comment])
      else if (content.length > offset +1 && parser.Tokens.isNewLine(content(offset + 1))) {
        val c = content(offset)
        assert(c != 0)
        tryit(COMMENT, offset + 2)(_ == LineComment)
      } else None
    }
    
    override protected def adjustDamage(from : Int, until : Int) = {
      val enclosing = this.enclosing(from, until)
      var from0 = from
      var until0 = until
      if (enclosing.isDefined && enclosing.get.kind == LineComment) {
        if (enclosing.get.from < from0) from0 = enclosing.get.from
        if (enclosing.get.until > until0) until0 = enclosing.get.until
      }
      //while (file.content( from0) == '\"')  from0 -= 1
      //while (file.content(until0) == '\"') until0 += 1
      
      while (from0 > 0 && (file.content(from0 - 1) match {
      case '*'|'\"' => true
      case '<'|'>' => true
      case '{'|'}' => true
      case '('|')' => true
      case '/' => true
      case _ => false
      })) from0 = from0 - 1
      
      while (until0 < file.content.length - 1 && (file.content(until0) match {
      case '\"'|'/' => true
      case '*' => true
      case '<'|'>' => true
      case '{'|'}' => true
      case '('|')' => true
      case _ => false
      })) until0 = until0 + 1
      (from0,until0)
    }
    
    override protected def startFor(offset : Int) : Int = {
      val result = super.startFor(offset)
      val result0 = enclosing(offset) match {
      case Some(Match(_,from,_)) => assert(from <= offset); from
      case _ => 0
      }        
      if (result > result0) result
      else result0
    }
    
    
    // tokenize XML blocks.
    override def tokenFor(offset : Int) : Token = {
      unmatchedOpen(offset) match {
      case Some(kind) if kind == StringMatch || kind == MultiMatch =>
          Token(offset, content.slice(offset, offset + kind.open.length), STRINGLIT)  
        case Some(kind : MultiLineComment) =>
          Token(offset, content.slice(offset, offset + kind.open.length), COMMENT)  
        case Some(kind) if kind == StringMatch || kind == MultiMatch || kind.isInstanceOf[MultiLineComment] =>
          abort
        case _ => super.tokenFor(offset)
      }
    }
    type Token <: TokenImpl
    trait TokenImpl extends super.TokenImpl  {
      def self : Token
      /*
      override def nextRegion = {
        if (text(0) == '(' && content.drop(offset).startsWith("(<")) {
          // XML region
          border(offset, NEXT) match {
          case Some(m @ Match(kind,from,until)) => 
            assert(kind == XMLParenMatch)
            var result = List[Region]()
            val i = m.within.elements
            var f = from + 1
            var g : Region => Region = r => r
            
            while (i.hasNext) {
              val n = i.next
              assert(n.kind == Curlies)
              assert(f <= n.from)
              if (f < n.from) { 
                result =
                  g(Token(f, content.slice(f, n.from), XMLSTART)) :: result
                g = r =>r
              }
              f = n.until
              val oldG = g
              g = next => {
                result = oldG(new Region {
                  def nextRegion = Some(next)
                  def regionOffset = n.from
                  def children = new Iterator[Region] {
                    var tok : Region = tokenFor(n.from)
                    def hasNext = tok.regionOffset < n.until
                    def next = {
                      val ret = tok
                      tok = tok.nextRegion.get
                      ret
                    }
                    override def toString = regionOffset + "{}"
                  }
                }) :: result
                next
              }
            }
            assert(f < until - 1)
            result = g(Token(f, content.slice(f, until - 1), XMLSTART)) :: result
            result = result.reverse
            Some(new Region {
              def children = result.elements
              def nextRegion = Some(tokenFor(until - 1))
              def regionOffset = from + 1
              override def toString = regionOffset + "XML"
            })
          case None => super.nextRegion
          }
        } else super.nextRegion
      }
	    */
    }
  }
  protected trait SingleToken extends InCode {
    def code : Int
    def endCode = code
  }
  
  
  protected case class BraceMatch(openC : Char, closeC : Char) extends InCode with ScalaCodeRegion {
    def open = "" + openC
    def close = "" + closeC
    override def checkLast(content : RandomAccessSeq[Char]) = content(0) match {
    case c if c == `closeC` => MatchClose()
    case _ => DefaultSegment.checkLast(content)
    }
  }
  protected val Parens = BraceMatch('(', ')')
  protected val Curlies = BraceMatch('{', '}')
  protected val Bracks = BraceMatch('[', ']')
  
  trait StringLike extends SingleToken {
    override def close = open
    override def code = STRINGLIT
    override def checkFirst(content : RandomAccessSeq[Char]) = content(0) match {
    case '\\' => Some(2)      
    case _ => super.checkFirst(content)  
    }
  }
  protected val StringMatch = new SingleLine with StringLike {
    override def open = "\""
  }
  protected val MultiMatch = new InCode with StringLike {
    override def open = "\"\"\""
  }
  class XMLMatch(op : Char, cl : Char) extends InCode {
    def open = ""+op+"<"
    def close = ">"+cl+""
    override def checkLast(content : RandomAccessSeq[Char]) = content(0) match {
    case '{' if content.length > 1 && content(1) == '{' => NoMatch(2)
    case '{' => Curlies
    case '>' if content.length > 1 && content(1) == cl => 
      if (content.length > 2 && content(1) == '}' && content(2) == '}') NoMatch(3)
      else MatchClose()
    case _ => super.checkLast(content)  
    }
  }
  //val XMLCurlyMatch = new XMLMatch('{', '}')
  val XMLParenMatch = new XMLMatch('(', ')')
  
  protected abstract class Comment extends InCode with SingleToken {
    def code = COMMENT
  }
  protected val LineComment = new Comment {
    def open = "//"
    def close = "\n"
    override def toString = "//"
    override def endCode = NEWLINE
    override def checkLast(content : RandomAccessSeq[Char]) = content(0) match {
    case c if isNewLine(c) => MatchClose()
    case _ => super.checkLast(content)
    }
  }
  protected val BackQuote = new SingleLine with SingleToken {
    def open = "`"
    def close = open
    def code = BACKQUOTED_IDENT
  }
  protected class MultiLineComment extends Comment with HasMultiLineComments {
    def open = "/*"
    def close = "*/"
  }
  protected val MultiLineComment = new MultiLineComment
  protected val DocComment = new MultiLineComment {
    override def open = "/**" // */
  }
  private def ck(file : File, offset : Int, m : OpenMatch) = file.content.drop(offset).startsWith(m.open)
  protected trait ScalaCodeRegion extends OpenMatch {
    override def checkFirst(content : RandomAccessSeq[Char]) = content(0) match {
    case '\'' => makeCoreScanner(content).next match {
    case (0,length,code) => Some(length)
    }
    case _ => super.checkFirst(content)
    }
  }

  protected trait HasMultiLineComments extends OpenMatch {
    override def checkLast(content : RandomAccessSeq[Char]) = content(0) match {
    case '/' => 
      val b0 = content.startsWith("/*")
      val b1 = b0 && content.startsWith("/**")
      val b2 = b1 && content.startsWith("/**/")
      if      (b2) MultiLineComment
      else if (b1) DocComment
      else if (b0) MultiLineComment
      else super.checkLast(content)
    case _ => super.checkLast(content)
    }
  }

  protected trait InCode extends OpenMatch {
    override def toString = open + close
    override def checkLast(content : RandomAccessSeq[Char]) = {
      if (content.startsWith(close)) MatchClose()
      else super.checkLast(content)
    }
  }
  protected trait SingleLine extends InCode {
    override def checkLast(content : RandomAccessSeq[Char]) = content(0) match {
    case c if isNewLine(c) => RejectOpen
    case _ => super.checkLast(content)
    }
  }
  protected val DefaultSegment = new HasMultiLineComments with ScalaCodeRegion {
    override def toString = "default"
    def open = ""
    def close = ""
    override def checkLast(content : RandomAccessSeq[Char]) = content(0) match {
    //case '{' if content.length > 1 && content(1) == '<' => XMLCurlyMatch
    case '(' if content.length > 1 && content(1) == '<' => XMLParenMatch
    case '(' => Parens
    case '{' => Curlies
    case '[' => Bracks        
    case ')' => UnmatchedClose(Parens)       
    case '}' => UnmatchedClose(Curlies)        
    case ']' => UnmatchedClose(Bracks)       
    case '`' =>  BackQuote
    case '\"' if content.startsWith("\"\"\"") => MultiMatch
    case '\"' => StringMatch
    case '/' if  content.startsWith("//") => LineComment
    case _ => super.checkLast(content)
    }
  }
}