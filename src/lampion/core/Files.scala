/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.core

import scala.collection.{Set}
trait Files extends Nodes {
  def logError(t : Throwable) : Unit
  def logError(msg : String, t : Throwable) : Unit
  implicit def seqToString(seq : Seq[Char]) : String = seq match {
  case string : scala.runtime.RichString => string.self
  case seq => 
    val i = seq.elements
    val buf = new StringBuilder
    while (i.hasNext) buf.append(i.next)
    buf.toString
  }
  trait Repairable {
    def repair(offset : Int, added : Int, removed : Int) : Unit
  }
  protected def checkAccess : checkAccess0.type = checkAccess0
  protected object checkAccess0 {
    final def &&[T](s : T) = s
  }
  
  
  //type Path
  type File <: FileImpl
  trait FileImpl extends Repairable {
    def self : File
    def file = self
    def content : RandomAccessSeq[Char]
    def willBuild = {}
    override def repair(offset : Int, added : Int, removed : Int) : Unit = {}
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
    
    
    //def path : Path
    def isLoaded : Boolean
    def doLoad : Unit = {}
    def clear = {}
  }
}
