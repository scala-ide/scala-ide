/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.search.SearchDocument
import org.eclipse.jdt.internal.core.search.JavaSearchParticipant

class ScalaSearchParticipant extends JavaSearchParticipant {
	override def indexDocument(document : SearchDocument, indexPath : IPath) {
    //new ScalaSourceIndexer(document).indexDocument
  }
}
