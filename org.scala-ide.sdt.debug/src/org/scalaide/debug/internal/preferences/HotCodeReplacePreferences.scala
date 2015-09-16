/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.preferences

import org.scalaide.debug.internal.ScalaDebugPlugin

/**
 * Provides convenient way to access preference values related to Hot Code Replace feature.
 */
object HotCodeReplacePreferences {
  import DebuggerPreferencePage._

  private lazy val preferenceStore = ScalaDebugPlugin.plugin.getPreferenceStore()

  def hcrEnabled: Boolean =
    preferenceStore.getBoolean(HotCodeReplaceEnabled)

  private[internal] def hcrEnabled_=(enabled: Boolean): Unit =
    preferenceStore.setValue(HotCodeReplaceEnabled, enabled)

  def notifyAboutFailedHcr: Boolean =
    preferenceStore.getBoolean(NotifyAboutFailedHcr)

  def notifyAboutUnsupportedHcr: Boolean =
    preferenceStore.getBoolean(NotifyAboutUnsupportedHcr)

  def performHcrForFilesContainingErrors: Boolean =
    preferenceStore.getBoolean(PerformHcrForFilesContainingErrors)

  private[internal] def performHcrForFilesContainingErrors_=(enabled: Boolean): Unit =
    preferenceStore.setValue(PerformHcrForFilesContainingErrors, enabled)

  def dropObsoleteFramesAutomatically: Boolean =
    preferenceStore.getBoolean(DropObsoleteFramesAutomatically)

  private[internal] def dropObsoleteFramesAutomatically_=(enabled: Boolean): Unit =
    preferenceStore.setValue(DropObsoleteFramesAutomatically, enabled)

  def allowToDropObsoleteFramesManually: Boolean =
    preferenceStore.getBoolean(AllowToDropObsoleteFramesManually)

  private[internal] def allowToDropObsoleteFramesManually_=(enabled: Boolean): Unit =
    preferenceStore.setValue(AllowToDropObsoleteFramesManually, enabled)
}
