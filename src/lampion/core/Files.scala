/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.core

trait Files {
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
  }
}
