/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse 
import org.eclipse.core.resources._ 
 
object Driver { 
  private[eclipse] var driver : Driver = _
}   
class Driver extends ScalaUIPlugin with net.ScalaMSILPlugin { 
  Driver.driver = this 
  def Project(underlying : IProject) = new Project(underlying)
  class Project(override val underlying : IProject) extends super[ScalaUIPlugin].ProjectImpl with super[ScalaMSILPlugin].ProjectImpl {
    def self = this
    def File(underlying : FileSpec) = new File(underlying)
    class File(override val underlying : FileSpec) extends FileImpl {
      def self = this
    } 
  } 
}
 