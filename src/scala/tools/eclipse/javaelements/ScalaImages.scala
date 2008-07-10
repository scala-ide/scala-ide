/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.net.{ MalformedURLException, URL }

import org.eclipse.jface.resource.ImageDescriptor

import scala.tools.eclipse.ScalaUIPlugin

object ScalaImages  {
  val MISSING_ICON = new ScalaIcon(ImageDescriptor.getMissingImageDescriptor)

  val SCALA_FILE = ScalaIcon("icons/full/obj16/scu_obj.gif")
  val EXCLUDED_SCALA_FILE = ScalaIcon("icons/full/obj16/scu_obj.gif")

  val SCALA_CLASS = ScalaIcon("icons/full/obj16/class_obj.gif")
  val SCALA_TRAIT = ScalaIcon("icons/full/obj16/trait_obj.gif")
  val SCALA_OBJECT = ScalaIcon("icons/full/obj16/object_obj.gif")

  val PUBLIC_VAL = ScalaIcon("icons/full/obj16/valpub_obj.gif")
  val PROTECTED_VAL = ScalaIcon("icons/full/obj16/valpro_obj.gif")
  val PRIVATE_VAL = ScalaIcon("icons/full/obj16/valpri_obj.gif")

  val SCALA_TYPE = ScalaIcon("icons/full/obj16/typevariable_obj.gif")
}

class ScalaIcon(val descriptor : ImageDescriptor)

object ScalaIcon {
  def apply(localPath : String) = {
    try {
      val pluginInstallURL : URL = ScalaUIPlugin.plugin.getBundle.getEntry("/")
      val url = new URL(pluginInstallURL, localPath)
      new ScalaIcon(ImageDescriptor.createFromURL(url))
    } catch {
      case _ : MalformedURLException =>
        ScalaImages.MISSING_ICON
    }    
  }
  
  def apply(iconResourcePath : URL) =
    new ScalaIcon(ImageDescriptor.createFromURL(iconResourcePath))
}
