/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

/*******************************************************************************
 * Added to the Scala plugin to fix interoperability issues between the 
 * Spring IDE and the Scala IDE. This plugin now implements the cuprovider and
 * imagedescriptorselector extension points, previously provided by the 
 * JDT weaving plugin.
 * 
 * Repo: git://git.eclipse.org/gitroot/ajdt/org.eclipse.ajdt.git
 * File: src/org.eclipse.contribution.weaving.jdt/src/org/eclipse/contribution/jdt/preferences/WeavingStateConfigurer.java
 * 
 *******************************************************************************/

package scala.tools.eclipse.contribution.weaving.jdt.configuration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URL;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.frameworkadmin.BundleInfo;
import org.eclipse.equinox.internal.simpleconfigurator.manipulator.SimpleConfiguratorManipulatorImpl;
import org.eclipse.equinox.simpleconfigurator.manipulator.SimpleConfiguratorManipulator;
import org.eclipse.osgi.framework.internal.core.FrameworkProperties;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.DisabledInfo;
import org.eclipse.osgi.service.resolver.State;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;

import scala.tools.eclipse.contribution.weaving.jdt.ScalaJDTWeavingPlugin;
import scala.tools.eclipse.contribution.weaving.jdt.configuration.IsWovenTester;

/**
 * @author Andrew Eisenberg
 * @created Jan 19, 2009
 *
 * Object that controls the state of the Equinox aspects weaver
 */
public class WeavingStateConfigurer {
    
    private final static Version MIN_WEAVER_VERSION = new Version(1, 6, 1);

    private final static boolean IS_WEAVING = IsWovenTester.isWeavingActive();

    
    public String getWeaverVersionInfo() {
        BundleDescription weaver = 
            Platform.getPlatformAdmin().getState(false).
            getBundle("org.aspectj.weaver", null);
        
        if (weaver != null) {
            if (MIN_WEAVER_VERSION.compareTo(weaver.getVersion()) <= 0) {
                return "";
//                return "AspectJ weaver version " + weaver.getVersion().toString() + " OK!";
            } else {
                return "No compatible version of org.aspectj.weaver found.  " +
                "JDT Weaving requires 1.6.1 or higher.  Found version " +
                weaver.getVersion();
            }
        } else {
            return "org.aspectj.weaver not installed.  JDT Weaving requires 1.6.3 or higher.";
        }
    }
    
    public IStatus changeWeavingState(boolean becomeEnabled) {
        // now, weaving service is controlled by 
        // starting and stopping weaving.aspectj bundle
        // this method is used only to ensure the hook exists
        IStatus success;
        boolean isCurrentlyWeaving = false;
        try {
            isCurrentlyWeaving = currentConfigStateIsWeaving();
        } catch (Exception e) {
            ScalaJDTWeavingPlugin.logException(e);
        }
        if (becomeEnabled && !isCurrentlyWeaving) {
            success = changeConfigDotIni(becomeEnabled);
        } else {
            // do nothing
            success = Status.OK_STATUS;
        }
        
        
        IStatus success2 = changeAutoStartupAspectsBundle(becomeEnabled);
        
        IStatus success3 = changeSimpleConfiguratorManipulator(becomeEnabled);
        if (success.getSeverity() >= IStatus.ERROR || success2.getSeverity() >= IStatus.ERROR || success3.getSeverity() >= IStatus.ERROR) {
            return new MultiStatus(ScalaJDTWeavingPlugin.ID, IStatus.ERROR, 
                    new IStatus[] { success, success2, success3, getInstalledBundleInformation() }, "Could not "
                    + (becomeEnabled ? "ENABLED" : "DISABLED") + " weaving service",
                    null);
        } else if (success.getSeverity() >= IStatus.WARNING || success2.getSeverity() >= IStatus.WARNING || success3.getSeverity() >= IStatus.WARNING) {
            return new MultiStatus(ScalaJDTWeavingPlugin.ID, IStatus.WARNING, 
                    new IStatus[] { success, success2, success3, getInstalledBundleInformation() }, "Weaving service "
                    + (becomeEnabled ? "ENABLED" : "DISABLED") + " with warnings",
                    null);
        } else {
            // if successful, also schedule to ask for reindexing on startup
            if (becomeEnabled) {
              // We don't ask to reindex (anyway, the Scala nature was not part of the 'weavable natures' list) 
              // while using the JDT weaving plugin
//                JDTWeavingPreferences.setAskToReindex(true);
            }
            return new MultiStatus(ScalaJDTWeavingPlugin.ID, IStatus.OK, 
                    new IStatus[] { getInstalledBundleInformation() },
                    "Weaving service successfully "
                    + (becomeEnabled ? "ENABLED" : "DISABLED"), null);
        } 
    }

