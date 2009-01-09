/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.editor
import scala.tools.nsc.ast.parser
import parser.Tokens._
import parser.Tokens

trait Tokenizers extends lampion.compiler.Tokenizers {
  trait Compiler extends scala.tools.nsc.Global with scala.tools.nsc.ast.parser.NewScanners {self : compiler.type =>
    override lazy val global : Compiler.this.type = this
  }
  val compiler : Compiler
  import scala.tools.nsc.io.AbstractFile
  import scala.tools.nsc.util
  
  type IdentifierPosition <: IdentifierPositionImpl
  trait IdentifierPositionImpl extends super.IdentifierPositionImpl with util.Position {
    def self : IdentifierPosition
    override def dbgString = "XXX"
    override def toString = java.lang.Integer.toString(hashCode, (10 + ('z' - 'a')))  
  }
  type File <: FileImpl 
  trait FileImpl extends super.FileImpl {selfX : File =>
    def self : File
    def nscFile :AbstractFile
    override def repair(offset : Int, added : Int, removed : Int) = {
      super    .repair(offset, added : Int, removed : Int)
    }
    override def doUnload = {
      super.doUnload
    }
    override def clear = {
      super.clear
    }
    type IdentifierPosition <: Tokenizers.this.IdentifierPosition with IdentifierPositionImpl
    trait IdentifierPositionImpl extends Tokenizers.this.IdentifierPositionImpl with super.IdentifierPositionImpl {
      def self : IdentifierPosition
      override def offset : Option[Int] = if(self.isValid) Some[Int](self.absolute : Int) else None
      override def source = Some(FileImpl.this.unit.source)
    }
    def specialPrev(offset : Int) : Option[Token]
    
    type Token <: TokenImpl
    trait TokenImpl extends super.TokenImpl {
      def self : Token
      def code : Int
      def isKeyword = parser.Tokens.isKeyword(code)  
      def isWhitespace = code == WHITESPACE
      def isNewline = code match {
      case NEWLINE|NEWLINES => true
      case _ => false
      }
      override protected def isEOF = code == EOF
      //def tokenPosition(owner : parses.Range) = TokenPosition(owner.get, deflated(owner))
      
      def isIdentifier = code match {
      case IDENTIFIER => true
      case BACKQUOTED_IDENT if text == "`" => true
      case _ => false
      }
      override def prev : Option[Token] = {
        if (offset <= 0) return None
        var last : Token = null.asInstanceOf[Token]
        specialPrev(offset) match {
        case ret @ Some(_) => return ret
        case _ =>
        }
        val firstC = content(offset - 1)
        val isSpace = Tokens.isSpace(firstC)
        val isNewLine = Tokens.isNewLine(firstC)
        var from = offset - 1
        while (from > 0 && (specialPrev(from) match {
        case Some(_) => false
        case _ =>
          val c = content(from - 1)
          if (isSpace) Tokens.isSpace(c)
          else if (isNewLine) Tokens.isNewLine(c)
          else if (firstC == '\'') { // just look for the next quote!
            if (c == '\'') {
              from = from - 1; false
            } else true
          } else !Tokens.isSpace(c) && !Tokens.isNewLine(c)
        })) from = from - 1
        val until = offset
        val content0 = FileImpl.this.content.slice(from,until)
        val s = makeScanner(from, content0)
        var tok : Token = s.next
        while (s.hasNext) tok = s.next
        assert(tok.extent == until)
        Some(tok)
      }
      
      
      def isStatementEnd : Boolean = code match {
      case IDENTIFIER|BACKQUOTED_IDENT if isOperator => false
      case SEMI|COMMA => true
      case NULL|TRUE|FALSE|THIS|USCORE => true
      case code if isKeyword || isSymbol(code) => false
      case RPAREN if isConditionBrace => 
        assert(true)
        false
      case _ => true
      }
      
      def isOperator = if (!isIdentifier) false else isOperator0(prev)
      protected def isConditionBrace = false
      def isOperator0(prev : Option[Token]) : Boolean = {
        var node = prev
        while (node.map(_.code) match {
        case None => return false            
        case Some(NEWLINE|NEWLINES|SEMI|DOT|ARROW|LPAREN|LBRACE|LBRACKET) => return false
        case Some(RBRACKET|RBRACE|RPAREN) => return !node.get.isConditionBrace
        case Some(WHITESPACE) => true
        case _ => return node.get.isStatementEnd
        }) node = node.get.prev
        return false
      }
      
