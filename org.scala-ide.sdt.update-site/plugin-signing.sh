#!/bin/bash

ECLIPSE=/Applications/dev/eclipse-helios/Eclipse.app/Contents/MacOS/eclipse

THIS=$(pwd)
TARGET_DIR=$THIS/target
BUNDLE_DIR=$TARGET_DIR/site

EXPECTED_ARGS=4
E_BADARGS=65

if [[ ! -f ${ECLIPSE} ]]; then
	echo "Please update ECLIPSE (${ECLIPSE}) to point to an existing Eclipse installation."
	echo "Aborting.."
	exit 1
fi

if [ $# -ne $EXPECTED_ARGS ]
then
  echo "Usage: ./plugin-signing <path to keystore> <alias> <store password> <key password>"
  exit $E_BADARGS
fi

PATH_KEYSTORE=$1
ALIAS=$2
STORE_PWD=$3
KEY_PWD=$4

signJars()
{
  # $1 is the folder containing the JARs to be signed
  JAR_FOLDER=$1
  
  for i in ${JAR_FOLDER}/*.jar

  do

  jarsigner -keystore $PATH_KEYSTORE -storepass $STORE_PWD -verbose -keypass $KEY_PWD $i $ALIAS

  done
}

# These files will be recreated after the plug-in JARs are signed.
rm -rf ${BUNDLE_DIR}/artifacts.jar
rm -rf ${BUNDLE_DIR}/content.jar
rm -rf ${TARGET_DIR}/site_assembly.zip
rm -rf ${TARGET_DIR}/site.zip

FEATURES_DIR=$BUNDLE_DIR/features
PLUGINS_DIR=$BUNDLE_DIR/plugins

# Sign both the features and plugins JARs
signJars ${FEATURES_DIR}
signJars ${PLUGINS_DIR}

# This will re-create both 'artifacts.jar' and 'content.jar'
$ECLIPSE -debug -consolelog -nosplash -verbose -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher -metadataRepository file:${BUNDLE_DIR} -artifactRepository file:${BUNDLE_DIR} -source ${BUNDLE_DIR}  -compress -append -publishArtifacts

# This will make sure that the above produced plug-in is visible on p2 repository
$ECLIPSE -debug -consolelog -nosplash -verbose -application org.eclipse.equinox.p2.publisher.CategoryPublisher -metadataRepository file:${BUNDLE_DIR} -categoryDefinition file:${BUNDLE_DIR}/site.xml 

# Re-create the  site_assembly.zip
pushd target/
zip -r site_assembly.zip site/
popd
