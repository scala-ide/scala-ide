/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.editor 

trait PresentationForDriver extends TypersPresentations 
trait Driver extends TypersPresentations {
  type Project <: ProjectImpl
  trait ProjectImpl extends super.ProjectImpl {  
    trait Node extends NodeImpl   
    trait ParseNode extends Node with ParseNodeImpl {
      def self : ParseNode
    } 
    trait IdentifierPosition extends IdentifierPositionImpl

    type File <: FileImpl
    trait FileImpl extends super.FileImpl {selfX:File=>
      class IdentifierPosition extends ProjectImpl.this.IdentifierPosition with IdentifierPositionImpl {
        override def self = this
      }
      override def IdentifierPosition = new IdentifierPosition
      class ParseNode extends ProjectImpl.this.ParseNode with ParseNodeImpl {
        def self = this
        makeNoChanges
      }
      def ParseNode = new ParseNode
      override def Token(offset : Int, text : RandomAccessSeq[Char], code : Int) = new Token(offset : Int, text : RandomAccessSeq[Char], code : Int)
      class Token(val offset : Int, val text : RandomAccessSeq[Char], val code : Int) extends TokenImpl {
        def self = this
      }
    }
  }
}
