package scala.tools.eclipse.scalatest

import java.net.MalformedURLException
import scala.tools.eclipse.ScalaImages
import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.core.runtime.Platform
import java.net.URL

object ScalaTestImages {

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
  val SCALATEST_STACK_FOLD_ENABLED = create("icons/full/elcl16/scalatest_stack_fold.gif")
  val SCALATEST_STACK_FOLD_DISABLED = create("icons/full/dlcl16/scalatest_stack_fold.gif")
  val SCALATEST_NEXT_FAILED_ENABLED = create("icons/full/elcl16/scalatest_next_failed.gif")
  val SCALATEST_NEXT_FAILED_DISABLED = create("icons/full/dlcl16/scalatest_next_failed.gif")
  val SCALATEST_PREV_FAILED_ENABLED = create("icons/full/elcl16/scalatest_prev_failed.gif")
  val SCALATEST_PREV_FAILED_DISABLED = create("icons/full/dlcl16/scalatest_prev_failed.gif")
  val SCALATEST_STOP_ENABLED = create("icons/full/elcl16/scalatest_stop.gif")
  val SCALATEST_STOP_DISABLED = create("icons/full/dlcl16/scalatest_stop.gif")
  val SCALATEST_SHOW_FAILED_TESTS_ONLY = create("icons/full/obj16/scalatest_show_failed.gif")
  
  private def create(localPath : String) = {
    try {
      val pluginInstallUrl = Platform.getBundle("org.scala-ide.sdt.scalatest").getEntry("/")
      val url = new URL(pluginInstallUrl, localPath)
      ImageDescriptor.createFromURL(url)
    } catch {
      case _ : MalformedURLException =>
        ScalaImages.MISSING_ICON
    }    
  }
  
}