      override def toString = super.toString + ":" + code
    }
    def Token(offset : Int, text : RandomAccessSeq[Char], code : Int) : Token 
    def makeScanner(base : Int, text : RandomAccessSeq[Char]) = {
      makeCoreScanner(text).map{
        case (offset,length,EOF) => 
          Token(base + offset, "\0", EOF)
        case (offset,length,code) => 
          val text0 = text.slice(offset, offset + length)
          //if (code == COMMENT && text0.endsWith("\n")) 
          //  logError("multi-line comment ends with newline???", null)
          Token(base + offset, text0, code)
      }
    }
    
    override def tokenFor(offset : Int) : Token = {
      val content = this.content.drop(offset)
      val i = makeScanner(offset, content)
      i.next
    }
    override def tokenFor(from : Int, until : Int) : Iterator[Token] = {
      val content = this.content.slice(from, until)
      val i = makeScanner(from, content).filter{tok => tok.code match {
        case WHITESPACE|NEWLINE|NEWLINES => false
        case _ => true
      }}
      i
    }
    // not used very often, doesn't have to be fast.
    protected def startFor(offset : Int) : Int = if (!editing) 0 else parses.adjacent(offset) match {
      case range @ parses.ActualRange(from,_) => 
        assert(from <= offset)
        // known token boundaries.
        if (offset <= range.until) from
        else range.until
      case _ => 0
    }
    override def tokenForFuzzy(offset : Int) : Token = {
      val start = startFor(offset)
      assert(start <= offset)
      var tok = tokenFor(start)
      while (tok.extent <= offset) tok = tok.next.get
      tok
    }
    private var unit0 : compiler.CompilationUnit = _
    def unit = {
      import scala.tools.nsc.util.SourceFile
      if (unit0 == null) {
        val file = new SourceFile {
          val file = nscFile
          def content = FileImpl.this.content
          def length = FileImpl.this.content.length
          def lineToString(x : Int) = "<no-string>"
          def skipWhitespace(x : Int) = x
          def beginsWith(x : Int, prefix : String) = false
          def lineToOffset(x : Int) = x
          def offsetToLine(x : Int) = x
          def isLineBreak(x : Int) = false
          override def position(offset: Int) : util.Position = new scala.tools.nsc.util.OffsetPosition(this, offset)
          override def identifier(pos : util.Position, compiler : scala.tools.nsc.Global) = identifierFor(pos)
        }
        unit0 = new compiler.CompilationUnit(file)
        unit0.fresh = new util.FreshNameCreator {
          @deprecated def newName = abort
          @deprecated def newName(prefix : String) = abort
          def newName(pos : util.Position) : String = {
            if (pos == util.NoPosition) newName()
            else pos match {
            case pos : IdentifierPositionImpl => ("id" + Integer.toString(pos.hashCode, 10 + ('z' - 'a')))
            }
          }
          def newName(pos : util.Position, prefix : String) : String = {
            if (pos == util.NoPosition) newName(prefix)
            else pos match {
            case pos : IdentifierPositionImpl => (prefix + "" + Integer.toString(pos.hashCode, 10 + ('z' - 'a')))
            }
          }
        }
      }
      unit0
    }
    private def identifierFor(pos : util.Position) : Option[String] = pos match {
    case util.NoPosition => None
    case pos : ParseNodeImpl => None
    case scala.tools.nsc.util.OffsetPosition(_,offset) => Some(tokenFor(offset).text)
    case pos : IdentifierPositionImpl => if (pos.isValid) Some(tokenFor(pos.offset.get).text) else None
    }
    override def loaded = {
      super.loaded
      new compiler.Run // force initialization
      // XXX: should do in type checker thread....
      //val locs = lockTyper0{generateIdeMaps.getIdeMap(unit.source, sourcePackage, defaultClassDir)}
      //if (locs.isEmpty) // otherwise we can use cached information.
      //  editing = true
    }
    def sourcePackage : Option[String] = None
    def defaultClassDir : Option[AbstractFile] = None
  } 
  
  protected def makeCoreScanner(text : RandomAccessSeq[Char]) = {
    import scala.tools.nsc.util._
    var hasError = false
    val reader = new NewCharArrayReader(text,true,null)
    val in0 = new compiler.DefaultInput(reader) {
      override def error(offset : Int, msg : String) : Unit = 
        hasError = true
    }
    new compiler.BaseScanner {
      implicit def in = in0 
    }.iterator
  }
  
}