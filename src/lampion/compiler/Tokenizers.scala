/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.compiler

import scala.collection.mutable.LinkedHashSet

import scala.tools.eclipse.util.CachedPositionBank

trait Tokenizers extends Parsers {
  type IdentifierPosition <: IdentifierPositionImpl
  trait IdentifierPositionImpl extends ErrorPosition {
    def self : IdentifierPosition
    def file : File
    def owner : ParseNode
    def destroyed : Unit = {}
    def doDestroy : Unit
    def absolute : Int
    def isValid : Boolean
  }
  
  type ParseNode <: ParseNodeImpl
  trait ParseNodeImpl extends super.ParseNodeImpl {selfX : ParseNode =>
    def self : ParseNode
    def identifierPosition(relative : Int) : IdentifierPosition
    def positions : scala.Collection[IdentifierPosition]
  }
  
  private val orphans = new LinkedHashSet[IdentifierPosition]()
  def destroyOrphans = {
    orphans.toList.foreach(t => if ((t.file ne null) && (t.file.isMatched)) t.doDestroy)
    orphans.clear // reap all non-affiliated identifier positions.
  }
  
  def processOrphans(f : IdentifierPosition => Unit) = orphans.toList.foreach(f)
  
  
  type File <: FileImpl
  trait FileImpl extends super.FileImpl {selfX : File =>
    def self : File
    
    def tokenForFuzzy(offset : Int) : Token
    
    def tokenFor(offset : Int) : Token = tokenForFuzzy(offset)
    
    def tokenFor(offset : Int, until : Int) : Iterator[Token] = new Iterator[Token] {
      var at = offset
      def hasNext = at < until
      def next = {
        val ret = tokenFor(at)
        at += ret.text.length
        ret
      }
    }
    
    override def repair(offset : Int, added : Int, removed : Int) = {
      super.repair(offset, added, removed)
      identifiers.repair(offset, added, removed)
    }
    
    override def doUnload = {
      identifiers.destroy
      super.doUnload
    }
    
    override def clear = {
      super.clear
      identifiers.clear
    }
    
    object identifiers extends CachedPositionBank {
      type Position = IdentifierPosition
      def Position = IdentifierPosition
      override def repair(offset : Int, added : Int, removed : Int) = {
        if (added != removed) super.repair(offset, added, removed)
      }
    }
    
    type IdentifierPosition <: Tokenizers.this.IdentifierPosition with IdentifierPositionImpl
    trait IdentifierPositionImpl extends Tokenizers.this.IdentifierPositionImpl with identifiers.PositionImpl {
      orphans += self
      def self : IdentifierPosition
      private[FileImpl] var owner0 : ParseNode = _
      final def owner = owner0
      private[Tokenizers] def ownerSet = if (owner0 != null) owner0.positions0 else orphans
      
      final def doDestroy : Unit = destroy
      override def destroyed(absolute : Int) : Unit = {
        (this:Tokenizers.this.IdentifierPositionImpl).destroyed
        super[PositionImpl].destroyed(absolute)
        ownerSet -= self
        owner0 = null.asInstanceOf[ParseNode]
      }
      override def isValid = super.isValid
    }
    
    def IdentifierPosition : IdentifierPosition
    type ParseNode <: Tokenizers.this.ParseNode with ParseNodeImpl
    trait ParseNodeImpl extends super.ParseNodeImpl with Tokenizers.this.ParseNodeImpl { selfX : ParseNode =>
      def self : ParseNode
      private[Tokenizers] val positions0 = new LinkedHashSet[IdentifierPosition]()
      override def positions : scala.Collection[IdentifierPosition] = positions0
      override def identifierPosition(relative : Int) : IdentifierPosition = {
        val offset = absoluteFor(relative)
        var ret = identifiers.create(offset)
        if (ret.owner0 != self) {
          ret.ownerSet remove ret
          ret.owner0 = self
          ret.owner0.positions0.add(ret)
        }
        ret
      }
      override protected def destroy0 = {
        super.destroy0
        positions0.foreach{p =>
          assert(p.owner0 == self)
          p.owner0 = null.asInstanceOf[ParseNode]
          orphans add p
        }
        positions0.clear
      }
      override protected def parseInner(parser : Parser) = {
        positions0.foreach{p =>
          assert(p.owner0 == self)
          p.owner0 = null.asInstanceOf[ParseNode]
          orphans add p
        }
        positions0.clear
        // will be recomputed during reparsing.
        super.parseInner(parser)
      }
    }

    type Token <: TokenImpl
    trait TokenImpl {
      def self : Token
      val text : RandomAccessSeq[Char]
      val offset : Int
      def extent = offset + text.length
      
      assert(text.length > 0);
      {
        val clength = content.length
        assert(offset >= 0 && (offset < clength || isEOF))
      }
      
      protected def isEOF : Boolean = false
      
      def prev : Option[Token] = if (offset <= 0) None else Some(tokenForFuzzy(offset - 1))
      
      def next : Option[Token] = if (extent >= content.length) None else Some(tokenFor(extent))
      
      override def toString = "t" + offset + ":" + text.mkString
      
      def enclosingParse : parses.Range = if (!editing) parses.NoRange else parses.find(offset)

      def deflated(enclosing : parses.Range) = {
        assert(!enclosing.isEmpty)
        enclosing.get.deflate(offset - enclosing.from)
      }
      
      def findPrev(f : Token => Boolean) : Option[Token] = prev match {
        case ret @ Some(token) => 
          if (f(token)) ret else token.findPrev(f)
        case None => None
      }
      
      def findNext(f : Token => Boolean) : Option[Token] = next match {
        case ret @ Some(token) => 
          if (f(token)) ret else token.findNext(f)
        case None => None
      }
    }
  }
}
