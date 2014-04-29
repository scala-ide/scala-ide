#!/bin/bash -e

# run in sequences the different maven calls needed to fully build Scala IDE from scratch

#ROOT_DIR=$(dirname $(readlink -f $0))
# not as good as the readlink version, but it is not working on os X
ROOT_DIR=$(dirname $0)
cd ${ROOT_DIR}
ROOT_DIR=${PWD}

if [ -z "$*" ]
then
  MVN_ARGS="-Pscala-2.11.x -Peclipse-juno clean install"
  MVN_P2_ARGS="-Pscala-2.11.x -Peclipse-juno clean package"
else
  MVN_ARGS="$*"
  MVN_P2_ARGS="$*"
fi

echo "Running with: mvn ${ARGS}"

# the parent project
echo "Building parent project in $ROOT_DIR"
cd ${ROOT_DIR}
mvn ${MVN_ARGS}

echo "Building the toolchain"
# the toolchain
cd ${ROOT_DIR}/org.scala-ide.build-toolchain
mvn ${MVN_ARGS}

echo "Generating the local p2 repositories"
# the p2 repos
cd ${ROOT_DIR}/org.scala-ide.p2-toolchain
mvn ${MVN_P2_ARGS}

#
#
## set the versions if required
#cd ${ROOT_DIR}
#if [ -n "${SET_VERSIONS}" ]
#then
#  echo "setting versions"
#  mvn ${MVN_P2_ARGS} -Pset-versions exec:java
#else
#  echo "Not running UpdateScalaIDEManifests."
#fi
#
## set features.xml
#echo "Setting features.xml"
#(mvn ${ARGS} -Pset-features antrun:run) || exit -1
#
## the plugins
#echo "Building plugins"
#cd ${ROOT_DIR}/org.scala-ide.sdt.build
#mvn ${MVN_P2_ARGS}

