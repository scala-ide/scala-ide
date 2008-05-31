/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.editor
import scala.tools.nsc.util
 
trait Parsers extends Matchers with ParseNodes {
  trait Compiler extends super.Compiler with scala.tools.nsc.ast.parser.Parsers{self : compiler.type =>
  }
  override val compiler : Compiler
  import compiler._
  import scala.tools.nsc.ast.parser.Tokens._
  trait Scanner extends compiler.ParserScanner

  abstract class Parser extends compiler.Parser with ParserImpl {
    override def acceptStatSep() = {
      if (super.acceptStatSep()) true
      in.token match { // we might get stuck here!
      case COMMA|RPAREN|RBRACKET =>
        in.nextToken; skip() // resync!
      case _ =>
      }
      false
    }
    override protected def skip() = {
      var depth = 0
      val txt = this.node.parseContext.pinfo
      var atLeastOne = false;
      while ((in.token,txt) match {
      case (LBRACE|LBRACKET|LPAREN,_)  => depth += 1; true
      case (COMMA,_) if depth == 0 => in.nextToken; false // consume
      case (RBRACE|RBRACKET|RPAREN,_)  => 
        if (depth == 0) false
        else { depth -= 1; true }
        // can be eaten in class definition.
      case (CLASS|DEF|VAL|VAR|OBJECT|TYPE|CASEOBJECT|CASECLASS|TRAIT| 
            ABSTRACT|FINAL|PRIVATE|PROTECTED|OVERRIDE|IMPLICIT|IMPORT, _:Statement) if depth == 0 => false
            
      case (SEMI|NEWLINE|NEWLINES,_) if depth == 0 => false
      case (EOF,_) => false
      case _ => true
      }) {
        atLeastOne = true
        in.nextToken
      }
      if (!atLeastOne) {
        assert(this != null)
      
      }
    }
    import scala.tools.nsc.symtab.Flags
    private def incompleteKey = {
      syntaxError(in.currentPos, "incomplete keyword", false)
      in.nextToken
    }
    private def skipIdentifier = {
      syntaxError(in.currentPos, "expected definition", false)
      in.nextToken
    }
    private def unexpected = {
      syntaxError(in.currentPos, "unexpected", false)
      node.enclosing.foreach(_.dirtyParse)
      in.nextToken
    }
    /* override IDE hooks */
    override def caseBlock : Tree = in.token match {
    case _ if true => super.caseBlock
    case ARROW =>
      indirect(CaseBlock)
    case _ =>
      syntaxError(in.currentPos, "expected arrow")
      EmptyTree
    }
    override def nonLocalDefOrDcl : List[Tree] = List(indirect(NonLocalDefOrDcl))
    override def localDef : List[Tree] = List(indirect(LocalDef))
    override def topLevelTmplDef : Tree = indirect(TopLevelTmplDef)
    override def statement(kind : Int) : Tree = in.token match {
    case RPAREN|RBRACE|RBRACKET|SEMI|COLON|ARROW|COMMA => 
      syntaxError((in.currentPos), "expected statement"); Ident(nme.ERROR)
    case _ => indirect(Statement(kind))
    }
    /* re-hooks */
    private def skipLeadingIdent = in.token match {
    case IDENTIFIER => syntaxError(in.currentPos, "definition expected"); in.nextToken
    case _ =>
    }
    def nonLocalDefOrDcl0 = {
      skipLeadingIdent // just in case somebody is typing a new modifier 
      if (isDefIntro || isModifier || in.token == LBRACKET /*todo: remove */ || in.token == AT) {} 
      else node.enclosing.foreach(_.dirtyParse)
      super.nonLocalDefOrDcl
    }
    def localDef0 = {
      skipLeadingIdent
      if (isDefIntro || isLocalModifier || in.token == AT) {}
      else node.enclosing.foreach(_.dirtyParse)
      super.localDef
    }
    def topLevelTmplDef0 = {
      skipLeadingIdent
      if (in.token == CLASS ||
        in.token == CASECLASS ||
        in.token == TRAIT ||
        in.token == OBJECT ||
        in.token == CASEOBJECT ||
        in.token == LBRACKET || //todo: remove
        in.token == AT ||
        isModifier) {}
      else node.enclosing.foreach(_.dirtyParse)
      super.topLevelTmplDef
    }
    def caseBlock0() : Tree = in.token match {
    case ARROW => super.caseBlock() 
    case _ =>
      syntaxError(in.currentPos, "expected arrow", false)
      node.enclosing.foreach(_.dirtyParse)
      EmptyTree
    }
    
