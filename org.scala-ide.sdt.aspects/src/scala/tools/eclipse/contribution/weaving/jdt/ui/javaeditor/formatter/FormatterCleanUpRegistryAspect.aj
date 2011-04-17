/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.formatter;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CodeFormatFix;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jface.text.IRegion;


@SuppressWarnings("restriction")
public privileged aspect FormatterCleanUpRegistryAspect {

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
				else 
					return null;
		return proceed(cu, regions, format, removeTrailingWhitespacesAll,
				removeTrailingWhitespacesIgnorEmpty, correctIndentation);
	}

}
