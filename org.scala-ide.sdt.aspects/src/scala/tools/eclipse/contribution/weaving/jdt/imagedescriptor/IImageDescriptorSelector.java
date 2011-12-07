/*******************************************************************************
 * Copyright (c) 2008 SpringSource and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *      SpringSource
 *      Andrew Eisenberg (initial implementation)
 *******************************************************************************/
package scala.tools.eclipse.contribution.weaving.jdt.imagedescriptor;

/*******************************************************************************
 * Added to the Scala plugin to fix interoperability issues between the 
 * Spring IDE and the Scala IDE. This plugin now implements the cuprovider and
 * imagedescriptorselector extension points, previously provided by the 
 * JDT weaving plugin.
 * 
 * Repo: git://git.eclipse.org/gitroot/ajdt/org.eclipse.ajdt.git
 * File: src/org.eclipse.contribution.weaving.jdt/src/org/eclipse/contribution/jdt/imagedescriptor/IImageDescriptorSelector.java
 * 
 *******************************************************************************/


import org.eclipse.jdt.internal.ui.text.java.LazyJavaCompletionProposal;
import org.eclipse.jface.resource.ImageDescriptor;

public interface IImageDescriptorSelector {
    /**
     * Creates the image descriptor for Java-like elements appearing in open type dialogs and the search view
     * Arguments are passed in from the Aspect
     */
    public ImageDescriptor getTypeImageDescriptor(boolean isInner, boolean isInInterfaceOrAnnotation, int flags, boolean useLightIcons, Object element);
    
    /**
     * Creates the image descriptor for Java-like elements appearing in content assist
     */
    public ImageDescriptor createCompletionProposalImageDescriptor(LazyJavaCompletionProposal proposal);
}
