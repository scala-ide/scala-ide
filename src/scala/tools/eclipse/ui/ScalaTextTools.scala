package scala.tools.eclipse.ui

import org.eclipse.core.runtime.Preferences

import org.eclipse.jdt.ui.text.JavaTextTools

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.rules.IPartitionTokenScanner

import scala.tools.eclipse.ScalaPartitionScanner

class ScalaTextTools(store : IPreferenceStore, coreStore : Preferences, autoDisposeOnDisplayDispose : Boolean) 
  extends JavaTextTools(store, coreStore, autoDisposeOnDisplayDispose) {

  /**
   * Create a ScalaPartitionScanner instead of the Java one
   */
  override def getPartitionScanner : IPartitionTokenScanner = {
    return new ScalaPartitionScanner
  }
}
