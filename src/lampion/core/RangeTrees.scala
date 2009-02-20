/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.core;
import scala.collection.mutable.ListBuffer
trait RangeTrees extends Positions {
  // trees are nested in each other
  type File <: FileImpl
  trait FileImpl extends super.FileImpl {
    def self : File
  trait RangeTreeBank extends Repairable {
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
      private[RangeTrees] var child : RangeTree = NoRangeTree
      
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
      
      def border(absolute : Int, offset : Int, dir : Dir) : Range = {
        var child = this.child
        while (true) {
          if (child == NoRangeTree || offset < child.offset0) return NoRange
          else if (offset == child.offset0 + (if (dir == PREV) 0 else child.length0)) 
            return new ActualRange(absolute + child.offset0, child)
          else if (offset < child.offset0 + child.length0) 
            return child.border(absolute + child.offset0, offset - child.offset0, dir)
          else child = child.next
        }
        abort
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
        abort
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
        abort
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
      private[RangeTrees] def destroy = destroyed(0)
    }
    def border(offset : Int, dir : Dir) = checkAccess && root.border(0, offset, dir)
    def find(offset : Int) = checkAccess && root.find(0, offset, false) // start-inc - end-exc
    def adjacent(offset : Int) = checkAccess && root.find(0, offset, true)
    def enclosing(offset : Int) : Range = checkAccess && find(offset) match { // start-exc - end-exc
    case NoRange => NoRange
    case ActualRange(`offset`, what) => what.parent match { // boundary case
      case `root` => NoRange
      case parent : RangeTreeImpl => ActualRange(offset - what.offset0, parent.self)
      }
    case ret => ret
    }
    def withBoundary(offset : Int) : Range = checkAccess && adjacent(offset) match {
    case range @ ActualRange(_,_) if offset >= range.from && offset <= range.until => range
    case _ => NoRange
    }
    
    
    def repair(offset : Int, added : Int, removed : Int) = {
      root.repair(0, offset, added, removed)
    }
    def create(offset : Int) = checkAccess && root.create(offset)

    def destroy = root.destroy
    def clear = root.clear
    def wipe(from : Int, until : Int) = checkAccess && root.wipe(0, from, until)
  
    def RangeTree : RangeTree
    private[RangeTrees] val NoRangeTree = null.asInstanceOf[RangeTree]
    type RangeTree <: RangeTreeImpl
    trait RangeTreeImpl extends RangeTreeParent {
      def self : RangeTree
      private[RangeTrees] var offset0 : Int = -1 // offset relative from parent
      private[RangeTrees] var length0 : Int = 0 // how long
      private[RangeTrees] var next : RangeTree = _
      private[RangeTrees] var prev : RangeTree = _
      private[RangeTrees] var parent : RangeTreeParent = _
      private[RangeTrees] def extent0 = offset0 + length0 
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
        } else abort // would have to be first child
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
        abort
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
      def relative_=(offset0 : Int) : Unit = {
        if (true) abort
        val diff = offset0 - this.offset0
        if (diff == 0) return
        
        assert(prev == null || prev.offset0 < offset0)
        assert(offset0 >= this.offset0 && offset0 < this.extent0)
        this.length0 = this.length0 - (offset0 - this.offset0)
        this.offset0 = this.offset0 + diff
        assert(this.offset0 == offset0)
        var child = this.child
        while (child != null) {
          child.offset0 = child.offset0 + diff
          assert(child.offset0 >= 0) // could go negative...
        }
      }
      
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
