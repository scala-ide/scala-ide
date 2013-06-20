#!/bin/bash -e

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

cd ${ROOT_DIR}/org.scala-ide.sdt.build
mvn ${ARGS}

