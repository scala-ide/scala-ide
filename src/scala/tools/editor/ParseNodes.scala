/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.editor 

import scala.tools.nsc.ast.parser.Tokens._
import scala.tools.nsc.util

trait ParseNodes extends Tokenizers {
  import compiler.Tree
  override type ParseTree = Tree

  type File <: FileImpl
  trait FileImpl extends super.FileImpl {selfX : File =>
    def self : File

    type ParseNode <: ParseNodes.this.ParseNode with ParseNodeImpl
    trait ParseNodeImpl extends super.ParseNodeImpl with util.Position { selfX : ParseNode =>
      def self : ParseNode
      def asParseTree : compiler.StubTree
      protected override def destroy0 = super.destroy0
      override def dbgString = "XXX"
      override def source = Some(file.unit.source)
      override def offset = Some(absolute)
      def offsetFor(deflated : Int) = absolute + inflate(deflated)
    }
  }
}
