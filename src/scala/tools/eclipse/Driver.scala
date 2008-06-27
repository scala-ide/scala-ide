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
  class Project(underlying0 : IProject) extends {
    override val underlying = underlying0 
  } with super[ScalaUIPlugin].ProjectImpl with super[ScalaMSILPlugin].ProjectImpl {
    def self = this
    def File(underlying : FileSpec) = new File(underlying)
    class File(underlying0 : FileSpec) extends {
      override val underlying = underlying0
    } with FileImpl {
      def self = this
    } 
  } 
}
