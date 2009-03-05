/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.compiler

import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.util.{ PositionBank, CachedPositionBank }

trait NewMatchers {
  val plugin = ScalaPlugin.plugin
  
  type File <: FileImpl
  trait FileImpl {

    def self : File
    
    def file = self
    
    def content : RandomAccessSeq[Char]
    
    private var editing0 = false
    
    final def editing = editing0
    
    final def editing_=(value : Boolean) =
      if (!editing0 && value) {
        editing0 = value
        prepareForEditing
      } else if(editing0 && !value)
        editing0 = value
    
    def prepareForEditing = {}
    
    def doLoad : Unit = {}
    
    def loaded = editing0 = false
    
    def unloaded = editing0 = false

    def isLoaded : Boolean

    def defaultMatch : OpenMatch
    protected def adjustDamage(from : Int, until : Int) : (Int,Int) = (from,until)

    def repair(offset : Int, added : Int, removed : Int) : Unit = {
      look = Nil
      openMatches .repair(offset, added, removed)
      closeMatches.repair(offset, added, removed)
      repair0(offset, offset + added)
    }
    def doUnload = {
      openMatches.destroy
      closeMatches.destroy
      editing0 = false
    }
    def clear = {
      openMatches.clear
      closeMatches.clear
    }
    private def repair0(from1 : Int, until1 : Int) : Unit = {
      var toUnmatch = List[PositionBank#PositionImpl]()
      class Damaged {
        var from0 = from1
        var until0 = until1
        def from = from0
        def from_=(x : Int) = if (x < from0) from0 = x 
        def until = until0
        def until_=(x : Int) = if (x > until0) until0 = x
        override def toString = from + "-" + until
      }
      val damaged = new Damaged
      look.foreach{
      case m if !m.isValid => 
      case m =>   
        val n = m.absolute
        damaged.until = n
        damaged.from = n
      }
      look = Nil
      ({
        val (x,y) = adjustDamage(damaged.from,damaged.until)
        damaged.from = x; damaged.until = y
      })
      // nothing todo
      if (damaged.from == damaged.until) return

      var stack = List[(Open,List[PositionBank#PositionImpl])]()
      var top : OpenMatch = null
      val damagedLength = damaged.until - damaged.from
      var enclosing = enclosing0(damaged.from, false)(n => stack = (n,Nil) :: stack)
      var content : RandomAccessSeq[Char] = null
      def refreshTop = {
        top = if (!stack.isEmpty) stack.head._1.kind 
              else if (enclosing.isDefined) enclosing.get.kind else defaultMatch
        assert(top != null)
      }
      def resetLimit = {
        val limit = if (enclosing.isDefined) 
                      if (damaged.until > enclosing.get.matching.absolute) damaged.until
                      else enclosing.get.matching.absolute 
                    else this.content.length
        assert(limit > damaged.from)
        content = this.content.slice(damaged.from, limit)
      }
      def enclosingUp = {
        var newStack = List[Open]()
        enclosing = enclosing0(enclosing.get.absolute, false)(n => newStack = n :: newStack) 
        if (!newStack.isEmpty) 
          stack = stack ::: newStack.reverse.map(n => (n,Nil))
        resetLimit
      }
      
      // flip the stack.
      stack = stack.reverse
      var newStack = List[(Open,List[PositionBank#PositionImpl])]()
      while (enclosing.isDefined && enclosing.get.matching.absolute < damaged.until) {
        newStack = (enclosing.get,Nil) :: newStack
        enclosingUp
      }
      if (!newStack.isEmpty) {
        stack = stack ::: newStack.reverse
      }
      
      refreshTop
      val prev0 = if (damaged.from == 0) 0.toChar else file.content(damaged.from - 1)
      resetLimit
      var idx = 0
      while (idx < damagedLength || (!stack.isEmpty && idx < content.length)) {
        val oldIdx = idx
        var closeIdx = -1
        val atOpen  = openMatches.find(damaged.from + idx)
        var opened = false
        def doMatchOpen(kind : OpenMatch) = {
          opened = true
          val open = openMatches.create(damaged.from + idx)
          //open.disconnect
          // could possibly have close....
          if (open.kind != kind) {
            open.disconnect
            open.kind = kind
          }
          //open.kind = kind
          //assert(open.matching == null)
          stack = (open,toUnmatch) :: stack
          toUnmatch = Nil
          refreshTop
        }
        val content0 = content.drop(idx)
        top.check(content0) match {
        case NoMatch(skip) => idx += skip
        case MatchOpen(kind) if atOpen.isDefined && 
          atOpen.get.kind == kind && atOpen.get.matching != null && idx >= damagedLength =>
          opened = true
          idx = atOpen.get.matching.absolute - damaged.from
          closeIdx = idx
        case MatchOpen(kind) => 
          doMatchOpen(kind)
          idx += kind.open.length
        case RejectOpen =>
          opened = true // don't destroy this line, don't advance index
          if (!stack.isEmpty) { // just delete read away.
            stack.head._1.unmatched
            toUnmatch = stack.head._2
            stack = stack.tail
          } else {
            enclosing.get.unmatched
            toUnmatch = Nil
            enclosingUp
          }
          refreshTop
        case UnmatchedClose(kind) =>
          closeIdx = idx + kind.close.length
          val closing = closeMatches.create(damaged.from + idx + kind.close.length)
          if (closing.kind != kind) {
            closing.kind = kind
          }
          if (closing.matching != null) {
            if (!(!stack.isEmpty && stack.head._1 != closing.matching)) {
              // TODO: fix this condition, will occur [for (val x <- positions.filter({<--))], see #704
              //logError("matching is off!", null)
            }
            while (!stack.isEmpty && stack.head._1 != closing.matching) {
              toUnmatch = stack.head._2
              stack.head._1.unmatched
              stack = stack.tail
            }
            if (!stack.isEmpty) {
              assert(stack.head._1 == closing.matching)
              stack = stack.tail
            } else {
              if (!(enclosing.isDefined && enclosing.get == closing.matching)) {
                // TODO: fix this condition, will occur if [def foo("<--) {}], see #702
                //logError("matching is off?", null)
                assert(this != null)
              }
            }
          } else {
            closing.unmatched
          }
          idx = closeIdx
        case m @ MatchClose() =>
          val close = top.close
          closeIdx = idx + close.length
          toUnmatch.foreach{_.destroy}
          val closing = closeMatches.create(damaged.from + idx + close.length)
          if (closing.kind != top) {
            closing.kind = top
          }
          if (!stack.isEmpty && closing.matching == stack.head._1) {
            toUnmatch = stack.head._2
            stack = stack.tail
          } else if (stack.isEmpty && enclosing.isDefined && closing.matching == enclosing.get) {
            // nothing
            
          } else if (enclosing.isDefined && closing.matching == enclosing.get) {
            assert(!stack.isEmpty)
            val head = stack.head
            stack = stack.tail
            closing.disconnect
            stack = stack ::: ((enclosing.get,Nil) :: Nil) // to the end
            head._1.disconnect
            toUnmatch = head._2
            head._1.matching = closing
            closing.matching = head._1
            head._1.matched
            closing.matched
            enclosingUp
          } else if (!stack.isEmpty) {
            assert(closing.matching != stack.head._1)
            closing.disconnect
            stack.head._1.disconnect
            toUnmatch = stack.head._2
            stack.head._1.matching = closing
            closing.matching = stack.head._1
            stack.head._1.matched
            closing.matched
            stack = stack.tail
          } else if (enclosing.isDefined) {
            enclosing.get.disconnect
            closing.disconnect
            enclosing.get.matching = closing
            closing.matching = enclosing.get
            enclosing.get.matched
            closing.matched
            toUnmatch = Nil
            enclosingUp
          }
          (m.checkAgain,atOpen) match {
          case (Some(check),Some(atOpen)) if atOpen.kind == check && atOpen.matching != null => 
            idx = atOpen.matching.absolute - damaged.from
            closeIdx = idx
          case (Some(check),_) => doMatchOpen(check); idx = closeIdx
          case _ => refreshTop; idx = closeIdx
          }
        }
        assert(idx >= oldIdx)
        if (idx > oldIdx) {
          val atClose = closeMatches.find(damaged.from + idx)
          if (!opened && !atOpen.isEmpty) 
            toUnmatch = atOpen.get :: toUnmatch
          if (closeIdx != idx && !atClose.isEmpty) 
            toUnmatch = atClose.get :: toUnmatch
        }
      }
      if (stack.isEmpty) toUnmatch.foreach(_.destroy)
      else {
        stack.foreach(p => if (p._1.matching == null) p._1.unmatched)
        toUnmatch.foreach{
        case p : openMatches.Position if p.matching == null => 
          p.unmatched
        case p : closeMatches.Position if p.matching == null =>
          p.unmatched
        case _ =>
        }
      }
      if (!look.isEmpty)
        return repair0(damaged.from, damaged.until) // where we left off
      else return
    }
    
    def isMatched = unmatched.isEmpty
    
    def borderNext(offset : Int) = if (offset < 0) None else {
      val open = openMatches.seek(offset)
      if (open.isDefined && open.get.absolute == offset && open.get.matching != null) 
        Some(Match(open.get.kind, open.get.absolute, open.get.matching.absolute))
      else None
    }
 
    def borderPrev(offset : Int) = if (offset < 0) None else {
      val close = closeMatches.seek(offset)
      if (close.isDefined && close.get.absolute == offset && close.get.matching != null) 
        Some(Match(close.get.matching.kind, close.get.matching.absolute, close.get.absolute))
      else None
    }
    
    def unmatchedOpen(offset : Int) : Option[OpenMatch] = if (offset >= 0 && !unmatched.isEmpty) {
      val pos = openMatches.seek(offset)
      if (pos.isDefined && offset == pos.get.absolute + pos.get.kind.open.length - 1 && pos.get.matching == null)
        Some(pos.get.kind)
      else None
    } else None
    
    def isUnmatchedOpen(offset : Int) : Boolean = {
      val pos = openMatches.seek(offset)
      if (pos.isDefined && offset == pos.get.absolute && pos.get.matching == null) { 
        true
      } else false
    }
    def isUnmatchedClose(offset : Int) : Boolean = {
      val pos = closeMatches.seek(offset)
      if (pos.isDefined && offset == pos.get.absolute && pos.get.matching == null) { 
        true
      } else false
    }
    def matchedClose(offset : Int) : Option[Match] = {
      val pos = closeMatches.seek(offset)
      if (pos.isDefined && offset == pos.get.absolute && pos.get.matching != null) { 
        Some(Match(pos.get.matching))
      } else None
    }
    private def unique = if (unmatched.length == 1) Some(unmatched(0)) else None
    abstract class Rebalance
    case class CompleteBrace(kind : OpenMatch, close : String) extends Rebalance
    case class DeleteClose(from : Int, length : Int) extends Rebalance
    def makeCompleted(offset : Int) : Unit = {
      val close = closeMatches.find(offset)
      if (close.isEmpty) return plugin.logError("no completed at " + offset, null)
      completed.put(close.get,())
    }
    def isCompleted(offset : Int) : Boolean = {
      val close = closeMatches.find(offset)
      if (close.isEmpty) return true
      completed.get(close.get).isDefined
    }
    
    def unmakeCompleted(offset : Int) : Unit = {
      val close = closeMatches.find(offset)
      if (close.isEmpty) return plugin.logError("no completed at " + offset, null)
      completed removeKey close.get
    }
    
    def rebalance(endOffset : Int) : Option[Rebalance] = (unmatched.find(_.isInstanceOf[openMatches.Position]) match {
    case Some(open : openMatches.Position) if endOffset >= open.kind.open.length => 
      // find open brace
      val at = openMatches.find(endOffset - open.kind.open.length)
      if (at.isEmpty || at.get.kind != open.kind) None
      else if (at.get == open) open.kind.close match {
      case null => None
      case close => Some(CompleteBrace(open.kind, close))
      } else {
        assert(at.get.matching != null) // must be matched, but we might have borrowed open's match
        val openEnclosing = enclosing(open.absolute)
        var atEnclosing = enclosing(at.get.absolute)
        while (openEnclosing != atEnclosing && 
               atEnclosing.isDefined && atEnclosing.get.kind == open.kind) 
          atEnclosing = enclosing(atEnclosing.get.from)
        if (openEnclosing == atEnclosing) Some(CompleteBrace(at.get.kind, at.get.kind.close))
        else None
      }
    case _ => None
    }) orElse (unmatched.find(_.isInstanceOf[closeMatches.Position]) match {
    case Some(close : closeMatches.Position) =>  
      val at = closeMatches.find(endOffset)
      def closeText(at : closeMatches.Position) = at.matching.kind.close
      if (at.isEmpty) None
      else if (at.get != close && {
        assert(at.get.matching != null) 
        val closeText0 = closeText(at.get)
        if (content.take(close.absolute).endsWith(closeText0)) false else true
      }) None
      else { // close/at have the same close text.
        var prev = at.get.prev0
        while (prev.isDefined && prev.get.matching != null && {
          val closeText0 = closeText(prev.get)
          if (content.take(endOffset).endsWith(closeText0)) false else true
        }) prev = closeMatches.seek(prev.get.matching.absolute)
        
        if (prev.isDefined && prev.get.matching == null) {
          if (prev.get != close) plugin.logError("parens messed up", null)
          // can erase
          Some(DeleteClose(prev.get.absolute, closeText(at.get).length))
        } else if (prev.isDefined && close == at.get) {
          Some(DeleteClose(prev.get.absolute, closeText(prev.get).length))
        } else if (close == at.get) None
        else if (close.absolute < at.get.absolute) None
        else {
          // ( ( () [)]   (  ) )  [)]
          var atEnclosing = enclosing(at.get.absolute)
          val closeEnclosing = enclosing(close.absolute)
          var last = close
          while (atEnclosing != closeEnclosing && atEnclosing.isDefined && 
            atEnclosing.get.kind == at.get.matching.kind) {
            if (last == close) last = closeMatches.find(atEnclosing.get.until).get
            atEnclosing = enclosing(atEnclosing.get.from)
          }
          assert(at.get.matching != null)
          if (atEnclosing != closeEnclosing) None
          else if (prev.isDefined && prev.get.absolute > at.get.matching.absolute)  
            Some(DeleteClose(prev.get.absolute, closeText(prev.get).length)) 
          else Some(DeleteClose(last.absolute, closeText(at.get).length))
        }
      }
    case _ => None
    })
        
        
    
    def prevCloseMatch(offset : Int) : Option[Match] = if (offset < 0) None else {
      val open = openMatches.seek(offset)
      val close = closeMatches.seek(offset)
      if (!close.isDefined) return None
      if (open.isDefined && open.get.absolute > close.get.absolute) return None
      if (close.get.matching == null) return None
      return Some(Match(close.get.matching))
    }
    
    
    private def Match(open : openMatches.Position) : Match =
      Match(open.kind, open.absolute, open.matching.absolute)

    def findNext(offset : Int) = {
      var cursor = openMatches.seek(offset)
      if (cursor.isDefined && cursor.get.absolute < offset) 
        cursor = cursor.get.next0
      while (cursor.isDefined && cursor.get.matching == null) cursor = cursor.get.next0  
      cursor.map(Match)
    }
      
    case class Match(kind : OpenMatch, from : Int, until : Int) {
      def open = kind.open
      def close = kind.close
      def next = {
        var cursor = openMatches.seek(until)
        var close = closeMatches.seek(until).get.next0
        while (close.isDefined && close.get.matching == null) close = close.get.next0
        assert(cursor.isDefined)
        if (cursor.get.absolute < until) 
          cursor = cursor.get.next0
        while (cursor.isDefined && cursor.get.matching == null) cursor = cursor.get.next0
        if (cursor.isDefined && close.isDefined && cursor.get.absolute > close.get.absolute) Match(close.get.matching) 
        else cursor.map(Match)
      }
      
      
      def within : List[Match] = {
        var ret = List[Match]()
        var cursor = openMatches.seek(from)
        assert(cursor.isDefined && cursor.get.absolute == from)
        cursor = cursor.get.next0
        while (cursor.isDefined && cursor.get.absolute < until) {
          if (cursor.get.matching != null) {
            ret = Match(cursor.get) :: ret
            cursor = openMatches.seek(ret.head.until)
            if (cursor.get.absolute != ret.head.until) {
              cursor = cursor.get.next0
            }
          } else cursor = cursor.get.next0
        }
        ret.reverse
      }
    }
    private def enclosing0(offset : Int, adjacentOk : Boolean)(f : => Open => Unit) : Option[Open] = {
      var open = openMatches.seek(offset)
      var close = closeMatches.seek(offset)
      while ({
        if (close.isDefined && close.get.matching == null) { close = close.get.prev0; true }
        else if (close.isDefined && close.get.absolute > open.get.absolute) {
          open = if (adjacentOk) Some(close.get.matching) else close.get.matching.prev0
          val c0 = close.get.matching.absolute
          val c1 = close.get.absolute
          assert(c0 < c1)
          val newClose = closeMatches.seek(close.get.matching.absolute)
          assert(newClose.isEmpty || newClose.get.absolute < close.get.absolute)
          close = newClose
          true
        } else if (open.isDefined && (open.get.matching == null || offset > open.get.matching.absolute || 
                   (!adjacentOk && (offset == open.get.absolute || offset == open.get.matching.absolute)))) {
          if (f != null && open.get.matching == null && (adjacentOk || open.get.absolute < offset)) f(open.get)
          open = open.get.prev0
          true
        } else false
      }) {}
      open
    }
    def enclosing(offset : Int) = enclosing0(offset,false)(null).map(pos => Match(pos.kind, pos.absolute, pos.matching.absolute))
    def  adjacent(offset : Int) = enclosing0(offset, true)(null).map(pos => Match(pos.kind, pos.absolute, pos.matching.absolute))

    def findMatchPrev(offset : Int) : Option[Match] = {
      val open = openMatches.seek(offset)
      if (open.isDefined && open.get.absolute == offset && open.get.matching != null)
        Some(Match(open.get.kind, open.get.absolute, open.get.matching.absolute))
      else 
        None
    }
    
    def findMatchNext(offset : Int) : Option[Match] = {
      val close = closeMatches.seek(offset)
      if (close.isDefined && close.get.absolute == offset && close.get.matching != null)
        Some(Match(close.get.matching.kind, close.get.matching.absolute, close.get.absolute))
      else 
        None
    }

    def enclosing(offset : Int, until : Int) : Option[Match] = {
      var enclosing = FileImpl.this.enclosing(offset)
      while (enclosing.isDefined && (enclosing.get.until <= until)) {
        enclosing = FileImpl.this.enclosing(enclosing.get.from)
      }
      enclosing
    }
    private var unclosed = List[Open]()
    private var look = List[PositionBank#PositionImpl]()
    private trait PositionImpl {
      def matching : PositionImpl
      def isValid : Boolean
    }
    protected def addUnmatched(offset : Int, length : Int) : Unit
    protected def removeUnmatched(offset : Int) : Unit
    private var unmatched = List[PositionBank#PositionImpl]()
    private sealed abstract class PositionBank extends CachedPositionBank {
      val other : PositionBank
      type Position <: PositionImpl
      trait PositionImpl extends super.PositionImpl with FileImpl.this.PositionImpl {
        def self : Position
        var matching : other.Position = _
        var kind : OpenMatch = _
        def disconnect = {
          if (matching != null) {
            look = matching :: look
            assert(matching.matching == self)
            matching.matching = null.asInstanceOf
            matching = null.asInstanceOf
          }
        }
        def matched = {
          val list = FileImpl.this.unmatched.filter(_ != self)
          if (list ne FileImpl.this.unmatched) {
            removeUnmatched(at(absolute))
            FileImpl.this.unmatched = list
          }
        }
        protected def at(absolute : Int) : Int
        protected def length : Int
        def unmatched = if (!FileImpl.this.unmatched.exists(_ == self)) {
          disconnect
          assert(matching == null)
          addUnmatched(at(absolute), length)
          FileImpl.this.unmatched = self :: FileImpl.this.unmatched
        }
        override def destroyed(absolute : Int) = {
          super.destroyed(absolute)
          if (matching != null) disconnect
          val list = FileImpl.this.unmatched.filter(_ != self)
          if (FileImpl.this.unmatched ne list) {
            FileImpl.this.unmatched = list
            removeUnmatched(at(absolute)) // could fail!
          }
        }
      }
    }
    private type Open = openMatches.Position
    private object openMatches extends PositionBank {
      lazy val other : closeMatches.type = closeMatches
      class Position extends PositionImpl {
        def self = this
        override def toString = super.toString + ":" + kind + (if (matching == null) " unmatched" else "")
        protected override def at(absolute : Int) = (absolute)
        protected override def length = kind.open.length
      }
      def Position = new Position
    }
    private type Close = closeMatches.Position
    private val completed = new collection.jcl.WeakHashMap[Close,Unit]()
    private object closeMatches extends PositionBank {
      lazy val other : openMatches.type = openMatches
      protected override def pushBack = false
      class Position extends PositionImpl {
        def self = this
        protected override def at(absolute : Int) = (absolute) - length
        protected override def length = kind.close.length
        override def toString = super.toString + (if (matching == null) " unmatched " + kind else matching.toString)
      }
      def Position = new Position
    }
  }
  trait OpenMatch {
    def open : String
    def close : String
    protected def checkFirst(content : RandomAccessSeq[Char]) : Option[Int] = None
    protected def checkLast(content : RandomAccessSeq[Char]) : MatchAnswer = NoMatch(1)
    final private[NewMatchers] def check(content : RandomAccessSeq[Char]) : MatchAnswer = checkFirst(content) match{
    case Some(length) => assert(length > 0); NoMatch(length)
    case None => checkLast(content)
    }
     
  }
  sealed abstract class MatchAnswer
  case class NoMatch(skip : Int) extends MatchAnswer
  object RejectOpen extends MatchAnswer
  case class MatchOpen(kind : OpenMatch) extends MatchAnswer
  case class MatchClose extends MatchAnswer {
    def checkAgain : Option[OpenMatch] = None
  }
  case class UnmatchedClose(kind : OpenMatch) extends MatchAnswer {
    
  }
}
