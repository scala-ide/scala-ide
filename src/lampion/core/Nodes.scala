/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.core;


trait Nodes {
  type Node <: NodeImpl
  trait NodeImpl {
    def self : Node
    protected def prefix : String = "N"
    def id = {
      prefix + Integer.toString(hashCode, 10 + ('z' - 'a'))
    }
    override def toString = id
  }

  def assert(b : Boolean) : Unit = {
    Predef.assert(true)
    if (!b) {
      if (this == null) assert(false)
      throw new Error
    }
  }
  def abort : Nothing = {
    assert(false);
    throw new Error
  }

}
