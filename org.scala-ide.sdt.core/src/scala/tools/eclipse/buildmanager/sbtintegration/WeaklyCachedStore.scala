package scala.tools.eclipse.buildmanager.sbtintegration

import sbt.inc.AnalysisStore
import scala.ref.WeakReference
import sbt.CompileSetup
import sbt.inc.Analysis

/** A store using weak references to cache the backing Analysis store.
 * 
 *  The backing analysis store is used to retrieve the current value, and 
 *  cache it using a weak reference. Use it when the underlying `get` operation
 *  is expensive, but don't want to waste memory unnecessarily.
 */
class WeaklyCachedStore(backing: AnalysisStore) extends AnalysisStore {
  private var store: WeakReference[Option[(Analysis, CompileSetup)]] = new WeakReference(null)
  
  def get(): Option[(Analysis, CompileSetup)] = store.get match {
    case Some(cached) => cached
    case _ => 
      val data = backing.get()
      store = new WeakReference(data)
      data
  }
  
  def set(analysis: Analysis, setup: CompileSetup): Unit = {
    backing.set(analysis, setup)
    store = new WeakReference(Some(analysis, setup))
  }
}