    def statement0(kind : Int) : Tree = in.token match { // something changed
    case RBRACE|SEMI|CASE => node.enclosing.foreach(_.dirtyParse); EmptyTree // nothing to do
    case (CLASS|DEF|VAL|VAR|OBJECT|TYPE|CASEOBJECT|CASECLASS|TRAIT| 
          ABSTRACT|FINAL|PRIVATE|PROTECTED|OVERRIDE|IMPLICIT|IMPORT) => 
      syntaxError(in.currentPos, "expected statement", false)
      node.enclosing.foreach(_.dirtyParse)
      in.nextToken
      EmptyTree
    case ELSE => 
      syntaxError(in.currentPos, "missing if", false)
      node.prevOption.foreach(_.dirtyParse)
      in.nextToken
      super.statement(kind)   
    case _ => super.statement(kind)
    }
    protected def indirect(info : ParseInfo) : Tree
  }
  trait ParseInfo extends (Parser => List[Tree]) with Product with TreeKind {
    override def isType = false
    override def isTerm = false
    override def hasSymbol = false
    override def isDef = false
    override def isTop : Boolean = false

    def prefix : String
    def compatible(that : ParseInfo) = getClass == that.getClass
    
    
    override def toString = prefix + {
      var i = 0
      val until = productArity
      var string = if (until > 0) "(" else ""
      while (i < until) {
        string = string + productElement(i)
        i += 1
        if (i < until) string = string + ", "
      }
      if (until > 0) string = string + ")"
      string
    }
    override def equals(that : Any) : Boolean = that match {
    case that : ParseInfo if that.getClass == getClass => 
      var i = 0
      val until = productArity
      while (i < until) {
        if (productElement(i) != that.productElement(i)) return false
        i += 1
      }
      return true
    case _ => false
    }
  }
  object TopLevel extends ParseInfo {
    def prefix = "top"
    def apply(parser : Parser) = parser.compilationUnit :: Nil
    def productArity = 0
    def productElement(n : Int) = throw new Error
    override def isTop : Boolean = true
  }
  case class Statement(kind : Int) extends ParseInfo {
    def apply(parser : Parser) = parser.statement0(kind) :: Nil
    override def isTerm = true
    def prefix = "s"
  }
  case object NonLocalDefOrDcl extends ParseInfo {
    def apply(parser : Parser) = parser.nonLocalDefOrDcl0
    override def hasSymbol = true
    override def isDef = true
    def prefix = "d"
  }
  case object CaseBlock extends ParseInfo {
    def apply(parser : Parser) = parser.caseBlock0() :: Nil
    override def hasSymbol = false
    override def isDef = false
    def prefix = "a"
  }
  
  case object LocalDef extends ParseInfo {
    def apply(parser : Parser) = parser.localDef0
    override def hasSymbol = true
    override def isDef = true
    def prefix = "l"
  }
  case object TopLevelTmplDef extends ParseInfo {
    def apply(parser : Parser) = List(parser.topLevelTmplDef0)
    override def hasSymbol = true
    override def isDef = true
    def prefix = "c"
  }
  private val initialContext = ParseContext(Nil,Nil,Nil,Nil,TopLevel)
  trait FileA extends super[Matchers].FileImpl {selfX : File =>}
  trait FileB extends super[ParseNodes].FileImpl{selfX : File =>}
  type File <: FileImpl
  trait FileImpl extends FileA with FileB {selfX : File => 
    def self : File
    protected def initialContext = Parsers.this.initialContext

    trait Scanner extends Parsers.this.Scanner
    abstract class Parser extends Parsers.this.Parser { 
      override def freshName(pos : util.Position, prefix: String) = 
        newTermName(unit.fresh.newName(pos, prefix))
    }
    type ParseNode <: Parsers.this.ParseNode with ParseNodeImpl 
    trait ParseNodeImpl extends super[FileB].ParseNodeImpl with Parsers.this.ParseNodeImpl {selfX : ParseNode => 
      def self : ParseNode
      // used instead of identifier positions for parse errors, which are computed in the UI thread.
      private case class ParseErrorPosition(relative : Int) extends ErrorPosition {
        def absolute = absoluteFor(relative)
        def isValid = self.isValid
        def owner = self
      }
      override def prefix = parseContext.pinfo.prefix
      trait Scanner extends FileImpl.this.Scanner
      
