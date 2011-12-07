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
 * File: src/org.eclipse.contribution.weaving.jdt/src/org/eclipse/contribution/jdt/imagedescriptor/ImageDescriptorSelectorAspect.aj
 * 
 *******************************************************************************/


import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.internal.ui.text.java.LazyJavaCompletionProposal;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.ui.text.java.CompletionProposalLabelProvider;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

/**
 * Captures locations in the code where {@link ImageDescriptor}s are created in the
 * context of creating Java elements.  
 * Allows other providers to plug in their own ImageDescriptors instead for the
 * creation of Java-like elements. 
 * 
 * @author andrew
 * @created Dec 2, 2008
 *
 */
public privileged aspect ImageDescriptorSelectorAspect {
    
    /**
     * Getting the image for open type dialogs
     * 
     * Note that if multiple image descriptor registries can work on the same kind of element,
     * then there is potential for conflicts.
     * 
     * All we do here is return the first descriptor that we find.  Conflicts be damned!
     * If there ever is a conflict (unlikely), we will deal with the consequences as it comes.
     */
    ImageDescriptor around(boolean isInner, boolean isInInterfaceOrAnnotation, int flags, boolean useLightIcons, Object element) : 
            imageDescriptorCreation(isInner, isInInterfaceOrAnnotation, flags, useLightIcons) && 
            cflow(typeSelectionDialogGettingLabel(element)) {
        ImageDescriptor descriptor = getImageDescriptor(isInner, isInInterfaceOrAnnotation, flags, useLightIcons, element);
        return descriptor != null ? 
                descriptor : 
                proceed(isInner, isInInterfaceOrAnnotation, flags, useLightIcons, element);
    }
    
    pointcut imageDescriptorCreation(boolean isInner, boolean isInInterfaceOrAnnotation, int flags, boolean useLightIcons) :
        execution(public static ImageDescriptor JavaElementImageProvider.getTypeImageDescriptor(boolean, boolean, int, boolean)) &&
        args(isInner, isInInterfaceOrAnnotation, flags, useLightIcons);
    
    /**
     *  This pointcut captures the act of getting a label for elements in the type selection dialog
     */
    // XXX I don't want to use wild cards here, but for some reason the methods are not woven if no wild cards are used.
    // I should file a bug for this.
    pointcut typeSelectionDialogGettingLabel(Object element) : 
        (execution(public Image *..FilteredTypesSelectionDialog*.TypeItemLabelProvider.getImage(Object)) ||  // for open type dialog
         execution(public Image *..HierarchyLabelProvider.getImage(Object)) || // for type hierarchy view
         execution(public Image *..DebugTypeSelectionDialog*.DebugTypeLabelProvider.getImage(Object)))   // for choosing a main class
         && within(org.eclipse.jdt.internal..*)
         && args(element);

    

    /** 
     * Getting {@link ImageDescriptor} for other standard places where java elements go
     */
    pointcut computingDescriptor(Object element, int flags) : 
        execution(ImageDescriptor JavaElementImageProvider.computeDescriptor(Object, int)) &&
        within(JavaElementImageProvider) &&
        args(element, flags);
    
    ImageDescriptor around(Object element, int flags) : computingDescriptor(element, flags) {
        
        ImageDescriptor descriptor = getImageDescriptor(false, false, flags, false, element);
        return descriptor != null ? 
                descriptor : 
                proceed(element, flags);
    }
    
    
    
    private ImageDescriptor getImageDescriptor(boolean isInner, boolean isInInterfaceOrAnnotation, int flags, boolean useLightIcons, Object element) {
        try {
            for (IImageDescriptorSelector selector : ImageDescriptorSelectorRegistry.getInstance()) {
                ImageDescriptor descriptor = selector.getTypeImageDescriptor(isInner, isInInterfaceOrAnnotation, flags, useLightIcons, element);
                if (descriptor != null) {   
                    return descriptor;
                }
            }
        } catch (Throwable t) {
//            JDTWeavingPlugin.logException(t);
        }
        return null;
        
    }
    
    //////////////////////////////////////////////////////
    // This section of the aspect handles image descriptor creation for content assist
    
    // execution of CompletionProposalLabelProvider.createImageDescriptor && cflow(LazyJavaCompletionProposal.computeImage)
    pointcut javaCompletionProposalImageComputing(LazyJavaCompletionProposal proposal) :
        execution(protected Image LazyJavaCompletionProposal.computeImage()) && within(LazyJavaCompletionProposal)
        && this(proposal);
    
    pointcut creatingProposalImageDescriptor() : execution(public ImageDescriptor CompletionProposalLabelProvider.createImageDescriptor(CompletionProposal)) 
        && within(CompletionProposalLabelProvider);

    ImageDescriptor around(LazyJavaCompletionProposal proposal) : cflow(javaCompletionProposalImageComputing(proposal)) &&
            creatingProposalImageDescriptor() {
        ImageDescriptor desc = getAssistImageDescriptor(proposal);
        
        return desc != null ? desc : proceed(proposal);
    }
    
    
    private ImageDescriptor getAssistImageDescriptor(LazyJavaCompletionProposal proposal) {
        try {
            for (IImageDescriptorSelector selector : ImageDescriptorSelectorRegistry.getInstance()) {
                ImageDescriptor descriptor = selector.createCompletionProposalImageDescriptor(proposal);
                if (descriptor != null) {   
                    return descriptor;
                }
            }
        } catch (Throwable t) {
//            JDTWeavingPlugin.logException(t);
        }
        return null;
        
    }
    
}