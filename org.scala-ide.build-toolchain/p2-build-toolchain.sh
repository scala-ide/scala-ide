#!/bin/sh

. $(dirname $0)/build-toolchain-2.9.sh

REPO_NAME=p2-osgi-toolchain
SOURCE=`pwd`/target
PLUGINS=${SOURCE}/plugins
REPO=file:${SOURCE}/${REPO_NAME}

mkdir -p $SOURCE
rm -Rf ${SOURCE}
mkdir -p ${PLUGINS}

cp ../org.scala-ide.scala.compiler/target/org.scala-ide.scala.compiler*.jar ${PLUGINS}
cp ../org.scala-ide.scala.library/target/org.scala-ide.scala.library*.jar ${PLUGINS}
cp ../org.scala-ide.sbt.full.library/target/org.scala-ide.sbt.full.library*jar ${PLUGINS}

eclipse \
-debug \
-consolelog \
-nosplash \
-verbose \
-application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher \
-metadataRepository ${REPO} \
-artifactRepository ${REPO} \
-source ${SOURCE} \
-compress \
-publishArtifacts

