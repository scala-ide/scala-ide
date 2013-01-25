#!/bin/bash

# run in sequences the different maven calls needed to fully build Scala IDE from scratch

#ROOT_DIR=$(dirname $(readlink -f $0))
# not as good as the readlink version, but it is not working on os X
ROOT_DIR=$(dirname $0)
cd ${ROOT_DIR}
ROOT_DIR=${PWD}

if [ -z "$*" ]
then
  ARGS="-P scala-2.10.x clean install"
else
  ARGS="$*"
fi

echo "Running with: mvn ${ARGS}"

# the parent project
cd ${ROOT_DIR}
mvn ${ARGS}

RES=$?
if [ ${RES} != 0 ]
then
  exit ${RES}
fi

# the toolchain
cd ${ROOT_DIR}/org.scala-ide.build-toolchain
mvn ${ARGS}

RES=$?
if [ ${RES} != 0 ]
then
  exit ${RES}
fi

# set the versions if required
cd ${ROOT_DIR}
if [ -n "${SET_VERSIONS}" ]
then
  mvn -Pset-versions exec:java
  RES=$?
  if [ ${RES} != 0 ]
  then
    exit ${RES}
  fi
else
  echo "Not running UpdateScalaIDEManifests."
fi

# the plugins
cd ${ROOT_DIR}/org.scala-ide.sdt.build
mvn ${ARGS}

RES=$?
if [ ${RES} != 0 ]
then
  exit ${RES}
fi
