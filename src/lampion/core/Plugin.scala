/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.core;

trait Plugin {
  
  type Project <: ProjectImpl
  trait ProjectImpl extends Files {
    def self : Project

    type File <: FileImpl
    trait FileImpl extends super.FileImpl {
      def self : File
      def project = ProjectImpl.this.self
    }
    
    def isOpen = true 
    def destroy : Unit = {}
    final def logError(t : Throwable) : Unit = Plugin.this.logError(t)
    final def logError(msg : String, t : Throwable) : Unit = Plugin.this.logError(msg, t)
  }
  
  def assert(b : Boolean) : Unit = {
    if (!b)
       throw new Error();
  }
  def abort : Nothing = throw new Error;
  
  final def logError(t : Throwable) : Unit = logError("No message", t)
  def logError(msg : String, t : Throwable) : Unit = Console.println("ERROR: " + t + " with " + msg)
}
