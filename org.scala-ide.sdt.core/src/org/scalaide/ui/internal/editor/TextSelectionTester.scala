package org.scalaide.ui.internal.editor

import org.eclipse.core.expressions.PropertyTester
import org.eclipse.jface.text.ITextSelection

/**
 * Provides tests that can be called as test properties in the plugin.xml file.
 */
class TextSelectionTester extends PropertyTester {

  /** This refers to the method whose name is equal to this variables value */
  private final val NonEmptyProperty = "nonEmpty"

  def nonEmpty(selection: ITextSelection): Boolean =
    selection.getLength() != 0

  def test(receiver: Object, property: String, args: Array[Object], expectedValue: Object): Boolean =
    receiver match {
      case selection: ITextSelection if property == NonEmptyProperty =>
        nonEmpty(selection)
      case _ =>
        false
    }
}