    /**
     * Change the default startup state of the aspectj weaving bundle using the 
     * {@link SimpleConfiguratorManipulator}
     * @param becomeEnabled if true, then ensure that the bundle is set to autostarted 
     * if false, the bundle should not be autostarted
     * @return {@link Status#OK_STATUS} if all goes well, otherwise returns the exception
     */
    private IStatus changeSimpleConfiguratorManipulator(boolean becomeEnabled) {
        SimpleConfiguratorManipulator manipulator = new SimpleConfiguratorManipulatorImpl();
        BundleContext bundleContext = null;
        try {
            bundleContext = Platform.getBundle(ScalaJDTWeavingPlugin.ID).getBundleContext();
        } catch (Exception e) {
            return new Status(IStatus.ERROR, ScalaJDTWeavingPlugin.ID, "Cannot get bundleContext", e);
        }
        if (bundleContext == null) {
            return new Status(IStatus.ERROR, ScalaJDTWeavingPlugin.ID, "Cannot get bundleContext", new Exception());
        }
        
        try {
            BundleInfo[] infos = manipulator.loadConfiguration(bundleContext, null);
            BundleInfo weavingInfo = null;
            for (BundleInfo info : infos) {
                if (info.getSymbolicName().equals("org.eclipse.equinox.weaving.aspectj")) {
                    weavingInfo = info;
                    break;
                }
            }
            if (weavingInfo == null) {
                return new Status(IStatus.ERROR, ScalaJDTWeavingPlugin.ID, "Could not find equinox aspects bundle", new Exception());
            }
            
            weavingInfo.setMarkedAsStarted(becomeEnabled);
            
            URL configURL = Platform.getConfigurationLocation().getURL();
            if (configURL == null || !"file".equals(configURL.getProtocol())) {
                throw new IOException("Platform configuration location is not found: " + configURL);
            }
            
            URL installURL = Platform.getInstallLocation().getURL();
            if (installURL == null || !"file".equals(installURL.getProtocol())) {
                throw new IOException("Platform install location is not found: " + installURL);
            }
            manipulator.saveConfiguration(infos, new File(configURL.getFile() + SimpleConfiguratorManipulator.BUNDLES_INFO_PATH), new File(installURL.getFile()).toURI());
            
            return Status.OK_STATUS;
        } catch (IOException e) {
            return new Status(IStatus.ERROR, ScalaJDTWeavingPlugin.ID, "Cannot load configuration", e);
        }
    }

