package scala.tools.eclipse.diagnostic

import org.eclipse.swt.events.SelectionListener
import org.eclipse.swt.events.SelectionEvent
import scala.tools.eclipse.logging.{Level, LogManager, HasLogger}
import org.eclipse.swt.widgets.Combo

class LogLevelListener(combo: Combo) extends SelectionListener with HasLogger {
  def widgetSelected(e: SelectionEvent) {
    val level = Level.withName(combo.getText)
    LogManager.setLogLevel(level)
  }
  
  def widgetDefaultSelected(e: SelectionEvent) { /* do nothing */ }
}