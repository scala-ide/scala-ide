package org.scalaide.extensions

/**
 * Base interface for save actions. A safe action is an IDE extension that is
 * executed whenever a document is saved.
 */
trait SaveAction extends ScalaIdeExtension