    private IStatus getInstalledBundleInformation() {
        StringBuffer sb = new StringBuffer();
        sb.append("Information on currently installed bundles:\n");
        
        Bundle[] allEABundles = Platform.getBundles("org.eclipse.equinox.weaving.aspectj", null); //$NON-NLS-1$
        if (allEABundles != null) {
            for (int i = 0; i < allEABundles.length; i++) {
                Bundle bundle = allEABundles[i];
                sb.append(createBundleNameString(bundle));
            }
        } else {
            sb.append("org.eclipse.equinox.weaving.aspectj not installed\n");
        }
        
        Bundle[] allWeaverBundles = Platform.getBundles("org.aspectj.weaver", null);
        if (allWeaverBundles != null) {
            for (int i = 0; i < allWeaverBundles.length; i++) {
                Bundle bundle = allWeaverBundles[i];
                sb.append(createBundleNameString(bundle));
            }
        } else {
            sb.append("org.aspectj.weaver not installed\n");
        }
        
        allWeaverBundles = Platform.getBundles("com.springsource.org.aspectj.weaver", null);
        if (allWeaverBundles != null) {
            for (int i = 0; i < allWeaverBundles.length; i++) {
                Bundle bundle = allWeaverBundles[i];
                sb.append(createBundleNameString(bundle));
            }
        } else {
            sb.append("com.springsource.org.aspectj.weaver not installed\n");
        }
        
        Bundle[] allWeavingHooks = Platform.getBundles("org.eclipse.equinox.weaving.hook", null);
        if (allWeavingHooks != null) {
            for (int i = 0; i < allWeavingHooks.length; i++) {
                Bundle bundle = allWeavingHooks[i];
                sb.append(createBundleNameString(bundle));
            }
        } else {
            sb.append("org.eclipse.equinox.weaving.hook not installed\n");
        }

        
        return new Status(IStatus.INFO, ScalaJDTWeavingPlugin.ID, sb.toString());
    }

    private StringBuffer createBundleNameString(Bundle bundle) {
        StringBuffer sb = new StringBuffer();
        sb.append(bundle.getSymbolicName()).append("_")
            .append(bundle.getVersion()).append(" : ID ")
            .append(bundle.getBundleId()).append(": STATE ");
        switch (bundle.getState()) {
            case Bundle.ACTIVE:
                sb.append("ACTIVE");
                break;

            case Bundle.INSTALLED:
                sb.append("INSTALLED");
                break;

            case Bundle.RESOLVED:
                sb.append("RESOLVED");
                break;

            case Bundle.STARTING:
                sb.append("STARTING");
                break;

            case Bundle.STOPPING:
                sb.append("STOPPING");
                break;
        }
        
        sb.append("\n");
        return sb;
    }
    
    private IStatus changeAutoStartupAspectsBundle(boolean becomeEnabled) {
        
        // get all versions of weaving.aspectj in the platform.  
        // disable and stop all but the most receent
        Bundle[] allEABundles = Platform.getBundles("org.eclipse.equinox.weaving.aspectj", null); //$NON-NLS-1$
        if (allEABundles == null || allEABundles.length == 0) {
            return new Status(IStatus.ERROR, ScalaJDTWeavingPlugin.ID, "Could not find org.eclipse.equinox.weaving.aspectj" +
            		" so weaving service cannot be " + 
            		(becomeEnabled ? "enabled" : "disabled") + ".");
        }
        try {
            State state = Platform.getPlatformAdmin().getState(false);
            if (becomeEnabled) {
                allEABundles[0].start();
                for (int i = 1; i < allEABundles.length; i++) {
                    allEABundles[i].stop();
                    BundleDescription desc = state.getBundle(allEABundles[i].getBundleId());
                    DisabledInfo info = new DisabledInfo(
                            "org.eclipse.contribution.weaving.jdt", //$NON-NLS-1$
                            "Disabled older version of Equinox Aspects", desc); //$NON-NLS-1$
                    try {
                        Platform.getPlatformAdmin().addDisabledInfo(info);
                    } catch (IllegalArgumentException e) {
                        // can ignore
                    }
                }
            } else {
                for (int i = 0; i < allEABundles.length; i++) {
                    switch(allEABundles[i].getState()) {
                        case Bundle.ACTIVE:
                        case Bundle.INSTALLED:
                        case Bundle.STARTING:
                        case Bundle.RESOLVED:
                            allEABundles[i].stop();
                    }
                }
            }
            
            return Status.OK_STATUS;
        } catch (Exception e) {
            return new Status(IStatus.ERROR, ScalaJDTWeavingPlugin.ID, "Error occurred in setting org.eclipse.equinox.weaving.aspectj to autostart" +
                    " so weaving service cannot be " + 
                    (becomeEnabled ? "enabled" : "disabled") + ".", e);
        }
    }

