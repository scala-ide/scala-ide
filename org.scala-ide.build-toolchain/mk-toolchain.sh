ECLIPSE=/Applications/Programming/eclipse-indigo/eclipse
SCALA_VERSION=2.10.0-SNAPSHOT
SCALA_LIBRARY_VERSION=2.10.0-SNAPSHOT
REPO_SUFFIX=trunk
LOCAL_REPO=`pwd`/m2repo

#./build-toolchain-2.9.x.sh

SOURCE=`pwd`/mkrepo
PLUGINS=${SOURCE}/plugins
REPO_NAME=scala-eclipse-toolchain-osgi-${REPO_SUFFIX}
REPO=file:${SOURCE}/${REPO_NAME}


rm -Rf ${SOURCE}
mkdir -p ${PLUGINS}
cp ../org.scala-ide.scala.compiler/target/org.scala-ide.scala.compiler-${SCALA_VERSION}.jar ${PLUGINS}
cp ../org.scala-ide.scala.library/target/org.scala-ide.scala.library-${SCALA_LIBRARY_VERSION}.jar ${PLUGINS}
cp ../org.scala-ide.sbt.full.library/target/org.scala-ide.sbt.full.library*jar ${PLUGINS}

$ECLIPSE \
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

cat <<EOF

	The P2 repository is in: ${REPO} 

	To build the IDE using the local repository for trunk, use the following command
	../org.scala-ide.build/build-ide-local-trunk.sh -Drepo.toolchain="file:${SOURCE}"
EOF

#scp -r ${SOURCE}/${REPO_NAME} scalaide@scala-ide.dreamhosters.com:scala-ide.dreamhosters.com/incoming-toolchain-osgi-${REPO_SUFFIX}/
#ssh scalaide@scala-ide.dreamhosters.com scala-ide/update-nightly-toolchain-osgi-${REPO_SUFFIX}.sh
