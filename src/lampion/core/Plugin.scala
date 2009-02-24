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
  }
}
