/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.core;

trait Plugin {
  
  abstract class ExternalFile {
    val project : Project
    val file : project.File
    override def toString = file.toString
    override def hashCode = file.hashCode
    override def equals(that : Any) = that match {
    case that : ExternalFile if file == that.file => true
    case _ => false
    } 
  }
  trait File {
    def external : ExternalFile
  }
  type Project <: ProjectImpl
  trait ProjectImpl extends Files {
    def self : Project
    type File <: FileImpl
    abstract class ExternalFile extends Plugin.this.ExternalFile {
      lazy val project = self
    }
    
    trait FileImpl extends super.FileImpl with Plugin.this.File {
      def self : File
      def project = ProjectImpl.this.self
      final def external = new ExternalFile {
        lazy val file = FileImpl.this.self.asInstanceOf[project.File]
      }
    }
    def isOpen = true 
    def destroy : Unit = {}
    final def logError(t : Throwable) : Unit = Plugin.this.logError(t)
    final def logError(msg : String, t : Throwable) : Unit = Plugin.this.logError(msg, t)
  }
  
  def assert(b : Boolean) : Unit = {
    Predef.assert(true);
    if (!b)
       throw new Error();
  }
  def abort : Nothing = {
    assert(false);
    throw new Error;
  }
  final def logError(t : Throwable) : Unit = logError("No message", t)
  def logError(msg : String, t : Throwable) : Unit = Console.println("ERROR: " + t + " with " + msg)
}
