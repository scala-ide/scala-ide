/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$
  
package scala.tools.editor

import scala.annotation.unchecked.uncheckedStable
import scala.collection.mutable.{ LinkedHashMap, LinkedHashSet }

import nsc.{util,io} 
import scala.tools.nsc
import scala.tools.eclipse.ScalaPlugin

trait Typers extends Parsers with lampion.compiler.Tokenizers {
  final type TypeInfo = List[compiler.Tree]
  case class TypeError(msg : String, isWarning : Boolean) extends ErrorKind {
    def this(msg : String) = this(msg, false)
  }
  
  import scala.tools.nsc.reporters.Reporter
  class CompilerReporter extends Reporter { // to use with global
    protected def info0(pos : util.Position, msg : String, severity : Severity, force : Boolean) : Unit = pos match {
    case pos : IdentifierPositionImpl if pos.isValid && pos.owner != null => 
      pos.owner.typeError(pos, msg, severity == WARNING)
    case pos => 
      Console.println("error with bad pos(" + pos + "): " + msg)
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
    override def check(condition : Boolean, msg : =>String) = {
      if (!condition) { 
        plugin.logError(msg, null)
      }
      condition
    }
  }
  def finishTyping = {
    typer.typed.foreach{
    case (node,info) => 
      node.resultTypeInfo0 = info
    }
    compiler.finishTyping
  }
  object analyzer0 extends nsc.typechecker.Analyzer with scala.tools.nsc.typechecker.IdeSupport {
    lazy val global : compiler.type = compiler
  } 
  type IdentifierPosition <: IdentifierPositionImpl
  trait IdA extends super[Parsers].IdentifierPositionImpl
  import scala.tools.nsc.util.{OffsetPosition}
  import scala.tools.nsc.io.{AbstractFile}
  
  trait IdentifierPositionImpl extends IdA with analyzer0.TrackedPosition {
    def self : IdentifierPosition
    override def owner : ParseNode
    override def asOffset : Option[(Int,AbstractFile)] = 
      if (!isValid) None else offset match {
      case Some(offset) => Some((offset,file.nscFile))
      case None => None
      } 
  } 
  trait TypedElementImpl {
    def self : TypedElement
    def dirtyTyped : Unit
    def withFile : Option[File]
    def makeNoChanges : Boolean
    trait DoType[T] extends Function0[T] {
      protected def doType0 : T
      def apply : T = {
        //assert(!typer.analyzed.contains(self))
        val head = typer.analyzed.firstOption
        if (head.isDefined  && head.get.makeNoChanges) {}
        else typer.analyzed = TypedElementImpl.this.self :: typer.analyzed
        try {
          doType0
        } finally {
          if (typer.analyzed.head eq TypedElementImpl.this.self)
            typer.analyzed = typer.analyzed.tail
          else Console.println("BAD: " + typer.analyzed + " " + TypedElementImpl.this)
        }
      } 
    }
  }
  private object typer {
    var analyzed = List[TypedElement]()
    val dirty = new LinkedHashSet[ParseNode]
    var typed : LinkedHashMap[ParseNode,TypeInfo] = null
  }
  def analyzed = typer.analyzed
  def currentTyped = typer.analyzed.firstOption
  
  trait TypedElement extends TypedElementImpl with compiler.ScopeClient
  type ParseNode <: ParseNodeImpl
  trait ParseNodeImpl extends super[Parsers].ParseNodeImpl with analyzer0.MemoizedTree with TypedElement {selfX : ParseNode => 
    def self : ParseNode
    
