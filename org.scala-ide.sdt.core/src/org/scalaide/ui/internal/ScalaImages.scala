package org.scalaide.ui.internal

import org.eclipse.core.runtime.FileLocator
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.Platform
import org.eclipse.jface.resource.ImageDescriptor
import org.osgi.framework.Bundle
import org.scalaide.core.ScalaPlugin

object ScalaImages {
  val MISSING_ICON = ImageDescriptor.getMissingImageDescriptor

  val SCALA_FILE = fromCoreBundle("/icons/full/obj16/scu_obj.gif")
  val SCALA_CLASS_FILE = fromCoreBundle("/icons/full/obj16/sclassf_obj.gif")
  val EXCLUDED_SCALA_FILE = fromCoreBundle("/icons/full/obj16/scu_resource_obj.gif")

  val SCALA_CLASS = fromCoreBundle("/icons/full/obj16/class_obj.gif")
  val SCALA_TRAIT = fromCoreBundle("/icons/full/obj16/trait_obj.gif")
  val SCALA_OBJECT = fromCoreBundle("/icons/full/obj16/object_obj.gif")
  val SCALA_PACKAGE_OBJECT = fromCoreBundle("/icons/full/obj16/package_object_obj.png")

  val PUBLIC_DEF = fromCoreBundle("/icons/full/obj16/defpub_obj.gif")
  val PRIVATE_DEF = fromCoreBundle("/icons/full/obj16/defpri_obj.gif")
  val PROTECTED_DEF = fromCoreBundle("/icons/full/obj16/defpro_obj.gif")

  val PUBLIC_VAL = fromCoreBundle("/icons/full/obj16/valpub_obj.gif")
  val PROTECTED_VAL = fromCoreBundle("/icons/full/obj16/valpro_obj.gif")
  val PRIVATE_VAL = fromCoreBundle("/icons/full/obj16/valpri_obj.gif")

  val SCALA_TYPE = fromCoreBundle("/icons/full/obj16/typevariable_obj.gif")

  val SCALA_PROJECT_WIZARD = fromCoreBundle("/icons/full/wizban/newsprj_wiz.png")

  val REFRESH_REPL_TOOLBAR = fromCoreBundle("/icons/full/etool16/refresh_interpreter.gif")

  val NEW_CLASS = fromCoreBundle("/icons/full/etool16/newclass_wiz.gif")
  val CORRECTION_RENAME = fromCoreBundle("/icons/full/obj16/correction_rename.gif")

  private[ui] def fromCoreBundle(path: String): ImageDescriptor =
    imageDescriptor(ScalaPlugin.plugin.pluginId, path) getOrElse MISSING_ICON

  private[ui] def fromBundle(bundleId: String, path: String): ImageDescriptor =
    imageDescriptor(bundleId, path) getOrElse MISSING_ICON

  /**
   * Creates an `Option` holding an `ImageDescriptor` of an image located in an
   * arbitrary bundle. The bundle has at least to be resolved and it may not be
   * stopped. If that is not the case or if the the path to the image is invalid
   * `None` is returned.
   */
  private def imageDescriptor(bundleId: String, path: String): Option[ImageDescriptor] =
    Option(Platform.getBundle(bundleId)) flatMap { bundle =>
      val state = bundle.getState()
      if (state != Bundle.ACTIVE && state != Bundle.STARTING && state != Bundle.RESOLVED)
        None
      else {
        val url = FileLocator.find(bundle, new Path(path), null)
        Option(url) map ImageDescriptor.createFromURL
      }
    }
}
