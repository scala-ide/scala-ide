/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.formatter;

import scala.tools.eclipse.contribution.weaving.jdt.util.AbstractProviderRegistry;

public class FormatterCleanUpRegistry extends AbstractProviderRegistry<IFormatterCleanUpProvider> {
  
  public static String FORMATTER_CLEAN_UP_PROVIDERS_EXTENSION_POINT = "org.scala-ide.sdt.aspects.formatterCleanUp"; //$NON-NLS-1$

  private static final FormatterCleanUpRegistry INSTANCE = new FormatterCleanUpRegistry();

  public static FormatterCleanUpRegistry getInstance() {
    return INSTANCE;
  }

  @Override
  protected String getExtensionPointId() {
    return FORMATTER_CLEAN_UP_PROVIDERS_EXTENSION_POINT;
  }
}
