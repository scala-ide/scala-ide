#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.9.0-SNAPSHOT
PROFILE_NAME="-P local-scala-trunk,!scala-trunk"

build $*
