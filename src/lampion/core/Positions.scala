/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.core

import scala.collection.jcl._

trait Positions {
  type File <: FileImpl
  trait FileImpl {
    def self : File
    
    def file = self
    
    def content : RandomAccessSeq[Char]
    
    def willBuild = {}
    
    def repair(offset : Int, added : Int, removed : Int) : Unit = {}
    
    def startWith(idx : Int, str : String) = {
      var idx0 = idx
      val str0 : RandomAccessSeq[Char] = (str)
      while (idx0 < str.length && idx0 < content.length && str0(idx0) == content(idx0))
        idx0 = idx0 + 1
      idx0 == str.length
    }
    
    private var editing0 = false
    
    final def editing = editing0
    
    final def editing_=(value : Boolean) = if (!editing0 && value) {
      editing0 = value
      prepareForEditing
    } else if(editing0 && !value) editing0 = value
    
    def doUnload : Unit = {
      editing0 = false
    }
    
    def loaded = {
      editing0 = false
      assert(!editing0)
    }
    
    def unloaded = {
      editing0 = false
    }

    def prepareForEditing = {}
    
    def isLoaded : Boolean
    def doLoad : Unit = {}
    def clear = {}
    
    trait PositionBank {
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
        val ret = cursor.goPrev
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
      def repair(offset : Int, added : Int, removed : Int) : Unit = /*synchronized*/ {
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
              cursor.goPrev
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
            cursor.goPrev
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
        
        def goPrev : Boolean = at match {
        case (`head`) => false
        case (at:PositionImpl) => 
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
        }

        def goNext : Boolean = at match {
        case (at) if at.next == null => false
        case (`head`) => 
          this.at = head.next
          this.offset = this.offset + head.next.offset
          true
        case (at : PositionImpl) =>   
          this.at = at.next
          this.offset = this.offset + at.next.offset
          assert(at.offset >= 0)
          at.offset = -at.offset
          true
        }

        def seek(pos : Position) : Int = /*PositionBank.this.synchronized*/ {
          assert(pos.isValid)
          while (pos.cursorAfter) {
            val ret = goPrev
            assert(ret)
          }
          while (at != pos) {
            val ret = goNext
            assert(ret)
          }
          assert(at == pos)
          this.offset
        }
        def position(offset : Int) : Option[Position] = /*PositionBank.this.synchronized*/ {
          assert(offset >= 0)
          while (this.offset < offset && this.goNext) {}
          while (this.offset > offset && this.goPrev) {}
          assert(this.offset <= offset)
          if (this.offset == 0) { // patch up the zero case
            if (this.at == head && this.at.next != null && this.at.next.offset == 0)
              this.goNext
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
      trait PositionImpl extends HasNextPosition {
        def self : Position
        private[Positions] var offset : Int = _
        private[Positions] def cursorAfter : Boolean = /*PositionBank.this.synchronized*/ { if (offset < 0) true else if (offset > 0) false else {
          if (cursor.offset0 == 0) false else true
        }}
        override def toString = Integer.toString(hashCode, 10 + 'z' - 'a')        
        
        def file : File = FileImpl.this.self
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
    
    trait RangeTreeBank {
      sealed abstract class Range {
        def get : RangeTree = throw new NoSuchElementException
        def isEmpty : Boolean = true
        def from : Int = throw new NoSuchElementException
        def until : Int = throw new NoSuchElementException
        def enclosing : Range = NoRange
      }
      case class ActualRange(absolute : Int, tree0 : RangeTree) extends Range {
        override def get = (tree0)
        override def isEmpty = false
        override def from = absolute
        override def until = absolute + tree0.length
        override def enclosing = tree0.parent match {
        case `root` => super.enclosing
        case parent : RangeTreeImpl => ActualRange(absolute - tree0.offset0, parent.self)
        }
      }
      object NoRange extends Range 
      trait RangeTreeParent {
        def absolute : Int
        var child : RangeTree = NoRangeTree
        
        def wipe(absolute : Int, from : Int, until : Int) : Unit = {
          var child = this.child
          while (child != null && child.offset0 < from) {
            assert(child.extent0 <= from)
            child = child.next
          }
          while (child != null && child.offset0 < until) {
            assert(child.offset0 >= 0)
            assert(child.extent0 <= until)
            val next = child.next
            child.destroy(absolute + child.offset0)
            child = next
          }
        }
        
        def borderNext(absolute : Int, offset : Int) : Range = {
          var child = this.child
          while (true) {
            if (child == NoRangeTree || offset < child.offset0) return NoRange
            else if (offset == child.offset0 + child.length0) 
              return new ActualRange(absolute + child.offset0, child)
            else if (offset < child.offset0 + child.length0) 
              return child.borderNext(absolute + child.offset0, offset - child.offset0)
            else child = child.next
          }
          error("Unreached")
        }
        
        def borderPrev(absolute : Int, offset : Int) : Range = {
          var child = this.child
          while (true) {
            if (child == NoRangeTree || offset < child.offset0) return NoRange
            else if (offset == child.offset0) 
              return new ActualRange(absolute + child.offset0, child)
            else if (offset < child.offset0 + child.length0) 
              return child.borderPrev(absolute + child.offset0, offset - child.offset0)
            else child = child.next
          }
          error("Unreached")
        }
        
        def find(absolute : Int, offset : Int, adjacent : Boolean) : Range = {
          var child = this.child
          while (true) {
            if (child == NoRangeTree || offset < child.offset0) return NoRange
            else if (!adjacent && offset < child.extent0)
              return child.find(absolute + child.offset0, offset - child.offset0, adjacent)
              // else if
            else if (adjacent && (child.next == NoRangeTree || offset < child.next.offset0))
              return child.find(absolute + child.offset0, offset - child.offset0, adjacent)
            else child = child.next
          }
          error("Unreached")
        }
        
        def create(offset : Int) : RangeTree = { // must set length manually.
          //assert(isValid)
          //assert(offset != 0 || this == root)
          var child = this.child
          if (child == NoRangeTree || offset < child.offset0) {
            val ret = RangeTree
            ret.offset0 = offset
            ret.parent = this
            ret.next = child
            if (child != NoRangeTree) 
              child.prev = ret
            this.child = ret
            return ret
          }
          while (true) {
            if (offset == child.offset0) return child
            else if (offset > child.offset0 && offset < child.extent0) 
              return child.create(offset - child.offset0)
              else if (offset >= child.extent0 && (child.next == NoRangeTree || offset < child.next.offset0)) {
                val ret = RangeTree
                ret.prev = child
                ret.next = child.next
                ret.parent = this
                ret.offset0 = offset
                child.next = ret
                if (ret.next != NoRangeTree) 
                  ret.next.prev = ret
                return ret
              } else child = child.next
          }
          error("Unreached")
        }
        protected def repairInner(absolute : Int, offset : Int, added : Int, removed : Int) : Unit = {}
        def repair(absolute : Int, offset : Int, added : Int, removed : Int) : Unit = {
          var child = this.child
          if (child != null && !child.hasLength) {
            assert(child.next eq null)
            assert(this eq root)
            return
          }
          var addedTotal = 0
          var removedTotal = 0
          while (child != NoRangeTree && offset > child.extent0) 
            child = child.next // don't touch.
  
          if (child != NoRangeTree && offset == child.extent0 && removed > 0) {
            child = child.next // border case, don't touch      
          }
              
          // border cases, stuff is added on the boundary of the node.
          if (child != NoRangeTree && removed == 0 &&
              ((offset == child.offset0) || (offset == child.extent0))) {
            child.length = child.length + added
            // the offset0 doesn't change.
            child.repair(absolute + child.offset0, offset - child.offset0, added, removed)
            addedTotal = added
            child = child.next
          }
          
          // partially overlapping....
          if (child != NoRangeTree && offset > child.offset0 && offset < child.extent0) { // don't destroy
            val (removed0,added0) = if (offset + removed <= child.extent0) (removed,added)
                                    else (child.extent0 - offset,0)
            val oldLength = child.length
            child.repair(absolute + child.offset0, offset - child.offset0, added0, removed0)
            assert(child.length == oldLength)
            addedTotal += added0        
            removedTotal += removed0
            child.length = child.length - removed0 + added0
            assert(child.length > 0)
            child = child.next
          }
          while (child != NoRangeTree && offset + removed > child.offset0) {
            assert(offset <= child.offset0)
            if (offset <= child.offset0 && offset + removed >= child.extent0) {
              val next = child.next
              child.destroy(absolute + child.offset0)
              child = next
            } else {
              assert(offset <= child.offset0)
              assert(offset + removed < child.extent0)
              val removed0 = offset + removed - child.offset0
              assert(removed0 != 0)
              val added0 = added
              assert(removed0 <= removed)
              child.offset0 = child.offset0 - (removed - removed0)
              assert(child.offset0 >= 0)
              val oldLength = child.length
              child.repair(absolute + child.offset0, offset - child.offset0, added0, removed0)
              assert(oldLength == child.length)
              child.length = child.length - removed0 + added0
              assert(child.length > 0)
              addedTotal += added0
              removedTotal += removed0
              child = child.next
            }
          }
          while (child != NoRangeTree) { // the end.
            assert(offset + removed <= child.offset0)
            child.offset0 = child.offset0 - removed + added
            assert(child.offset0 >= 0)
            child = child.next
          }
          assert(addedTotal <= added)
          assert(removedTotal <= removed)
          if ((addedTotal < added) || (removedTotal < removed)) repairInner(absolute, offset, added, removed) // we've changed.
        }
        protected def destroyed(absolute : Int) : Unit = {
          var child = this.child
          // all children are dumped out..
          while (child != NoRangeTree) {
            val next = child.next
            child.parent = NoRangeTree
            child.next = NoRangeTree
            child.prev = NoRangeTree
            val offset = child.offset0 + absolute
            child.offset0 = -1
            child.destroyed(offset) // recursively destroy everything.
            child = next
          }
          this.child = NoRangeTree
        }
        def clear : Unit = {
          var child = this.child
          while (child != NoRangeTree) {
            val next = child.next
            child.clear
            child = next
          }
          this.child = NoRangeTree
        }
      }
      private object root extends RangeTreeParent {
        def absolute = 0
        def destroy = destroyed(0)
      }
      
      def borderNext(offset : Int) = root.borderNext(0, offset)
      def borderPrev(offset : Int) = root.borderPrev(0, offset)
      
      def find(offset : Int) = root.find(0, offset, false) // start-inc - end-exc
      def adjacent(offset : Int) = root.find(0, offset, true)
      def enclosing(offset : Int) : Range = find(offset) match { // start-exc - end-exc
      case NoRange => NoRange
      case ActualRange(`offset`, what) => what.parent match { // boundary case
        case `root` => NoRange
        case parent : RangeTreeImpl => ActualRange(offset - what.offset0, parent.self)
        }
      case ret => ret
      }
      def withBoundary(offset : Int) : Range = adjacent(offset) match {
      case range @ ActualRange(_,_) if offset >= range.from && offset <= range.until => range
      case _ => NoRange
      }
      
      
      def repair(offset : Int, added : Int, removed : Int) = {
        root.repair(0, offset, added, removed)
      }
      def create(offset : Int) = root.create(offset)
  
      def destroy = root.destroy
      def clear = root.clear
      def wipe(from : Int, until : Int) = root.wipe(0, from, until)
    
      def RangeTree : RangeTree
      val NoRangeTree = null.asInstanceOf[RangeTree]
      type RangeTree <: RangeTreeImpl
      trait RangeTreeImpl extends RangeTreeParent {
        def self : RangeTree
        var offset0 : Int = -1 // offset relative from parent
        var length0 : Int = 0 // how long
        var next : RangeTree = _
        var prev : RangeTree = _
        var parent : RangeTreeParent = _
        def extent0 = offset0 + length0 
        def parent0 = parent
        def isValid = parent != null
        def enclosing : Option[RangeTree] = parent match {
        case tree : RangeTreeImpl => Some(tree.self)
        case r if r == root => None
        }
        def absolute = {
          assert(isValid)
          parent.absolute + offset0
        }
        def last : Unit = {
          assert(isValid)
          var a = -1
          while (this.next != NoRangeTree) {
            if (a == -1) a = parent.absolute
            this.next.destroy(a + this.next.offset0)
          }
        }
        def clearChildren = {
          val a = absolute
          while (child != null) 
            child.destroy(a + child.offset0)
        }
        def intervals(absolute : Int) = if (!hasLength) Nil.elements else new Iterator[(Int,Int)] {
          var cursor = child
          var last = absolute
          def hasNext = cursor != self
          def next = cursor match {
          case null => 
            cursor = self
            (last, absolute + length)
          case node if node == self => throw new NoSuchElementException
          case node => 
            val ret = (last, absolute + node.offset0)
            last = absolute + node.extent0    
            cursor = cursor.next
            ret
          }
        }
        def prev0 = prev
        def prevOption = prev0 match {
        case null => None
        case prev => Some(prev)
        }
        def prev0_=(prev : RangeTree) : Unit = {
          assert(prev == null || prev.parent == parent)
          assert(prev != this)
          var a = -1
          while (this.prev != prev) {
            assert(this.prev != null)
            if (a == -1) a = parent.absolute
            this.prev.destroy(a + this.prev.offset0)
          }
          assert(this.prev == prev)
        }
        override def clear = {
          super.clear
          next = NoRangeTree
          prev = NoRangeTree
          parent = null
          offset0 = -1
        }
        final def destroy(absolute : Int) : Unit = {
          //val absolute = this.absolute
          assert(isValid)
          val oldParent = parent
          if (parent.child == this) {
            assert(prev == NoRangeTree)
            parent.child = next
          } else if (prev != NoRangeTree) {
            assert(parent.child != this)
            assert(prev.next == this)
            prev.next = next
          } else error("Unexpected failure") // would have to be first child
          
          if (next != NoRangeTree) {
            assert(next.prev == this)          
            next.prev = prev
          }
          this.parent = null
          this.next = NoRangeTree
          this.prev = NoRangeTree
          this.offset0 = -1
          destroyed(absolute)
        }
        // adjust length.
        def length = {
          assert(length0 >= 0)
          length0
        }
        def hasLength = length0 > 0
        // manual change, note that repair doesn't call this method.
        def length_=(length0 : Int) = this.length0 = {
          assert(length0 >= 0)
          length0
        }
        
  
        override def repair(absolute : Int, offset : Int, added : Int, removed : Int) : Unit = {
          super.repair(absolute, offset, added, removed)
        }
        def inflate(offset0 : Int) : Int = {
          var child = this.child
          var offset = offset0
          while (true) {
            if (child == null || offset < child.offset0) return offset
            assert(child.length0 >= 0)
            offset = offset + child.length0
            child = child.next
          }
          error("Unreached")
        }
        def deflate(offset : Int) : Int = {
          assert(offset >= 0)
          // tree relative to deflated
          var childLength = 0
          var child = this.child
          while (child != null && child.offset0 < offset) {
            assert(child.extent0 < offset)
            childLength = childLength + child.length
            child = child.next
          }
          offset - childLength
        }

        def relative = offset0
        
        def setRelative(offset : Int) = this.offset0 = offset
        
        override def find(absolute : Int, offset : Int, adjacent : Boolean) : Range = {
          if (hasLength && offset >= length) ActualRange(absolute, self)
          else super.find(absolute, offset, adjacent) match {
          case NoRange => ActualRange(absolute, self)
          case ret => ret
          }
        }
      }
    }
    trait CachedRangeTreeBank extends RangeTreeBank {
      override def clear = {
        super.clear
        busy = false
      }
      override def repair(offset : Int, added : Int, removed : Int) = {
        cache.clear
        assert(!busy)
        busy = true
        super.repair(offset, added, removed)
        assert(busy)
        busy = false
        assert(cache.isEmpty)
      }
      import scala.collection.jcl.LinkedHashMap
      private val cache = new LinkedHashMap[RangeTree,Int]
      private var busy = false
      type RangeTree <: RangeTreeImpl
      trait RangeTreeImpl extends super.RangeTreeImpl {
        def self : RangeTree
        override def setRelative(offset : Int) = {
          if (!busy) cache.removeKey(self)
          super.setRelative(offset)
        }
        override def absolute = cache.get(self) match {
          case Some(offset) => offset
          case None if (!busy) =>
            val ret = super.absolute
            cache(self) = ret
            ret
          case _ => super.absolute
        }
        def next0 = next
        def child0 = child
      }    
    }
  }
}
