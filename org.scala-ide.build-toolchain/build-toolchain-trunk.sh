#!/bin/sh

. $(dirname $0)/env.sh

# Scala compiler version to be used as a dependency
SCALA_VERSION=2.10.0-SNAPSHOT

# Scala library version
SCALA_LIBRARY_VERSION=2.10.0-SNAPSHOT

# The precompiled compiler-interface.jar that SBT uses to communicate with scalac
# This is usually the same as Scala version, and it is not enough to be just binary 
# compatible (compiler interface depends on scalac, not just std library)
SCALA_PRECOMPILED=2_10_0-SNAPSHOT

# The scala version that was used to compile SBT dependencies (it is part of the artifact name)
# It is usually enough to have a binary compatible version of SCALA_VERSION
# For instance, here we can use 2.9.0, for a 2.9.1 compiler
SBT_SCALA_VERSION=2.10.0-SNAPSHOT

SBINARY_VERSION=0.4.0

set_version ${SCALA_VERSION}

PROFILE="-P sbt-2.10,default"

build $*
