/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.core;
import scala.collection.jcl._

trait Positions extends Files with Dirs {
  trait PositionWithFile {
    def file : File
  }
  type File <: FileImpl
  trait FileImpl extends super.FileImpl {
    def self : File
    trait PositionBank extends Repairable {
      protected def pushBack = true
      def destroy : Unit = { while (head.next != null) destroy(head.next) }
      def clear : Unit =  {
        var x = head.next
        while (x != null) {
          x.prev = null
          x.offset = 0
          val y = x.next
          x.next =null.asInstanceOf[Position]
          x = y
        }
        cursor.clear
      }
      def remove(offset : Int) =  { cursor.position(offset) match {
      case Some(pos) if pos.absolute == offset => destroy(pos)
      case _ => 
      }}
      def seek(offset : Int) = /*synchronized*/ { cursor.position(offset) }
      def find(offset : Int) = seek(offset) match {
      case ret @ Some(pos) if pos.absolute == offset => ret 
      case _ => None
      }
      
      private def destroy(pos : Position) : Unit = if (pos.isValid) {
        //val absolute = 
        val absolute = cursor.seek(pos)       
        val ret = cursor.go(PREV)
        assert(ret)
        assert(pos.offset >= 0)
        pos.next match {
        case null =>
        case next => 
          assert(next.offset > 0)
          next.offset = next.offset + pos.offset
          assert(next.offset > 0 || (next.offset == 0 && next.prev == head))
          assert(next.prev == pos)
          next.prev = pos.prev
        }
        pos.prev.next = pos.next
        pos.prev = null
        pos.next = null.asInstanceOf[Position]
        pos.offset = 0
        pos.destroyed(absolute)
      }
      def create(offset : Int) : Position = /*synchronized*/ {cursor.position(offset) match {
      case Some(position) if position.absolute == offset => position
      case at0 =>
        val at = at0 getOrElse head
        val atAbsolute = at.absolute
        cursor.position(atAbsolute)
        //assert(atAbsolute < offset)
        val ret = Position
        ret.prev = at
        ret.next = at.next
        ret.offset = offset - atAbsolute
        assert(ret.offset > 0 || (ret.offset == 0 && ret.prev == head))
        
        at.next = ret
        ret.next match {
        case null => 
        case pos => // added....
          assert(pos.offset > ret.offset)
          assert(pos.prev == at)
          pos.prev = ret
          pos.offset = pos.offset - ret.offset
          assert(pos.offset > 0 || (pos.offset == 0 && pos.prev == head))
        }
        cursor.seek(ret)
        ret
      }}
      override def repair(offset : Int, added : Int, removed : Int) : Unit = /*synchronized*/ {
        if (false && added == removed) // can't do this in all cases!
          return
        if (removed > 0) {
          while ({ 
            val pos = cursor.position(offset + removed)
            if (pos.isDefined &&  offset + removed != pos.get.absolute &&
                                 (offset <  pos.get.absolute || 
                    (pushBack &&  offset == pos.get.absolute))) {
              pos.get.destroy
              true
            } else if (pos.isDefined && !pushBack && offset + removed == pos.get.absolute) {
              pos.get.destroy
              true
            } else if (pos.isDefined && offset + removed == pos.get.absolute) {
              while (removed >= pos.get.offset) 
                pos.get.prev.asInstanceOf[PositionImpl].destroy
              cursor.seek(pos.get)
              cursor.go(PREV)
              pos.get.offset = pos.get.offset - removed
              assert(pos.get.offset > 0 || (pos.get.offset == 0 && pos.get.prev == head))
              false
            } else {
              val at = pos getOrElse head
              if (pos.isDefined) {
                assert(offset + removed > pos.get.absolute)
                assert(offset >= pos.get.absolute)
              }
              at.next match {
              case null =>
              case next =>
                assert(next.offset > removed)
                next.offset -= removed
              }
              false
            } 
          }) {}
        }
        if (added > 0) { // push in the gap!
          val pos = cursor.position(offset)
          assert(pos.isEmpty || pos.get.isValid)
          assert(pos.isEmpty || pos.get.absolute <= offset)
          if (pos.isDefined && pos.get.absolute == offset && pushBack) {
            cursor.go(PREV)
            pos.get.offset = pos.get.offset + added
            assert(pos.get.offset > 0 || (pos.get.offset == 0 && pos.get.prev == head))
          } else {
            val at = pos getOrElse head
            at.next match {
            case null =>
            case next =>
              assert(next.offset > 0)
              assert(next.prev == at)
              next.offset = next.offset + added
              assert(next.offset > 0 || (next.offset == 0 && next.prev == head))
            }
          }
        }
      }
      private object cursor {
        private var offset : Int = 0
        private var at : HasNextPosition = head
        def go(dir : Dir) : Boolean = {checkAccess && {(at) match {
        case (`head`) if dir == PREV => false
        case (at) if dir == NEXT && at.next == null => false
        case (at:PositionImpl) if dir == PREV => 
          // move backwards
          assert(at.offset >= 0)
          this.offset = this.offset - at.offset
          assert(offset >= 0)
          at.prev match {
          case `head` => assert(offset == 0)
          case prev : PositionImpl =>
            assert(prev.offset <= 0)
            prev.offset = -prev.offset // unmark
          }
          this.at = at.prev
          true
        case (`head`) if dir == NEXT => 
          this.at = head.next
          this.offset = this.offset + head.next.offset
          true
        case (at : PositionImpl) if dir == NEXT =>   
          this.at = at.next
          this.offset = this.offset + at.next.offset
          assert(at.offset >= 0)
          at.offset = -at.offset
          true
        case `head` | _ : PositionImpl =>
          assert(dir != NEXT && dir != PREV) // Would be nice if the match compiler could rule this case out
          false
        }}}
        def seek(pos : Position) : Int = /*PositionBank.this.synchronized*/ {
          assert(pos.isValid)
          while (pos.cursorAfter) {
            val ret = go(PREV)
            assert(ret)
          }
          while (at != pos) {
            val ret = go(NEXT)
            assert(ret)
          }
          assert(at == pos)
          this.offset
        }
        def position(offset : Int) : Option[Position] = /*PositionBank.this.synchronized*/ {
          assert(offset >= 0)
          while (this.offset < offset && this.go(NEXT)) {}
          while (this.offset > offset && this.go(PREV)) {}
          assert(this.offset <= offset)
          if (this.offset == 0) { // patch up the zero case
            if (this.at == head && this.at.next != null && this.at.next.offset == 0)
              this.go(NEXT)
          }
          this.at match {
          case `head` => None
          case node : PositionImpl if node.isValid => Some(node.self)  
          }
        }
        def clear = /*PositionBank.this.synchronized*/ {
          this.offset = 0
          this.at = head
          head.next = null.asInstanceOf[Position]
        }
        private[Positions] def offset0 = offset
        private[Positions] def at0 = at
      }
      sealed trait HasNextPosition {
        def isValid : Boolean
        var next : Position = _
        def asPosition : Option[Position]
        def absolute : Int
      }
      type Position <: PositionImpl
      def Position : Position
      trait PositionImpl extends HasNextPosition with Positions.this.PositionWithFile {
        def self : Position
        private[Positions] var offset : Int = _
        private[Positions] def cursorAfter : Boolean = /*PositionBank.this.synchronized*/ { if (offset < 0) true else if (offset > 0) false else {
          if (cursor.offset0 == 0) false else true
        }}
        override def toString = Integer.toString(hashCode, 10 + 'z' - 'a')        
        
