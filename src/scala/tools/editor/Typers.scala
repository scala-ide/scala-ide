/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$
 
package scala.tools.editor

import scala.tools.nsc
import nsc.{util,io}
import scala.collection.jcl._
 
trait Typers extends Parsers with lampion.compiler.Typers {
  final override type TypeInfo = List[compiler.Tree]

  import scala.tools.nsc.reporters.Reporter
  class CompilerReporter extends Reporter { // to use with global
    protected def info0(pos : util.Position, msg : String, severity : Severity, force : Boolean) : Unit = pos match {
    case pos : IdentifierPositionImpl if pos.isValid && pos.owner != null => 
      pos.owner.typeError(pos, msg, severity == WARNING)
    case pos => 
      assert(true)
      Console.println("ERROR " + msg + " @ " + pos)
    }
  
  }
  override val compiler : Compiler
  trait Compiler extends scala.tools.nsc.IdeSupport with super.Compiler {self : compiler.type =>
    override def finishTyping = {
      super.finishTyping
      analyzer0.finishTyping
    }
    override def currentClient : ScopeClient = 
      (currentTyped).getOrElse(super.currentClient)
  }
  override def finishTyping = {
    super.finishTyping
    compiler.finishTyping
  }
  object analyzer0 extends nsc.typechecker.Analyzer with scala.tools.nsc.typechecker.IdeSupport {
    lazy val global : compiler.type = compiler
  } 
  type IdentifierPosition <: IdentifierPositionImpl
  trait IdA extends super[Parsers].IdentifierPositionImpl
  trait IdB extends super[Typers].IdentifierPositionImpl
  import scala.tools.nsc.util.{OffsetPosition}
  import scala.tools.nsc.io.{AbstractFile}
  
  trait IdentifierPositionImpl extends IdA with IdB with analyzer0.TrackedPosition {
    def self : IdentifierPosition
    override def owner : ParseNode
    override def asOffset : Option[(Int,AbstractFile)] = 
      if (!isValid) None else offset match {
      case Some(offset) => Some((offset,file.nscFile))
      case None => None
      }
  } 
  trait TypedElement extends TypedElementImpl with compiler.ScopeClient
  override def typed = super.typed
  type ParseNode <: Node with ParseNodeImpl
  trait ParseNodeImpl extends super[Parsers].ParseNodeImpl with super[Typers].ParseNodeImpl with analyzer0.MemoizedTree with TypedElement with ParseNodeTypedElement {selfX : ParseNode => 
    def self : ParseNode 
    override def newTypeInfo = parseTrees.map(compiler.lightDuplicator.transform)
    override def useTrees : List[compiler.Tree] = useTypeInfo 
    override def setUseTrees(uses : List[compiler.Tree]) : Unit = setUseTypeInfo(uses)
    override def lastTyped : List[compiler.Tree] = resultTypeInfo.getOrElse(Nil)
    override def shouldBeTyped = super.shouldBeTyped && typeIsDirty && !hasParseErrors
    override def typeChanged : Unit = if (isValid) {
      enclosing.foreach(_.asTypedElement.dirtyTyped)
    } 
    override def kind = parseContext.pinfo
    private class StubTree extends analyzer0.StubTree {
      def underlying = ParseNodeImpl.this.self
    } 
    override def asParseTree : compiler.StubTree = new StubTree
    override def activate(f : => Unit) : Unit = {
      val processor = self
      (new processor.DoType[Unit] {
        def doType0 = f
      }).apply
    }
    def typeError(msg : String) : Unit = {
      typeError(parseTrees.head.pos.asInstanceOf[IdentifierPosition], msg, false)
    }
    override def parseNode = self
    override def asTypedElement : ParseNodeImpl.this.type = this
    override def changed : Unit = { super.changed; if (!typeIsDirty) dirtyTyped }
    override def pos = identifierPosition(0)
    override def typeIsDirty : Boolean = super.typeIsDirty
    protected override def migrate(oldInfo : List[compiler.Tree], newInfo : List[compiler.Tree]) : List[compiler.Tree] = {
      if (oldInfo == null || oldInfo.isEmpty) newInfo
      else {
        assert(true)
        assert(true)
        if (newInfo.last.hasSymbol) (oldInfo.last.symbol,newInfo.last.symbol) match {
        case (sym,compiler.NoSymbol|null) => if (sym != null && sym != compiler.NoSymbol &&
                                                 !sym.isError) newInfo.last.symbol = sym
        case _ => 
        }
        (oldInfo.last.tpe,newInfo.last.tpe) match {
        case (tpe,compiler.NoType|null) => if (tpe != null && tpe != compiler.NoType && !tpe.isError) newInfo.last.tpe = tpe
        case _ =>
        } 
        newInfo
      }
    }
    private[Typers] def initAsFirst = {
      mode = analyzer0.EXPRmode
      pt = compiler.WildcardType
      namerTxt = analyzer0.rootContext(file.unit)
      typerTxt = namerTxt
      asTypedElement.dirtyTyped
    }
  }
  private def lockTyper0[T](f : => T) = lockTyper(f)
  
  type File <: FileImpl
  trait FileA extends super[Parsers].FileImpl {selfX:File=>}
  trait FileB extends super[Typers] .FileImpl {selfX : File =>}
  trait FileImpl extends FileA with FileB {selfX:File=>
    def self : File
    protected def p2i(pos : util.Position) = pos match {
    case pos : IdentifierPositionImpl => Some(pos.self)
    case _ => None
    }
    override def prepareForEditing = lockTyper0{
      super.prepareForEditing
      // the top node.
      import scala.tools.nsc.io._
      val nscFile = this.nscFile.asInstanceOf[PlainFile]
      val node : ParseNode = parses.create(0)
      node.initAsFirst
    }
    
    
    // since editing has begun
    override def doUnload = {
      lockTyper0{ //
        editing = false
        super.doUnload
      }
      doAfterParsing
    }
    type IdentifierPosition <: Typers.this.IdentifierPosition with IdentifierPositionImpl 
    trait IdentifierPositionImpl extends super[FileA].IdentifierPositionImpl with super[FileB].IdentifierPositionImpl with Typers.this.IdentifierPositionImpl {
      def self : IdentifierPosition
    }
  }
  override protected def flushTyper0: Unit = {
    import compiler._
    new compiler.IdeRun
    compiler.phase = new analyzer0.typerFactory.StdPhase(nsc.NoPhase) {
      def apply(unit : CompilationUnit) = Typers.this.abort
    }
    typed.toList.foreach{
    case (node,info) => node.doNamer
    }
    typed.toList.foreach{
    case (node,info) => node.doTyper
    }
    analyzer0.finishTyping
    compiler.finishTyping
  }
}
