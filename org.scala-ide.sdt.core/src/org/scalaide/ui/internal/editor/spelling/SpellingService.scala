package org.scalaide.ui.internal.editor.spelling

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.ui.texteditor.spelling.ISpellingEngine
import org.eclipse.ui.texteditor.spelling.ISpellingProblemCollector
import org.eclipse.ui.texteditor.spelling.SpellingContext
import org.eclipse.ui.texteditor.spelling.{SpellingService => ESpellingService}
import org.scalaide.util.eclipse.EclipseUtils

/**
 * The purpose of the super class is to find a spelling engine in a given
 * preference store and use it to check the spelling of the content of an editor.
 *
 * But this is not as straightforward as it may look like:
 * - A spelling engine is meant to be added by an extension point.
 * - However, this extension point does not allow to to specify a spelling
 *   engine for a file extension.
 * - Furthermore, the platform picks only one of multiple available spelling
 *   engines.
 * - JDT already defines a spelling engine, which is used instead of our own.
 * - We only want to have a spelling engine for Scala editors, not for all
 *   editors of the platform.
 *
 * To overcome these points, [[org.eclipse.ui.texteditor.spelling.SpellingService]]
 * is subclassed and implemented in a way that fulfills our needs.
 *
 * @param store
 *        The store where the property is saved whether spell checking is
 *        enabled or not
 * @param engine
 *        The spelling engine that is used by this class
 */
final class SpellingService(store: IPreferenceStore, engine: ISpellingEngine) extends ESpellingService(store) {

  override def check(document: IDocument, regions: Array[IRegion], context: SpellingContext, collector: ISpellingProblemCollector, monitor: IProgressMonitor): Unit = {
    try {
      collector.beginCollecting()

      if (store.getBoolean(ESpellingService.PREFERENCE_SPELLING_ENABLED)) {
        EclipseUtils.withSafeRunner {
          engine.check(document, regions, context, collector, monitor)
        }
      }

    } finally {
      collector.endCollecting()
    }
  }
}
