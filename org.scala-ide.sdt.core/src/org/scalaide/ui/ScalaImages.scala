package org.scalaide.ui

import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.swt.graphics.Image
import org.scalaide.util.eclipse.OSGiUtils

object ScalaImages {
  val MISSING_ICON: ImageDescriptor = ImageDescriptor.getMissingImageDescriptor

  val SCALA_FILE: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/scu_obj.gif")
  val SCALA_CLASS_FILE: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/sclassf_obj.gif")
  val EXCLUDED_SCALA_FILE: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/scu_resource_obj.gif")

  val SCALA_CLASS: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/class_obj.gif")
  val SCALA_TRAIT: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/trait_obj.gif")
  val SCALA_OBJECT: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/object_obj.gif")
  val SCALA_PACKAGE_OBJECT: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/package_object_obj.png")

  val PUBLIC_DEF: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/defpub_obj.gif")
  val PRIVATE_DEF: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/defpri_obj.gif")
  val PROTECTED_DEF: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/defpro_obj.gif")

  val PUBLIC_VAL: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/valpub_obj.gif")
  val PROTECTED_VAL: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/valpro_obj.gif")
  val PRIVATE_VAL: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/valpri_obj.gif")

  val SCALA_TYPE: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/typevariable_obj.gif")

  val SCALA_PROJECT_WIZARD: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/wizban/newsprj_wiz.png")

  val REFRESH_REPL_TOOLBAR: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/etool16/refresh_interpreter.gif")

  val NEW_CLASS: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/etool16/newclass_wiz.gif")
  val CORRECTION_RENAME: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/correction_rename.gif")

  val DESC_SETTINGS_OBJ: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/settings.gif")

  val ADD_METHOD_PROPOSAL: Image = JavaPluginImages.DESC_MISC_PUBLIC.createImage()
  val PRIVATE_VAR: ImageDescriptor = JavaPluginImages.DESC_FIELD_PRIVATE
  val PUBLIC_VAR: ImageDescriptor = JavaPluginImages.DESC_FIELD_PUBLIC
  val PROTECTED_VAR: ImageDescriptor = JavaPluginImages.DESC_FIELD_PROTECTED
  val PACKAGE: ImageDescriptor = JavaPluginImages.DESC_OBJS_PACKAGE
  val DESC_OBJS_IMPCONT: ImageDescriptor = JavaPluginImages.DESC_OBJS_IMPCONT
  val DESC_OBJS_IMPDECL: ImageDescriptor = JavaPluginImages.DESC_OBJS_IMPDECL
  val DESC_INNER_METHOD: ImageDescriptor = OSGiUtils.getImageDescriptorFromCoreBundle("/icons/full/obj16/methloc_obj.png")
}
