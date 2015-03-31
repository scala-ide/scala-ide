/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.hcr
package ui

import scala.collection.mutable.Publisher
import scala.collection.mutable.Subscriber

import org.eclipse.jface.dialogs.MessageDialog
import org.scalaide.debug.internal.preferences.HotCodeReplacePreferences
import org.scalaide.util.eclipse.SWTUtils
import org.scalaide.util.ui.DisplayThread

import ScalaHotCodeReplaceManager.HCRFailed
import ScalaHotCodeReplaceManager.HCRNotSupported
import ScalaHotCodeReplaceManager.HCRResult

/**
 * Informs user when there's something wrong related to HCR.
 */
private[internal] object HotCodeReplaceListener extends Subscriber[HCRResult, Publisher[HCRResult]] {

  override def notify(publisher: Publisher[HCRResult], event: HCRResult): Unit = event match {
    case HCRNotSupported(launchName) =>
      if (HotCodeReplacePreferences.notifyAboutUnsupportedHcr)
        notifyAboutUnsupportedHCR(launchName)
    case HCRFailed(launchName) =>
      if (HotCodeReplacePreferences.notifyAboutFailedHcr)
        notifyAboutFailedHCR(launchName)
  }

  private def notifyAboutUnsupportedHCR(launchName: String): Unit = DisplayThread.asyncExec {
    val title = "Hot Code Replace not supported"
    val message = s"""Hot Code Replace can't be performed for debug configuration '$launchName'.
                     |Your VM doesn't support redefining classes.""".stripMargin
    MessageDialog.openWarning(SWTUtils.getShell, title, message)
  }

  private def notifyAboutFailedHCR(launchName: String): Unit = DisplayThread.asyncExec {
    val title = "Hot Code Replace failed"
    val message = s"""Performing Hot Code Replace failed for debug configuration '$launchName'.
                     |Your debug session can be in bizarre state. Consider restarting it.""".stripMargin
    MessageDialog.openError(SWTUtils.getShell, title, message)
  }
}
