/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import java.net.{ MalformedURLException, URL }

import org.eclipse.jface.resource.ImageDescriptor

object ScalaImages  {
  val MISSING_ICON = ImageDescriptor.getMissingImageDescriptor

  val SCALA_FILE = create("icons/full/obj16/scu_obj.gif")
  val SCALA_CLASS_FILE = create("icons/full/obj16/sclassf_obj.gif")
  val EXCLUDED_SCALA_FILE = create("icons/full/obj16/scu_resource_obj.gif")

  val SCALA_CLASS = create("icons/full/obj16/class_obj.gif")
  val SCALA_TRAIT = create("icons/full/obj16/trait_obj.gif")
  val SCALA_OBJECT = create("icons/full/obj16/object_obj.gif")
  val SCALA_PACKAGE_OBJECT = create("icons/full/obj16/package_object_obj.png")

  val PUBLIC_DEF = create("icons/full/obj16/defpub_obj.gif")
  val PRIVATE_DEF = create("icons/full/obj16/defpri_obj.gif")
  val PROTECTED_DEF = create("icons/full/obj16/defpro_obj.gif")
  
  val PUBLIC_VAL = create("icons/full/obj16/valpub_obj.gif")
  val PROTECTED_VAL = create("icons/full/obj16/valpro_obj.gif")
  val PRIVATE_VAL = create("icons/full/obj16/valpri_obj.gif")

  val SCALA_TYPE = create("icons/full/obj16/typevariable_obj.gif")
  
  val SCALA_PROJECT_WIZARD = create("icons/full/wizban/newsprj_wiz.png")
  
  val REFRESH_REPL_TOOLBAR = create("icons/full/etool16/refresh_interpreter.gif")
  
  val SCALATEST_SUITE = create("icons/full/obj16/scalatest_suite.gif")
  val SCALATEST_SUITE_OK = create("icons/full/obj16/scalatest_suite_succeed.gif")
  val SCALATEST_SUITE_FAIL = create("icons/full/obj16/scalatest_suite_fail.gif")
  val SCALATEST_SUITE_ABORTED = create("icons/full/obj16/scalatest_suite_aborted.gif")
  val SCALATEST_SUITE_RUN = create("icons/full/obj16/scalatest_suite_run.gif")
  val SCALATEST_RUN = create("icons/full/obj16/scalatest_test_run.gif")
  val SCALATEST_SUCCEED = create("icons/full/obj16/scalatest_test_succeed.gif")
  val SCALATEST_FAILED = create("icons/full/obj16/scalatest_test_failed.gif")
  val SCALATEST_IGNORED = create("icons/full/obj16/scalatest_test_ignored.gif")
  val SCALATEST_SCOPE = create("icons/full/obj16/scalatest_test_scope.gif")
  val SCALATEST_INFO = create("icons/full/obj16/scalatest_info.gif")
  val SCALATEST_STACKTRACE = create("icons/full/obj16/scalatest_stacktrace.gif")
  val SCALATEST_RERUN_ALL_TESTS_ENABLED = create("icons/full/elcl16/scalatest_rerun_all.gif")
  val SCALATEST_RERUN_FAILED_TESTS_ENABLED = create("icons/full/elcl16/scalatest_rerun_failed.gif")
  val SCALATEST_RERUN_ALL_TESTS_DISABLED = create("icons/full/dlcl16/scalatest_rerun_all.gif")
  val SCALATEST_RERUN_FAILED_TESTS_DISABLED = create("icons/full/dlcl16/scalatest_rerun_failed.gif")
  
  private def create(localPath : String) = {
    try {
      val pluginInstallURL : URL = ScalaPlugin.plugin.getBundle.getEntry("/")
      val url = new URL(pluginInstallURL, localPath)
      ImageDescriptor.createFromURL(url)
    } catch {
      case _ : MalformedURLException =>
        ScalaImages.MISSING_ICON
    }    
  }
}