    override def withFile = Some(parseNode.file)
    //Do you believe in magic, in a young girls heart?  .... The music is groovy!  
     def doMagic0(processor : TypedElement, info : => TypeInfo)(f : => Unit) = {
      assert(typer.synchronized{typer.dirty.isEmpty})
      if (typer.typed != null) {
        plugin.logError("left over " + typer.typed, null)
      }
      typer.typed = new LinkedHashMap[ParseNode,TypeInfo] {
        override def default(node : ParseNode) = {
          val ret = node.newTypeInfo
          this(node) = ret
          ret
        }
      }
      try {
        typer.typed(self) = info
        (new processor.DoType[Unit] {
          def doType0 = f
        }).apply
      } finally {
        typer.typed = null
        (typer.synchronized{typer.dirty.clear})
      }
    }
    override protected def parseChanged : Unit = {
      super.parseChanged
      asTypedElement.dirtyTyped
    }
    def hasTypeErrors = hasErrors(_.isInstanceOf[TypeError])
    private[Typers] var resultTypeInfo0 : TypeInfo = null.asInstanceOf[TypeInfo]
    def resultTypeInfo = resultTypeInfo0 match { 
      case null => None
      case info => Some(info)
    }
    //
    def useTypeInfo = {
      assert((currentTyped match {
        case Some(t) => t.makeNoChanges
        case _ => false
      }) || typeIsDirty)
      typer.typed(self)
    }
    def setUseTypeInfo(uses : TypeInfo) : Unit = {
      typer.typed(self) = uses
    }
    def typeError(pos : ErrorPosition, msg : String, isWarning : Boolean) : Unit =
      if (!isWarning) error(pos, TypeError(msg, isWarning))
    def newTypeInfo : TypeInfo = parseTrees.map(compiler.lightDuplicator.transform)
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
      @uncheckedStable val processor = self
      (new processor.DoType[Unit] {
        def doType0 = f
      }).apply
    }
    def typeError(msg : String) : Unit = {
      typeError(parseTrees.head.pos.asInstanceOf[IdentifierPosition], msg, false)
    }
    def parseNode = self
    def asTypedElement : ParseNodeImpl.this.type = this
    override def changed : Unit = { super.changed; if (!typeIsDirty) dirtyTyped }
    override def pos = identifierPosition(0)
    override def typeIsDirty : Boolean = typer.dirty.contains(self)
    protected def migrate(oldInfo : List[compiler.Tree], newInfo : List[compiler.Tree]) : List[compiler.Tree] = {
      if (oldInfo == null || oldInfo.isEmpty) newInfo
      else {
        if (newInfo.last.hasSymbol) (oldInfo.last.symbol,newInfo.last.symbol) match {
        case (sym,compiler.NoSymbol|null) => if (sym != null && sym != compiler.NoSymbol &&
                                                 sym != compiler.ErrorType) newInfo.last.symbol = sym
        case _ => 
        }
        (oldInfo.last.tpe,newInfo.last.tpe) match {
        case (tpe,compiler.NoType|null) => if (tpe != null && tpe != compiler.NoType && tpe != compiler.ErrorType) newInfo.last.tpe = tpe
        case _ =>
        } 
        newInfo
      }
    }
    override def dirtyTyped = if (parseNode.isValid) {
      typer.synchronized{
        typer.dirty += parseNode
        if (typer.typed != null) 
          typer.typed.removeKey(parseNode) match {
          case None =>
          case Some(info) =>
            parseNode.resultTypeInfo0 = migrate(parseNode.resultTypeInfo0, info)
          }
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
  trait FileImpl extends FileA {selfX:File=>
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
    trait IdentifierPositionImpl extends super[FileA].IdentifierPositionImpl with Typers.this.IdentifierPositionImpl {
      def self : IdentifierPosition
    }
  }
  protected def typed : LinkedHashMap[ParseNode,TypeInfo] = {
    val ret = typer.typed
    assert(ret != null)
    ret
  }
  override protected def afterParsing = {
    super.afterParsing
    flushTyper
  }
  
   protected def flushTyper : Unit = {
    var iteration = 1
    var processed : LinkedHashSet[ParseNode] = null
    // will keep going...
    while (!typer.synchronized{typer.dirty.isEmpty}) {
      if (processed != null && typer.dirty.exists{node =>
        if (!processed.put(node)) { 
          plugin.logError("probable infinite typer cycle on " + node, null)
          true
        } else false
      }) {
        plugin.logError("breaking probably infinite loop with " + typer.dirty, null)
        typer.dirty.clear
      }
      //probably should already be empty.
      
      typer.synchronized{typer.dirty.retain(_.shouldBeTyped)} // get rid of all the trash.
      val oldSize = typer.dirty.size
      if (!typer.analyzed.isEmpty) typer.analyzed = Nil
      if (typer.typed != null) {
        try {
          plugin.logError("TYPED: " + typer.typed, null)
        } catch {
          case e => plugin.logError(e)
        }
        typer.typed = null
      }
      typer.typed = new LinkedHashMap[ParseNode,TypeInfo] {
        override def default(key : ParseNode) = {
          assert(key.shouldBeTyped && typer.dirty.contains(key)) // otherwise we don't type check
          
          key.asTypedElement.typeChanged
          key.flushErrors(_.isInstanceOf[TypeError])
          val ret = key.newTypeInfo
          this(key) = ret
          ret
        }
      }
      typer.synchronized{
        typer.dirty.toList.foreach(typer.typed(_))
      }
      flushTyper0
      finishTyping
      typer.synchronized{typer.typed.foreach{
      case (node,_) => typer.dirty -= node
      }}
      
      typer.typed = null
      if (!typer.analyzed.isEmpty) {
        Console.println("BAD_STACK: " + typer.analyzed)
        typer.analyzed = Nil
      }
      iteration += 1
      if (iteration == 15)
        processed = new LinkedHashSet[ParseNode]
    }
  }
   
  protected def flushTyper0: Unit = {
    import compiler._
    new compiler.IdeRun
    compiler.phase = new analyzer0.typerFactory.StdPhase(nsc.NoPhase) {
      def apply(unit : CompilationUnit) = error("phase.apply")
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
