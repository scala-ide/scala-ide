#!/bin/bash -e

# run in sequences the different maven calls needed to fully build Scala IDE from scratch

#ROOT_DIR=$(dirname $(readlink -f $0))
# not as good as the readlink version, but it is not working on os X
ROOT_DIR=$(dirname $0)
cd ${ROOT_DIR}
ROOT_DIR=${PWD}

if [ -z "$*" ]
then
  ARGS="-Pscala-2.10.x -Peclipse-juno clean install"
else
  ARGS="$*"
fi

echo "Running with: mvn ${ARGS}"

# the parent project
echo "Building parent project in $ROOT_DIR"
cd ${ROOT_DIR}
mvn ${ARGS}

echo "Building the toolchain"
# the toolchain
cd ${ROOT_DIR}/org.scala-ide.build-toolchain
mvn ${ARGS}


# set the versions if required
cd ${ROOT_DIR}
if [ -n "${SET_VERSIONS}" ]
then
  echo "setting versions"
  mvn -Pset-versions exec:java
else
  echo "Not running UpdateScalaIDEManifests."
fi

# the plugins
echo "Building plugins"
cd ${ROOT_DIR}/org.scala-ide.sdt.build
mvn ${ARGS}