      override def isOnSameLineAs(relative : Int) : Boolean = {
        val diff = relative - this.relative
        val offset = this.absolute
        import scala.tools.nsc.ast.parser.Tokens
        val content = FileImpl.this.content
        if (diff > 0) {
          var i = 0
          while (i < diff) {
            if (Tokens.isNewLine(content(offset + i))) return false
            i += 1
          }
          return true
        } else {
          var i = 0
          while (i < -diff) {
            if (Tokens.isNewLine(content(offset - i))) return false
            i += 1
          }
          return true
        }
      }
      override def Parser(content : RandomAccessSeq[Char], context : ParseContext) = new Parser(content : RandomAccessSeq[Char], context : ParseContext)
      def computeLastCode(in : compiler.ParserScanner) =
        compiler.inLastOfStat(IDENTIFIER) // in.lastCode) 
          // only used for computing new line information, shouldn't be relevant.
      protected def identifier(in : compiler.ScannerInput, name : Name) = name

      class Parser(content : RandomAccessSeq[Char], context : ParseContext) extends FileImpl.this.Parser with super.ParserImpl {

        def self : Parser = this 
        def doParse : (Int,Boolean) = {
          val timer = new lampion.util.BenchTimer
          val newParseTrees = context.pinfo(this)
          if ((!newParseTrees.isEmpty && !newParseTrees.equalsWith(parseTrees)(_ equalsStructure _))) {
            parseTrees = newParseTrees
            if (!hasParseErrors) parseChanged // we cause re-typing.
          }
          if (this == null) Console.println("REPARSE: " + ParseNodeImpl.this + (" " + timer.elapsedString))
          val newLastCode = computeLastCode(in)
          val parentChanged = if (lastCode != newLastCode) {
            lastCode = newLastCode
            true
          } else false
          val length = in.currentPos
          val length0 = absoluteFor(length)
          if (length0 < FileImpl.this.content.length && tokenFor(length0).offset != length0) {
            assert(true)
            val tok = tokenFor(length0)
            assert(tok.offset == length0)
            ()
          }
          (length, parentChanged)
        }
        override def synch(relative : Int, node : ParseNode) = {
          assert(unadjust(relative) >= in.in.offset)
          in.seek((relative), if (node.lastCode) CHARLIT else EOF)
          assert(in.currentPos >= relative)
        }
        override def indirect(info : ParseInfo) : Tree = {
          //ptimer.update
          //ptimer.disable
          val offset = in.currentPos
          in.flush
          val txt = ParseContext(in.sepRegions, implicitClassViews, placeholderParams, placeholderTypes, info)
          val tree = indirect(offset, txt)
          //Console.println("INDIRECT: " + tree.asInstanceOf[StubTree].container.getParseTrees)
          //ptimer.enable
          tree
        }
        implicit def i2p(offset : Int) = node.identifierPosition(offset)

        def incompleteInputError(msg : String) = {
          error(ParseNodeImpl.this.self, ParseError(msg,false))
        }
        override def warning(pos : Int, msg : String) = {}
        def deprecationWarning(pos : Int, msg : String) = {}
        override def syntaxError(pos : Int, msg : String) = {
          // pos is already relative to node.
          val offset = pos
          assert(offset >= 0)
          var offset0 = offset
          if (offset0 >= content.length) offset0 = content.length - 1
          while (offset0 > 0 && 
            (content(offset0) == ';' || isSpace(content(offset0)) || isNewLine(content(offset0))))
            offset0 -= 1
          error(ParseErrorPosition(if (offset == offset0) (pos) else 
            (tokenForFuzzy(offset0 + ParseNodeImpl.this.absolute).offset - ParseNodeImpl.this.absolute)), ParseError(msg, false))
        }
        override def xmlLiteral : Tree = ParseNodeImpl.this.xmlLiteral0(this)
        override def xmlLiteralPattern : Tree = ParseNodeImpl.this.xmlLiteralPattern0(this)
          
        private val in0 = new util.NewCharArrayReader(content, !compiler.settings.nouescape.value, (a0,a1) => ())
        private val in1 = new compiler.DefaultInput(in0) with compiler.ScannerInput {
          override def error(offset : Int, msg : String) : Unit =
            ParseNodeImpl.this.error(ParseErrorPosition((offset)), ParseError(msg, false))
        } 
        protected def   adjust(offset : Int) = offset
        protected def unadjust(offset : Int) = offset
        override val in = new compiler.ParserScanner with ParseNodeImpl.this.Scanner {
          override def in = in1
          init
          override def   adjust(offset : Int)  = Parser.this.  adjust(offset)
          override def unadjust(offset : Int)  = Parser.this.unadjust(offset)
          override def identifier(name : Name) = // gives us the name.
            ParseNodeImpl.this.identifier(in, name)
        }
        
