/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.editor.decorators.implicits

import org.scalaide.ui.internal.preferences.ImplicitsPreferencePage
import org.scalaide.ui.internal.actions.AbstractToggleHandler

/**
 * Handler to toggle the Implicits Display (shortcut to avoid open Preferences,...)
 *
 * @see scala.tools.eclipse.ui.AbstractToggleHandler
 */
class ToggleImplicitsDisplayHandler extends AbstractToggleHandler("org.scala-ide.sdt.core.commands.ToggleImplicitsDisplay", ImplicitsPreferencePage.P_ACTIVE)
