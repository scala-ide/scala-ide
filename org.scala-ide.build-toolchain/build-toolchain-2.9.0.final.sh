#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.9.0
SCALA_PRECOMPILED=2_9_0
SBT_SCALA_VERSION=2.9.0
SBINARY_VERSION=0.4.0

set_version ${SCALA_VERSION}

PROFILE="-P sbt-2.9,default"

build $*