        in.sepRegions = context.sepRegions // .map(code => new compiler.Token(0, 1, code))
        implicitClassViews = context.implicitClassViews
        placeholderParams = context.placeholderParams
      }
      import compiler.Tree
      import scala.collection.mutable._
      private def xml(parser : Parser, pattern : Boolean)(f : Buffer[compiler.Tree] => Tree) = {
        val offset = absoluteFor(parser.in.currentPos)
        FileImpl.this.enclosing(offset) match {
        case Some(m @ Match(kind,from,until)) if kind == XMLParenMatch =>
          parser.in.flush // prepare for XML
          import scala.tools.nsc.ast.parser.Tokens._
          assert(offset - 1 == from) // because the brace has already been consumed by the parser
          // find each block...
          val trees = new ArrayBuffer[compiler.Tree]
          m.within.foreach{
              case Match(kind,from,until) =>
                assert(kind == Curlies)  
                parser.in.seek(relativeFor(from+1), RBRACE)
                parser.in.sepRegions = RBRACE :: parser.in.sepRegions
                val ret = if (!pattern) parser.block :: Nil
                          else parser.patterns(true)
                parser.in.sepRegions = parser.in.sepRegions.tail
                parser.in.flush
                trees ++= ret
          }
          parser.in.seek(relativeFor(until - 1), XMLSTART)
          f(trees)
        case _ => 
          parser.syntaxError(parser.in.currentPos, "XML without parantheses unimplemented")
          parser.in.nextToken
        compiler.EmptyTree
        }
      }
      def xmlLiteral0(parser : Parser) : Tree =
        xml(parser, false)(trees => compiler.Block(trees.toList, compiler.New(_Elem, treesX)))

      def xmlLiteralPattern0(parser : Parser) : Tree =
        xml(parser, true){trees => 
          import compiler._
          Apply( _Elem, List( 
              Ident(nme.WILDCARD), Literal("form"), Ident( nme.WILDCARD ) /* md */ , Ident( nme.WILDCARD )) /* scope */ ::: trees.toList)
        }
      import compiler._
        
      private def _scala(name: Name) =
        Select(Select(Ident(nme.ROOTPKG), nme.scala_), name)

      private def _scala_xml(name: Name) = Select(_scala(_xml), name)
      def _xml = newTermName("xml")

      def _Null = _scala_xml(newTermName("Null"))
      def _TopScope = _scala_xml(newTermName("TopScope"))
      def _Elem = _scala_xml(newTypeName("Elem"))
      private def treesX = (Literal(Constant(null)) :: Literal("TAG") :: _Null :: _TopScope :: Nil):: Nil
    } 
  } 
  case class ParseContext(sepRegions : List[Int], 
      implicitClassViews : List[Tree], placeholderParams: List[ValDef], 
      placeholderTypes: List[TypeDef], pinfo : ParseInfo) extends ParseContextImpl {
    def compatible(that : ParseContext) = pinfo.compatible(that.pinfo)
    override def equals(that : Any) = that match {
    case ParseContext(x,y,z,zz,a) => x == sepRegions && {
      assert(true)
      a == pinfo
    } &&
      y.equalsWith(implicitClassViews)(_ equalsStructure _) &&
        z.equalsWith(placeholderParams)(_ equalsStructure _) &&
          zz.equalsWith(placeholderTypes)(_ equalsStructure _)
    case _ => false
    }
  }
  type ParseNode <: Node with ParseNodeImpl 
  trait ParseNodeImpl extends super.ParseNodeImpl {selfX : ParseNode => 
    def self : ParseNode
    protected var parseTrees : List[Tree] = Nil
    override def toString = super.toString + (if (parseTrees.isEmpty) "" else "::" + (parseTrees.last match {
    case tree : DefTree => tree.name.decode
    case tree : Match => tree.selector.toString
    case tree => tree.toString 
    }))
    
    def getParseTrees = parseTrees
    protected var lastCode : Boolean = false
    protected override def destroy0 = {
      super.destroy0
    }
  }
}
