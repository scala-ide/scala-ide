/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.compiler
import scala.collection.jcl._

trait Parsers extends NewMatchers with core.RangeTrees  {
  protected val ptimer = new lampion.util.BenchTimer
  
  abstract class ErrorKind {
    val msg : String
    val isWarning : Boolean
  }
  case class ErrorWrapper(node : ParseNode, error : ErrorKind)
  case class ParseError(msg : String, isWarning : Boolean) extends ErrorKind {
    def this(msg : String) = this(msg, false)
  }
  type Parser <: ParserImpl
  trait ParserImpl {selfX : Parser =>
    def node : ParseNode
    // used for token positions.
    def deflate(offset : Int) : Int
  }
  type ParseContext <: ParseContextImpl
  trait ParseContextImpl {
    def compatible(that : ParseContext) : Boolean
  }
  type ParseTree
  trait ErrorPosition {
    def absolute : Int
    def owner : ParseNode
    def isValid : Boolean
  }
  type ParseNode <: Node with ParseNodeImpl
  trait ParseNodeImpl extends ErrorPosition {selfX : ParseNode =>
    def self : ParseNode
    def file : File
    def asParseTree : ParseTree
    def prevOption : Option[ParseNode]
    private[Parsers] var context : ParseContext = _
    def parseContext = context
    private[compiler] def updateParse(context : ParseContext, from : Parsers.this.Parser) : ParseTree = {
      if (context != this.context) {
        this.context = context
        dirtyParse
      }         
      doParse
      asParseTree
    }
    def isOnSameLineAs(relative : Int) : Boolean
    override def finalize = {
      super.finalize
      //Console.println("GARBAGE_COLLECT: " + id)
    }

    def absolute : Int
    def length : Int
    def enclosing : Option[ParseNode]
    //def prev : Option[ParseNode]
    def absoluteFor(relative : Int): Int = absolute + relative
    def relativeFor(offset : Int): Int = {
      val absolute = this.absolute
      assert(offset >= absolute)
      (offset - absolute)
    }
    def flushErrors(p : ErrorKind => Boolean) : Unit
    def hasErrors(f : ErrorKind => Boolean) : Boolean
    def hasParseErrors = hasErrors(_.isInstanceOf[ParseError])
    protected def error(position : ErrorPosition, error : ErrorKind) : Unit // can specify errors at absolute

    protected def parseChanged = {}
    protected def destroy0 : Unit = {}
    def isValid = true
    protected def parseInner(parser : Parser) = parser.doParse
    def doParse : Unit 
    def dirtyParse : Unit
    // offset into this parse node
    type Parser <: Parsers.this.Parser with ParserImpl 
    trait ParserImpl extends Parsers.this.ParserImpl {selfX : Parser =>
      def doParse : (Int,Boolean) 
    }
    def Parser(content : RandomAccessSeq[Char], context : ParseContext) : Parser
  }
  trait FileA extends super[NewMatchers].FileImpl
  trait FileB extends super[RangeTrees].FileImpl
  type File <: FileImpl
  trait FileImpl extends FileA with FileB {selfX : File =>
    def self : File
    protected def initialContext : ParseContext
    override def prepareForEditing = {
      super.prepareForEditing
      val node = rootParse 
      node.context = initialContext
      node.dirtyParse
    }
    def rootParse : ParseNode = parses.create(0)
    
    protected def noParse(kind : Match, offset : Int, added : Int, removed : Int) : Boolean = false
    protected def noParse(offset : Int, added : Int, removed : Int) : Boolean = {
      var enclosing = FileImpl.this.enclosing(offset, offset + removed)
      if (enclosing.isDefined && noParse(enclosing.get, 
          offset - enclosing.get.from, added, removed)) true
      else false
    }
    
    object parses extends CachedRangeTreeBank {
      val dirty = new LinkedHashSet[ParseNode]
      type RangeTree = ParseNode
      def RangeTree = ParseNode
      override def repair(offset : Int, added : Int, removed : Int) : Unit = {
        super.repair(offset, added, removed)
        // figure out what parse node to damage.
        if (noParse(offset, added, removed)) return
        var go = adjacent(offset)
        while (!go.isEmpty) {
          go.get.dirtyParse
          if (go.get.hasLength && offset < go.until) 
            go = NoRange
          else go = go.enclosing
        }
      }
    }
    type ParseNode <: Parsers.this.ParseNode with ParseNodeImpl
    trait ParseNodeImpl extends NodeImpl with parses.RangeTreeImpl with Parsers.this.ParseNodeImpl { selfX : ParseNode =>
      def self : ParseNode
      override def file = FileImpl.this.self
      override def isValid = super[ParseNodeImpl].isValid && super[RangeTreeImpl].isValid
      final def owner = self
      protected def updateParse0(context : ParseContext, from : Parsers.this.Parser) : ParseTree = {
        asParseTree
      }
      override protected def repairInner(absolute : Int, offset : Int, added : Int, removed : Int) = {
        super.repairInner(absolute, offset, added, removed)
        if (!noParse(absolute + offset, added, removed))
          dirtyParse
      }
      override def repair(absolute : Int, offset : Int, added : Int, removed : Int) = {
        super.repair(absolute, offset, added, removed)
      }
      type Parser <: Parsers.this.Parser with ParserImpl
      trait ParserImpl extends super.ParserImpl { selfX : Parser =>
        private[Parsers] var lastChild : ParseNode = null.asInstanceOf[ParseNode]
        private var childLength : Int = 0
        
