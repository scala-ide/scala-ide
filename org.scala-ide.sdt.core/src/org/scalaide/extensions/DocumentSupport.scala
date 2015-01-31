package org.scalaide.extensions

import org.scalaide.core.text.Document

/**
 * Can be mixed into a [[ScalaIdeExtension]] that operates on a [[Document]].
 */
trait DocumentSupport extends ScalaIdeExtension {

  /**
   * The document this IDE extension operates on. The document provides only an
   * immutable view on the real document of the underlying file system. All
   * changes that are made needs to be returned by [[perform()]].
   *
   * '''ATTENTION''':
   * Do not implement this value by any means! It will be automatically
   * implemented by the IDE.
   */
  val document: Document
}