    private IStatus changeConfigDotIni(boolean becomeEnabled) {
        
        // a little crude find the config.ini go through each 
        // line and filter out the osgi.framework.extensions line
        IStatus success;
        try {
            String configArea = getConfigArea();
            File f = new File(new URI(configArea));
            if (f.canWrite()) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                
                String newConfig = internalChangeWeavingState(becomeEnabled, br);
                BufferedWriter bw = new BufferedWriter(new FileWriter(f));
                bw.write(newConfig);
                bw.close();
    
                
                if (becomeEnabled == currentConfigStateIsWeaving()) {
                    success = Status.OK_STATUS;
                } else {
                    success = new Status(IStatus.ERROR, ScalaJDTWeavingPlugin.ID, "Could not add or remove org.eclipse.equinox.weaving.hook as a framework adaptor.");
                }
            } else {
                // cannot write to file...most likely this is because the config.ini 
                // is in a global location that is read-only.
                success = new Status(IStatus.WARNING, ScalaJDTWeavingPlugin.ID, "Could not add " +
                		"'osgi.framework.extensions=org.eclipse.equinox.weaving.hook'\n " +
                		"to the config.ini because the file is read-only.  Weaving may not be enabled.");
            }
        } catch (Exception e) {
            success = new Status(IStatus.ERROR, ScalaJDTWeavingPlugin.ID, e
                    .getMessage(), e);
        }
        return success;
    }

    protected String internalChangeWeavingState(boolean becomeEnabled, 
            BufferedReader br) throws IOException {
        StringBuffer sb = new StringBuffer();
        String line = null;
        boolean hookAdded = false;
        while ((line = br.readLine()) != null) {
            if (line.trim().startsWith("osgi.framework.extensions=")) {
                String[] split = line.split("=");
                if (split.length > 1) {
                    String[] extNames = split[1].split(",");
                    boolean shouldAddLine = false;
                    StringBuffer sb2 = new StringBuffer();
                    sb2.append("osgi.framework.extensions=");
                    for (int i = 0; i < extNames.length; i++) {
                        String extName = extNames[i].trim();
                        // don't add hook, we add it later in needed
                        if (!extName.equals("org.eclipse.equinox.weaving.hook")) {
                            sb2.append(extName + ",");
                            shouldAddLine = true;
                        }
                    }
                    if (shouldAddLine) {
                        if (becomeEnabled) {
                            sb2.append("org.eclipse.equinox.weaving.hook\n");
                            hookAdded = true;
                        } else {
                            // replace last comma
                            sb2.replace(sb2.length() - 1, sb2.length(), "\n");
                        }
                        sb.append(sb2);
                    }
                }
            } else {
                sb.append(line + "\n");
            }
        }

        // if line didn't exist before
        if (becomeEnabled && !hookAdded) {
            sb.append("osgi.framework.extensions=org.eclipse.equinox.weaving.hook\n");
        }
        try {
            br.close();
        } catch (IOException e) {
            ScalaJDTWeavingPlugin.logException(e);
        }
        return sb.toString();
    }

    private String getConfigArea() {
        String configArea = FrameworkProperties.getProperty("osgi.configuration.area") + "config.ini";
        configArea = configArea.replaceAll(" ", "%20");
        return configArea;
    }
    
    
    public boolean currentConfigStateIsWeaving() throws Exception {
        String configArea = getConfigArea();
        
        File f = new File(new URI(configArea));
        if (! f.exists()) {
            throw new FileNotFoundException("Could not find config file: " + f.getAbsolutePath());
        }
        if (! f.canWrite()) {
            throw new IOException("Could not write to config file: " + f.getAbsolutePath());
        }
        BufferedReader br = new BufferedReader(new FileReader(f));
        return internalCurrentConfigStateIsWeaving(br);
    }

    protected boolean internalCurrentConfigStateIsWeaving(BufferedReader br)
            throws IOException {
        String line = null;
        while ((line = br.readLine()) != null) {
            if (line.trim().startsWith("osgi.framework.extensions=") &&
                    line.contains("org.eclipse.equinox.weaving.hook")) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isWeaving() {
        return IS_WEAVING;
    }
}