        override def file : File = FileImpl.this.self
        private[Positions] var prev : HasNextPosition = _
        def prev0 = prev.asPosition
        def next0 = if (next == null) None else Some(next)
        
        def isValid = prev ne null
        def destroyed(absolute : Int) : Unit = {}
        def asPosition = Some(self)
        final def destroy : Unit = PositionBank.this.destroy(self)
        def absolute = /*synchronized*/ { cursor.seek(self) }
      }
      private object head extends HasNextPosition  {
        def asPosition = None
        def isValid = true
        def absolute = 0
      }
    }
    trait CachedPositionBank extends PositionBank {
      private val cache = new LinkedHashMap[Position,Int]
      private var busy = false
      type Position <: PositionImpl
      trait PositionImpl extends super.PositionImpl {
        def self : Position
        override def absolute = /*CachedPositionBank.this.synchronized*/ { if (!busy) cache.get(self) match {
          case Some(offset) => offset
          case None =>
            val ret = super.absolute
            cache(self) = ret; ret
        } else super.absolute}
        override def toString = super.toString + (cache.get(self) match {
        case None => ""
        case Some(idx) => " (" + idx + ")"
        })
      }
      override def destroy = /*synchronized*/ {
        assert(!busy)
        super.destroy
        assert(!busy)
      }
      override def clear = /*synchronized*/ {
        busy = false
        super.clear
        busy = false
      }
      override def repair(offset : Int, added : Int, removed : Int) = /*synchronized*/ {
        assert(!busy)
        busy = true
        cache.clear
        super.repair(offset, added, removed)
        assert(busy) 
        assert(cache.isEmpty)
        busy = false
      }
    }
    trait TrackDestroyedPositionBank extends PositionBank {
      var destroyed = List[Position]()
      type Position <: PositionImpl
      trait PositionImpl extends super.PositionImpl {
        def self : Position
        override def destroyed(absolute : Int) = /*synchronized*/ {
          super.destroyed(absolute)
          TrackDestroyedPositionBank.this.destroyed = self :: TrackDestroyedPositionBank.this.destroyed
        }
      }
      override def repair(offset : Int, added : Int, removed : Int) = /*synchronized*/ {
        destroyed = Nil
        super.repair(offset, added, removed)
      }
    }
  }
}
