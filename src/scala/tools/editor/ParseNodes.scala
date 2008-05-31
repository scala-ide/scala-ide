/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.editor 
import scala.tools.nsc.ast.parser.Tokens._
import scala.tools.nsc.util
trait ParseNodes extends Tokenizers with lampion.compiler.Parsers {
  import compiler.Tree
  override type ParseTree = Tree
  
  type ParseNode <: Node with ParseNodeImpl
  trait ParseNodeImpl extends super[Tokenizers].ParseNodeImpl with super[Parsers].ParseNodeImpl with util.Position {selfX : ParseNode =>
    def self : ParseNode
    def asParseTree : compiler.StubTree
    private[ParseNodes] def prefix0 : String
    protected override def destroy0 = {
      assert(true)
      super.destroy0 
    } 
    override def dbgString = "XXX"
    override def source = Some(file.unit.source)
    override def offset = Some(absolute)
  }
  type File <: FileImpl
  trait FileImplA extends super[Tokenizers].FileImpl {selfX : File => }
  trait FileImplB extends super[Parsers].FileImpl {selfX : File => }
  trait FileImpl extends FileImplA with FileImplB {selfX : File =>
    def self : File

    type ParseNode <: ParseNodes.this.ParseNode with ParseNodeImpl
    trait ParseNodeImpl extends super[FileImplA].ParseNodeImpl with super[FileImplB].ParseNodeImpl with ParseNodes.this.ParseNodeImpl {selfX : ParseNode =>
      def self : ParseNode
      private[ParseNodes] def prefix0 = prefix
      def offsetFor(deflated : Int) = absolute + inflate(deflated)
    }
  }
}
