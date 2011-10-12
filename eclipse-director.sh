#!/bin/bash

eclipse_dir="/Applications/Programming/eclipse-helios/"
eclipse_opt="-nosplash -consoleLog"
app="org.eclipse.equinox.p2.director"

eclipse_repo="http://download.eclipse.org/releases/helios/"
branch="master"
scala_repo="http://scala-ide.dreamhosters.com/nightly-update"

function usage()
{
    cat <<EOF
`basename $0` [opt] command [pluginid]

Options:
    --eclipse-dir <path>      Path to the Eclipse installation that you want to modify

Commands:
    list                      List available plugins. (useless right now, as it uses the Eclipse repo).

    install <id>              Install plugin. It is the version number of the Scala installation
                              For instance: 2.9.1.final or trunk

    uninstall                 Uninstall the currently installed Scala plugin

    uninstall-bundle <id>     Uninstall the given bundle

    help                      Print this help screen

EOF
    exit 1
}

while [ $# -gt 0 ]; do

    case $1 in 
        "" | "help")
            usage
            ;;

        "--eclipse-dir")
            eclipse_dir=$2
            echo "Eclipse installation dir is $eclipse_dir"
            shift 2
            ;;

        "--branch")
            branch=$2
            echo "Eclipse repository is $scala_repo-$branch-?"
            shift 2
            ;;

        "list")
            $eclipse_dir/eclipse $eclipse_opt \
                -application $app \
                -repository $eclipse_repo \
                -list
            shift
            ;;

        "install")
            echo "Installing $2.."
            $eclipse_dir/eclipse $eclipse_opt \
                -application $app \
                -repository "$scala_repo-$branch-$2" \
                -installIU org.scala-ide.sdt.feature.feature.group
            shift 2
            ;;

        "uninstall")
            echo "Unnstalling.."
            shift 1
            $eclipse_dir/eclipse $eclipse_opt \
                -application $app \
                -repository "$eclipse_repo" \
                -uninstallIU org.scala-ide.sdt.feature.feature.group
            ;;

        "uninstall-bundle")
            echo "Unnstalling.. $2"
            $eclipse_dir/eclipse $eclipse_opt \
                -application $app \
                -repository "$eclipse_repo" \
                -uninstallIU $2
            shift 2
            ;;

        *)
            usage
            ;;
    esac
done
