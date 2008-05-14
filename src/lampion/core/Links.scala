/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.core;

trait Links extends Nodes with Files with Dirs {
  trait LinkContainer {
    def asNode : LinkedNode
    def isEnd(dir : Dir) : Boolean
    final def link(dir : Dir, node : LinkedNode) : Unit = {
      assert(node(NEXT) == unassignedLink)
      assert(node(PREV) == unassignedLink)
      link(dir, new LinkedList {
        init(node)
      });
    }
    def link(dir : Dir, list : LinkedList) : Unit = {
      if (list.isEmpty) return
      if (dir == PREV) return apply(PREV).link(NEXT, list)
      assert(dir == NEXT)
      val begin = list.begin(NEXT).asNode
      val   end = list.  end(PREV).asNode
      begin(PREV) = this
      end(NEXT) = apply(NEXT)
      apply(NEXT)(PREV) = end
      this(NEXT) = begin
      list.clear
      var node = begin
      while ({
        node addedTo this
        node != end
      }) node = node(NEXT).asNode
    }
    def apply(dir : Dir) : LinkContainer
    def update(dir : Dir, cnt : LinkContainer) : Unit
  }
  object unassignedLink extends LinkContainer {
    def isEnd(dir : Dir) : Boolean = throw new NoSuchElementException
    def asNode = throw new NoSuchElementException
    def update(dir : Dir, cnt : LinkContainer) =
      throw new UnsupportedOperationException
    def apply(dir : Dir) : LinkContainer = 
      throw new UnsupportedOperationException
  }
  trait LinkedList {
    protected def init(from : LinkedNode, until : LinkedNode) : Unit = {
      begin(NEXT) = from
      end(PREV) = until
      until(NEXT) = end
      from(PREV) = begin
    }
    protected def init(node : LinkedNode) : Unit = init(node, node)
    override def toString = {
      if (isEmpty) "<>"
      else if (begin(NEXT) == end(PREV)) begin(NEXT).toString
      else {
        begin(NEXT).toString + " to " + end(PREV).toString
      }
    }
    trait InitLinkContainer extends LinkContainer {
      def isEnd(dir : Dir) : Boolean = linkDir == dir
      def asNode = 
        throw new NoSuchElementException
      private var link : LinkContainer = unassignedLink;
      def linkDir : Dir;
      override def update(dir : Dir, cnt : LinkContainer) = {
        if (dir != linkDir.reverse) throw new UnsupportedOperationException
        link = cnt
      }
      override def apply(dir : Dir) = {
        if (dir != linkDir.reverse) throw new UnsupportedOperationException
        link
      }
      def container = LinkedList.this
    }
    protected def linkBegin(list : LinkedList) : Unit = begin.link0(list)
    
    def apply(dir : Dir) = dir match {
    case PREV => begin
    case NEXT =>   end
    }
    object begin extends InitLinkContainer {
      def linkDir = PREV;
      private[LinkedList] def link0(list : LinkedList) = super.link(NEXT, list)
      override def link(dir : Dir, list : LinkedList) : Unit = {
        if (dir == NEXT) linkBegin(list)
        else super.link(dir, list)
      }
    }
    object end extends InitLinkContainer {
      def linkDir = NEXT;
    }
    def clear = {
      begin(NEXT) = end
      end(PREV) = begin
    }
    clear
    def isEmpty = begin(NEXT) == end
    def foreach(f : LinkedNode => Unit) : Unit = {
      var node = begin(NEXT)
      while (node != end) {
        f(node.asNode); node = node(NEXT)
      }
    }
  }
  type LinkedNode <: Node with LinkedNodeImpl
  trait LinkedNodeImpl extends NodeImpl with LinkContainer {
    def self : LinkedNode
    def file : File
    
    private var next : LinkContainer = unassignedLink
    private var prev : LinkContainer = unassignedLink
    def isEnd(dir : Dir) : Boolean = false
    override def apply(dir : Dir) = if (dir == NEXT) next else prev
    
    def asNode(dir : Dir) = apply(dir) match {
    case node : LinkedNodeImpl => Some(node.self)
    case _ => None
    }
    
    def test(dir : Dir)(f : LinkedNode => Boolean) : Boolean = 
      if (apply(dir).isEnd(dir)) false else f(apply(dir).asNode)
    
    override def update(dir : Dir, cnt : LinkContainer) = dir match {
    case NEXT => next = cnt
    case PREV => prev = cnt
    }
    override def asNode = (self)
    
    
    def find(dir : Dir)(f : LinkedNode => Boolean) : Option[LinkedNode] = {
      var node = this(dir)
      while (node match {
      case node if node.isEnd(dir) => false
      case node : LinkedNodeImpl if f(node.self) => return Some(node.self)
      case _ => true
      }) node = node(dir)
      return None
    }
    
    def unlink(until : LinkedNode) : Unit = {
      var node = self
      while (node != until) node = node(NEXT).asNode
      val prev = self(PREV)
      val next = until(NEXT)
      node = self
      do {
        val node0 = node(NEXT)
        node(PREV) = unassignedLink
        node(NEXT) = unassignedLink
        if (node != until) node = node0.asNode
      } while (node != until)
      prev(NEXT) = next
      next(PREV) = prev
    }

    def removed : Boolean = {
      this(NEXT) == unassignedLink
    }
    def addedTo(container : LinkContainer) = {}
  }
  type File <: FileImpl;
  trait FileImpl extends super.FileImpl with LinkedList {
    def self : File;
    override def unloaded = {
      super.unloaded
      clear
    }
    override def clear = {
      super[FileImpl].clear
      super[LinkedList].clear
    }
    
    override def loaded : Unit = {
      super.loaded
    }
  }
  
  
  
}
