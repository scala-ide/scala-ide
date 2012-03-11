#!/bin/bash

# This script updates the compiler and library OSGI bundles in an existing Eclipse
# installation. Use at your own risk! It saves a lot of time if all you need is a custom 
# compiler, but it should be binary compatible (the other bundles, such as the Sbt builder
# and the Scala plugin itself, are not rebuilt against the new version and may lead to 
# MissingMethodErrors)

eclipse_dir="/Applications/Programming/eclipse-indigo/"
base_dir=""

function usage()
{
    cat <<EOF
`basename $0` [opt] command <basedir>

    Update the compiler and library bundles in an existing Eclipse installation. <basedir>
    should have a subdirectory lib/ containing scala-library.jar and/or scala-compiler.jar
    (for instance, the build/pack directory in a Scala checkout)

Options:
    --eclipse-dir <path>      Path to the Eclipse installation that you want to modify


Commands:
    update-lib <basedir>      Update the scala library with the given jar file. <basedir> should 
                              have lib/scala-library.jar.

    update-comp <basedir>     Update the scala compiler with the given jar file. <basedir> should 
                              have lib/scala-compiler.jar.

    update <basedir>          Update both the library and compiler from <basedir>/lib/scala-[library|compiler].jar

    help                      Print this help screen

EOF
    exit 1
}

function test_basedir() {
    dummy=`stat $base_dir/lib/$1`
    if [[ $? -ne 0 ]]; then
        echo "Make sure the argument passed to 'update' contains lib/$1"
        exit $?
    fi
}

#
# Return the Scala version inside a given jar file, based on the MANIFEST.MF file
# For obvious reasons, it 0-pads the minor version component, i.e. 2.9.2.rdev-... becomes 2.09.2.-rdev...
#
function get_version() {
    unzip -c "$1" META-INF/MANIFEST.MF | grep Bundle-Version | cut -d :  -f 2 | awk -F"." '{printf("%0d.%02d.%0d.%s",$1,$2,$3,$4); }'
}

#
# Return the Scala version inside a given jar file, based on the compiler or library.properties file.
# It replaces the maven-style qualifier (2.10.0-M2) with a . (2.10.0.M2)
#
function get_properties_version() {
    unzip -c "$1" "$2.properties" | grep "version.number" | cut -d =  -f 2 | sed 's/\([0-9]\)-M/\1.M/'
}

latest_file=""
newest_version=""

#
# This function popluates global variables $latest_file and $newest_version
# $1 - 'library' of 'compiler', used to build the bundle name under @eclipse_dir/plugins
#
function find_latest_version {
    count=0
    latest_file=""
    newest_version=""


    echo -e "\nLooking for jar files inside $eclipse_dir..\n"
    for f in $eclipse_dir/plugins/org.scala-ide.scala.${1}*.jar; do
        #statements
        version=`get_version $f`
        echo -e '\t' $version

        if [[ $version > $newest_version ]]; then
            latest_file=$f
            newest_version=$version
        fi

        count=$(($count+1))
    done

    if [[ $count -gt 1 ]]; then
        echo -e "Found more than one jar in your eclipse installation, using\n\t $latest_file with bundle version $newest_version."
    fi

    if [[ $count -eq 0 ]]; then
        echo "Could not find any $1 in your eclipse dir. Backing off"
        exit 1
    fi
}

#
# $1 - scala-library.jar (file to use)
# $2 - 'library' or 'compiler' (file to look for)
#
function update_jar() {
    find_latest_version $2
    to_patch=$latest_file
    backup=${to_patch}_saved

    echo "Latest file: $latest_file"

    cp $latest_file $backup
    echo -e "\nCopied original file to `basename $backup`"

    updated_version=`get_properties_version $base_dir/$1 $2`
    echo -e "\nSetting new version: $updated_version"

    echo -e "Bundle-Version: $updated_version\n" > /tmp/manf

    jar uvmf /tmp/manf $latest_file -C $base_dir $1
    rm /tmp/manf

    cached_lib=`find $eclipse_dir -iname scala-$2.jar`
    cached_dir=`dirname $cached_lib`
    echo -e "Removing cached version in $cached_dir"
    rm -rf $cached_dir
}


while [ $# -gt 0 ]; do

    case $1 in 
        "" | "help")
            usage
            ;;

        "--eclipse-dir")
            eclipse_dir=$2
            echo -e "Eclipse installation dir is $eclipse_dir\n"
            shift 2
            ;;

        "update-comp")
            base_dir=$2
            test_basedir "scala-compiler.jar"
            update_jar "lib/scala-compiler.jar" "compiler"
            shift 2
            ;;

        "update-lib")
            base_dir=$2
            test_basedir "scala-library.jar"
            update_jar "lib/scala-library.jar" "library"
            shift 2
            ;;

        "update")
            base_dir=$2
            test_basedir "scala-library.jar"
            test_basedir "scala-compiler.jar"

            update_jar "lib/scala-library.jar" "library"

            echo "..Now updating compiler.."

            update_jar "lib/scala-compiler.jar" "compiler"

            shift 2
            ;;

        *)
            usage
            ;;
    esac
done
