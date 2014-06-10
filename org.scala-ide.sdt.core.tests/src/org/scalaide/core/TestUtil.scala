package org.scalaide.core

import org.osgi.framework.Version
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.BundledScalaInstallation
import org.scalaide.core.internal.project.MultiBundleScalaInstallation

object TestUtil {

  def installedScalaVersionGreaterOrEqualsTo(version: Version): Boolean = PartialFunction.cond(ScalaInstallation.platformInstallation){
      case bSI : BundledScalaInstallation => bSI.osgiVersion.compareTo(version) >= 0
      case mSI : MultiBundleScalaInstallation => mSI.osgiVersion.compareTo(version) >= 0
     }

}