        def self : Parser
        def node = ParseNodeImpl.this.self
        protected def synch(relative0 : Int, node : ParseNode) : Unit
        protected def doDestroy = !hasParseErrors
        protected def indirect(relative0 : Int, txt : ParseContext) : ParseTree = {
          assert(lastChild == null || (lastChild.parent0 == this.node && 
            relative0 >= lastChild.relative + lastChild.length))
          val existing = if (lastChild == null) this.node.child0 else lastChild.next0
          var indirect0 : Option[ParseNode] = if (existing == null) None else Some(existing)
          def doDestroy0 = {
            val next = indirect0.get.next0
            if (doDestroy) indirect0.get.destroy(indirect0.get.absolute)
            if (next == null) None else Some(next)
          }
          while (indirect0.isDefined && 
                 indirect0.get.relative < relative0 &&
                 ((indirect0.get.next0 != null && 
                   indirect0.get.next0.relative <= relative0) ||
                  (!indirect0.get.isOnSameLineAs(relative0) || 
                   !indirect0.get.parseContext.compatible(txt)))) {
            indirect0 = doDestroy0  
          }
          indirect0 = if (indirect0.isDefined && indirect0.get.relative <= relative0) {
            indirect0 // reuse
          } else if (indirect0.isDefined && indirect0.get.isOnSameLineAs(relative0) &&
                     indirect0.get.parseContext.compatible(txt)) {
            indirect0 // reuse
          } else None
          
          if (indirect0.isDefined && indirect0.get.length + (indirect0.get.relative - relative0) <= 0) {
            indirect0 = doDestroy0
          }
          if (indirect0.isDefined && indirect0.get.relative != relative0) {
            indirect0.get.length = indirect0.get.length + (indirect0.get.relative - relative0)
            indirect0.get setRelative relative0
            indirect0.get.dirtyParse
          }

          indirect(relative0, indirect0, txt)
        } 
        protected def indirect(relative0 : Int, indirect0 : Option[ParseNode], txt : ParseContext) = {
          val indirect = indirect0 getOrElse {
            this.node.create(relative0)
          }
          assert(indirect.parent0 == this.node)
          //assert(node.parent0 == this.node)
          val result = indirect.updateParse(txt, self)
          assert(lastChild == null || lastChild.parent0 == this.node)
          assert(indirect.parent0 == this.node)
          assert(indirect.hasLength)
          childLength = childLength + indirect.length // add it
          val relative1 = relative0 + indirect.length
          assert(relative1 > relative0)
          synch(relative1, indirect)
          // cleanup
          //assert(hasParseErrors || magicProcessor != null || indirect.prev0 == lastChild)
          lastChild = indirect
          result
        }
        def deflate(offset : Int) = {
          if (offset >= childLength) {
            val ret = offset - childLength
            assert(ret == node.deflate(offset))
            ret
          } else node.deflate(offset) // looking back 
        }
      }
      protected def contentForParse = file.content.drop(self.absolute)
      
      def dirtyParse : Unit = parses.dirty += self
      
      override def doParse : Unit = if (parses.dirty.remove(self) || !hasLength) {
        val hadParseErrors = hasParseErrors
        flushErrors{_.isInstanceOf[ParseError]} 
        val content = contentForParse
        val parser = Parser(content, parseContext)
        val (length,changeParent) = parseInner(parser)
        if (length == 0) { // reset.
          enclosing.foreach(_.dirtyParse)
          return
        }
        assert(length > 0)
        if (true || !hasParseErrors) {
          if (parser.lastChild != null) 
            parser.lastChild.last // last child
            else clearChildren // no children parsed
        }
        if (hasParseErrors) {
          if (!hasLength) this.length = length
          else if (length < this.length) {
            this.length = length
            enclosing.foreach(_.dirtyParse)
          }
        } else if (!hasLength || length != this.length) {
          this.length = length
          enclosing.foreach(_.dirtyParse)
        } else if (changeParent) {
          enclosing.foreach(_.dirtyParse)
        }
        if (hadParseErrors && !hasParseErrors) parseChanged
        
        // just in case any children dirty us
        parses.dirty remove self
      }
      final override def destroyed(absolute : Int) = {
        flushErrors(e => true)
        super.destroyed(absolute)
        destroy0
      }
    }
    def ParseNode : ParseNode
    
    def enclosingParse(offset : Int) = parses.enclosing(offset)
    //implicit def r2p(range : Option[parses.Range]) : Option[ParseNode] = range.map(_.position)
    def doParsing = {
      ptimer.reset
      ptimer.disable
      val timer = new lampion.util.BenchTimer
      while (!parses.dirty.isEmpty) {
        val next = parses.dirty.elements.next
        if (next.isValid) {
          next.doParse
          if (!next.hasLength) {
            logError("no length", null)
          }
        }
        else parses.dirty remove next
      }
      val time = timer.elapsed
      if (time > .05) Console.println("PARS: " + ptimer.elapsedString)
    }
    override def repair(offset : Int, added : Int, removed : Int) = {
      super .repair(offset, added, removed)
      parses.repair(offset, added, removed)
    }
    override def doUnload = {
      super.doUnload
      assert(!editing)
      parses.destroy
    }
    override def clear = {
      super.clear
      parses.clear
    }
  }
  protected def doAfterParsing : Unit
  //protected def checkYieldJob : Unit // called in the context of typing.
  //protected def doYieldJob[T](f : => T) : T // pauses typing
  protected def lockTyper[T](f : => T) : T
  protected def afterParsing = {}
}

    
