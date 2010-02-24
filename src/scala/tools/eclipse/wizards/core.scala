/*
 * Copyright 2010 LAMP/EPFL
 * 
 * @author Tim Clendenen
 * 
 */
package scala.tools.eclipse.wizards

class NewClassWizardPage extends {
	                     val declarationType = "Class" } 
                         with AbstractNewElementWizardPage 
                         with ClassOptions

class NewTraitWizardPage extends {
                         val declarationType = "Trait" } 
                         with AbstractNewElementWizardPage 
                         with TraitOptions

class NewObjectWizardPage extends {
                          val declarationType = "Object" } 
                          with AbstractNewElementWizardPage 
                          with ObjectOptions

class NewClassWizard 
  extends AbstractNewElementWizard(new NewClassWizardPage())

class NewTraitWizard
  extends AbstractNewElementWizard(new NewTraitWizardPage())

class NewObjectWizard 
  extends AbstractNewElementWizard(new NewObjectWizardPage())

