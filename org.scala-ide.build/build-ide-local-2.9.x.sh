#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.9.2-SNAPSHOT
SCALA_LIBRARY_VERSION=2.9.1

PROFILE_NAME="-P local-scala-2.9.x,!scala-trunk,!scala-2.9.2-SNAPSHOT"

build $*