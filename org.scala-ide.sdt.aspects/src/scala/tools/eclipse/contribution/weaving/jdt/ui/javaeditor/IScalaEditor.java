/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.jface.text.IDocumentPartitioner;

public interface IScalaEditor {

    public IDocumentPartitioner createDocumentPartitioner();

}
