package scala.tools.eclipse.launching;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.junit.util.CoreTestSearchEngine;

/**
 * This is a direct copy of the java code from the junit plugin, until we decide whether or not to fork the code for scalatest stuff
 */

/**
 * JUnitPropertyTester provides propertyTester(s) for IResource types
 * for use in XML Expression Language syntax.
 */
public class JUnitPropertyTester extends PropertyTester {

	private static final String PROPERTY_IS_TEST= "isTest";	 //$NON-NLS-1$

	private static final String PROPERTY_CAN_LAUNCH_AS_JUNIT_TEST= "canLaunchAsJUnit"; //$NON-NLS-1$

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.participants.properties.IPropertyEvaluator#test(java.lang.Object, java.lang.String, java.lang.String)
	 */
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if (!(receiver instanceof IAdaptable)) {
			throw new IllegalArgumentException("Element must be of type 'IAdaptable', is " + receiver == null ? "null" : receiver.getClass().getName()); //$NON-NLS-1$ //$NON-NLS-2$
		}

		IJavaElement element;
		if (receiver instanceof IJavaElement) {
			element= (IJavaElement) receiver;
		} else if (receiver instanceof IResource) {
			element = JavaCore.create((IResource) receiver);
			if (element == null) {
				return false;
			}
		} else { // is IAdaptable
			element= (IJavaElement) ((IAdaptable) receiver).getAdapter(IJavaElement.class);
			if (element == null) {
				IResource resource= (IResource) ((IAdaptable) receiver).getAdapter(IResource.class);
				element = JavaCore.create(resource);
				if (element == null) {
					return false;
				}
			}
		}
		if (PROPERTY_IS_TEST.equals(property)) {
			return isJUnitTest(element);
		} else if (PROPERTY_CAN_LAUNCH_AS_JUNIT_TEST.equals(property)) {
			return canLaunchAsJUnitTest(element);
		}
		throw new IllegalArgumentException("Unknown test property '" + property + "'"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private boolean canLaunchAsJUnitTest(IJavaElement element) {
		try {
			switch (element.getElementType()) {
				case IJavaElement.JAVA_PROJECT:
				case IJavaElement.PACKAGE_FRAGMENT_ROOT:
					return true; // can run, let test runner detect if there are tests
				case IJavaElement.PACKAGE_FRAGMENT:
					return ((IPackageFragment) element).hasChildren();
				case IJavaElement.COMPILATION_UNIT:
				case IJavaElement.CLASS_FILE:
				case IJavaElement.TYPE:
				case IJavaElement.METHOD:
					return isJUnitTest(element);
				default:
					return false;
			}
		} catch (JavaModelException e) {
			return false;
		}
	}

	/*
	 * Return whether the target resource is a JUnit test.
	 */
	@SuppressWarnings("restriction")
	private boolean isJUnitTest(IJavaElement element) {
		try {
			IType testType= null;
			if (element instanceof ICompilationUnit) {
				testType= (((ICompilationUnit) element)).findPrimaryType();
			} else if (element instanceof IClassFile) {
				testType= (((IClassFile) element)).getType();
			} else if (element instanceof IType) {
				testType= (IType) element;
			} else if (element instanceof IMember) {
				testType= ((IMember) element).getDeclaringType();
			}
			if (testType != null && testType.exists()) {
				return CoreTestSearchEngine.isTestOrTestSuite(testType);
			}
		} catch (CoreException e) {
			// ignore, return false
		}
		return false;
	}
}
