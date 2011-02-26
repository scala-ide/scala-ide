/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.formatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring.CleanUpChange;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CodeFormatFix;
import org.eclipse.jdt.internal.ui.fix.CodeFormatCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jface.text.IRegion;

@SuppressWarnings("restriction")
public privileged aspect FormatterCleanUpRegistryAspect {

	pointcut calculateChange(CleanUpContext cleanUpContext, ICleanUp[] cleanUps, List undoneCleanUps, HashSet slowCleanUps) :
		args(cleanUpContext, cleanUps, undoneCleanUps, slowCleanUps) && 
	   execution(CleanUpChange CleanUpRefactoring.calculateChange(CleanUpContext, ICleanUp[] , List , HashSet ));
		
	
	pointcut createCleanUp(ICompilationUnit cu, IRegion[] regions,
			boolean format, boolean removeTrailingWhitespacesAll,
			boolean removeTrailingWhitespacesIgnorEmpty,
			boolean correctIndentation) :
	 args(cu, regions, format, removeTrailingWhitespacesAll, removeTrailingWhitespacesIgnorEmpty, correctIndentation) &&
	 execution(ICleanUpFix CodeFormatFix.createCleanUp(ICompilationUnit , IRegion[] , boolean , boolean , boolean , boolean ));
	
	ICleanUpFix around(ICompilationUnit cu, IRegion[] regions, boolean format,
			boolean removeTrailingWhitespacesAll,
			boolean removeTrailingWhitespacesIgnorEmpty,
			boolean correctIndentation) :
				createCleanUp(cu, regions, format, removeTrailingWhitespacesAll, removeTrailingWhitespacesIgnorEmpty, correctIndentation) {
		if ("scala".equals(cu.getResource().getFileExtension()))
			if (format)
				for (IFormatterCleanUpProvider provider : FormatterCleanUpRegistry
					.getInstance().getProviders())
				return provider.createCleanUp(cu);
		return proceed(cu, regions, format, removeTrailingWhitespacesAll, removeTrailingWhitespacesIgnorEmpty, correctIndentation);
	}
 
	// Filter out CleanUps that aren't yet Scala compatible:
	CleanUpChange around(CleanUpContext cleanUpContext, ICleanUp[] cleanUps, List undoneCleanUps, HashSet slowCleanUps):
		calculateChange(cleanUpContext, cleanUps, undoneCleanUps, slowCleanUps) {
		ICleanUp[] newCleanUps;
		if ("scala".equals(cleanUpContext.getCompilationUnit().getResource().getFileExtension())) {
    		List<ICleanUp> newCleanUpList = new ArrayList<ICleanUp>();
	    	for (ICleanUp cleanUp : cleanUps) 
		    	if (cleanUp instanceof CodeFormatCleanUp)
			    	newCleanUpList.add(cleanUp);
		    newCleanUps = (ICleanUp[]) newCleanUpList.toArray(new ICleanUp[newCleanUpList.size()]);
		} else
			newCleanUps = cleanUps;
		return proceed(cleanUpContext, newCleanUps, undoneCleanUps, slowCleanUps);
	}

	
}
