#!/bin/bash

# run in sequences the different maven calls needed to fully build Scala IDE from scratch


#ROOT_DIR=$(dirname $(readlink -f $0))
# not as good as the readlink version, but it is not working on os X
ROOT_DIR=$(dirname $0)

RUNNING_DIR=${PWD}

if [ -z "$*" ]
then
  ARGS="clean install"
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
cd ${RUNNING_DIR}
cd ${ROOT_DIR}/org.scala-ide.build-toolchain
mvn ${ARGS}

RES=$?
if [ ${RES} != 0 ]
then
  exit ${RES}
fi

# the plugins
cd ${RUNNING_DIR}
cd ${ROOT_DIR}/org.scala-ide.sdt.build
mvn ${ARGS}

RES=$?
if [ ${RES} != 0 ]
then
  exit ${RES}
fi

