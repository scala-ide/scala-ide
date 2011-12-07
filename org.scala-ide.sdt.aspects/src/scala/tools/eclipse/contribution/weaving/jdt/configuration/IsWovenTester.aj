package scala.tools.eclipse.contribution.weaving.jdt.configuration;

import org.eclipse.jdt.core.ToolFactory;

/*******************************************************************************
 * Added to the Scala plugin to fix interoperability issues between the 
 * Spring IDE and the Scala IDE. This plugin now implements the cuprovider and
 * imagedescriptorselector extension points, previously provided by the 
 * JDT weaving plugin.
 * 
 * Repo: git://git.eclipse.org/gitroot/ajdt/org.eclipse.ajdt.git
 * File: src/org.eclipse.contribution.weaving.jdt/src/org/eclipse/contribution/jdt/IsWovenTester.aj
 * 
 *******************************************************************************/


/**
 * This aspect tests to see if the weaving service is properly installed.
 * 
 * @author andrew
 * @created Dec 3, 2008
 *
 */
public aspect IsWovenTester {

    interface ScalaWeavingMarker { }
    
    /**
     * add a marker interface to an arbitrary class in JDT
     * later, we can see if the marker has been added.
     */
    declare parents : ToolFactory implements ScalaWeavingMarker;
    
    private static boolean weavingActive = new ToolFactory() instanceof ScalaWeavingMarker;
    
    public static boolean isWeavingActive() {
        return weavingActive;
    }
    
}
