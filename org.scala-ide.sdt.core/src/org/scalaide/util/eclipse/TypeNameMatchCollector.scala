package org.scalaide.util.eclipse

import java.util.Collection
import org.eclipse.core.runtime.Assert
import org.eclipse.jdt.core.search.TypeNameMatch
import org.eclipse.jdt.core.search.TypeNameMatchRequestor
import org.eclipse.jdt.internal.corext.util.TypeFilter

class TypeNameMatchCollector(val collection: Collection[TypeNameMatch]) extends TypeNameMatchRequestor {

  Assert.isNotNull(collection)

  override def acceptTypeNameMatch(m: TypeNameMatch): Unit =
    if (!TypeFilter.isFiltered(m)) {
      collection.add(m)
    }

}
