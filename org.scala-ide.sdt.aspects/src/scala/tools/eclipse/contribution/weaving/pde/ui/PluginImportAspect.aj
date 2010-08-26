/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.pde.ui;

import java.util.List;

import org.eclipse.pde.internal.ui.wizards.imports.PluginImportHelper;
import org.eclipse.ui.wizards.datatransfer.IImportStructureProvider;

@SuppressWarnings("restriction")
public privileged aspect PluginImportAspect {
  pointcut folderContainsFileExtension(IImportStructureProvider provider, Object element, String fileExtension) :
    execution(private static boolean PluginImportHelper.folderContainsFileExtension(IImportStructureProvider, Object, String)) &&
    args(provider, element, fileExtension);
    
  boolean around(IImportStructureProvider provider, Object element, String fileExtension) :
    folderContainsFileExtension(provider, element, fileExtension) {
      return !".java".equals(fileExtension) ? proceed(provider, element, fileExtension) : folderContainsSourceExtension(provider, element);
  }

  public static boolean folderContainsSourceExtension(IImportStructureProvider provider, Object element) {
    List children = provider.getChildren(element);
    if (children != null && !children.isEmpty()) {
      for (int i = 0; i < children.size(); i++) {
        Object curr = children.get(i);
        if (provider.isFolder(curr)) {
          if (folderContainsSourceExtension(provider, curr)) {
            return true;
          }
        } else {
          String label = provider.getLabel(curr); 
          if (label.endsWith(".java") || label.endsWith(".scala") || label.endsWith(".aj")) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
