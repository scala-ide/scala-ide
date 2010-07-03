#!/bin/sh

. $(dirname $0)/env.sh

SCALA_TOOLCHAIN_VERSION=2.8.0-RC6
SCALA_VERSION=2.8.0.RC6

set_toolchain_version ${SCALA_TOOLCHAIN_VERSION}
build_both $*
