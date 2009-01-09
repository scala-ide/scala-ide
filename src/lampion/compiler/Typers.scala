/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.compiler
import scala.collection.jcl._
import scala.ref._

trait Typers extends Tokenizers {
  case class TypeError(msg : String, isWarning : Boolean) extends ErrorKind {
    def this(msg : String) = this(msg, false)
  }
  type TypeInfo
  type TypedElement <: TypedElementImpl
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
          else if (head.isDefined && head.get.makeNoChanges) {
            assert(true)
            assert(true)
          }
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
  
  trait ParseNodeTypedElement extends TypedElementImpl {
    def parseNode : ParseNode
    protected def migrate(oldInfo : TypeInfo, newInfo : TypeInfo) : TypeInfo = oldInfo
    override def dirtyTyped = if (parseNode.isValid) {
      assert(true)
      assert(true)
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
    override def withFile = Some(parseNode.file)
  }
  
  type ParseNode <: Node with ParseNodeImpl
  trait ParseNodeImpl extends super.ParseNodeImpl { selfX : ParseNode =>
    def self : ParseNode
    private[Typers] var resultTypeInfo0 : TypeInfo = null.asInstanceOf[TypeInfo]
    def resultTypeInfo = resultTypeInfo0 match { 
    case null => None
    case info => Some(info)
    }
    def newTypeInfo : TypeInfo
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
    
    def shouldBeTyped = isValid && !hasParseErrors
    def typeError(pos : ErrorPosition, msg : String, isWarning : Boolean) : Unit =
      if (!isWarning) error(pos, TypeError(msg, isWarning))
      // new vs. old dirty.
    def typeIsDirty = typer.dirty.contains(self)
    def asTypedElement : TypedElement with ParseNodeTypedElement
    def doMagic0(processor : TypedElement, info : => TypeInfo)(f : => Unit) = {
      assert(typer.synchronized{typer.dirty.isEmpty})
      if (typer.typed != null) {
        logError("left over " + typer.typed, null)
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
    def hasTypeErrors = hasErrors(_.isInstanceOf[TypeError])

    protected override def destroy0 = super.destroy0
    override protected def parseChanged : Unit = {
      super.parseChanged
      asTypedElement.dirtyTyped
    }
    protected def typedChanged = {}
    private[Typers] def typedChanged0 = typedChanged
  }
  protected def finishTyping = {
    typer.typed.foreach{
    case (node,info) => 
      if (!node.hasTypeErrors) 
        assert(true)
        node.resultTypeInfo0 = info
    }
  }
  
  protected def flushTyper : Unit = {
    var iteration = 1
    var processed : LinkedHashSet[ParseNode] = null
    // will keep going...
    while (!typer.synchronized{typer.dirty.isEmpty}) {
      if (processed != null && typer.dirty.exists{node =>
        if (!processed.add(node)) { 
          logError("probable infinite typer cycle on " + node, null)
          true
        } else false
      }) {
        logError("breaking probably infinite loop with " + typer.dirty, null)
        typer.dirty.clear
      }
      //probably should already be empty.
      
      typer.synchronized{typer.dirty.retain(_.shouldBeTyped)} // get rid of all the trash.
      val oldSize = typer.dirty.size
      if (!typer.analyzed.isEmpty) typer.analyzed = Nil
      if (typer.typed != null) {
        try {
          logError("TYPED: " + typer.typed, null)
        } catch {
          case e => logError(e)
        }
        typer.typed = null
      }
      typer.typed = new LinkedHashMap[ParseNode,TypeInfo] {
        override def default(key : ParseNode) = {
          assert(key.shouldBeTyped && typer.dirty.contains(key)) // otherwise we don't type check
          key.typedChanged0
          key.flushErrors(_.isInstanceOf[TypeError])
          val ret = key.newTypeInfo
          this(key) = ret
          ret
        }
      }
      typer.synchronized{
        typer.dirty.foreach(typer.typed(_))
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
      if (iteration == 15) {
        assert(true)
        processed = new LinkedHashSet[ParseNode]
      }
    }
  }
  protected def typed : LinkedHashMap[ParseNode,TypeInfo] = {
    val ret = typer.typed
    assert(ret != null)
    ret
  }
  protected def flushTyper0  : Unit
  override protected def afterParsing = {
    super.afterParsing
    flushTyper
  }
}
