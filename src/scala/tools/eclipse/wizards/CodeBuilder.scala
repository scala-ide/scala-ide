/*
 * Copyright 2010 LAMP/EPFL
 * 
 * @author Tim Clendenen
 * 
 */
package scala.tools.eclipse.wizards

import scala.collection.mutable.HashMap

object CodeBuilder {
	
  import templates._
  
  val lhm: HashMap[String, BufferSupport] = HashMap.empty
  
  class NameBuffer(val name: String, val cons: String) extends BufferSupport {
	  
	def this(name: String) {
	  this(name, "")
	}
	  
	def this(buffer: NameBuffer, cons: String) {
	  this(buffer.name, cons)
	  offset = buffer.offset
	  length = buffer.length
	}
	
	protected def contents(implicit ld: String) = name + cons
  }
  
  class ExtendsBuffer(val superTypes: List[String], val cons: String) 
    extends BufferSupport {
	  
	def this(superTypes: List[String]) {
	  this(superTypes, "")
	}
	  
	def this(buffer: ExtendsBuffer, cons: String) {
	  this(buffer.superTypes, cons)
	  offset = buffer.offset
	  length = buffer.length
	}
	
	protected def contents(implicit ld: String) = {
	  val t = typeTemplate(superTypes)
	  val a = t.split(" ")
	  if(a.length > 1)
	    a(2) = a(2) + cons
	  a.mkString(" ")
	}
  }
}
