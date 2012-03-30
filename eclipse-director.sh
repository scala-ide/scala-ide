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
    --eclipse-dir <path>        Path to the Eclipse installation that you want to modify

    --branch <branch>           What branch to use? (e.g. 'master' or '2-0-x')

Commands:
    list                        List available plugins. (useless right now, as it uses the Eclipse repo).

    install <version>           Install plugin. It is the version number of the Scala installation
                                For instance: 2.9.2-SNAPSHOT or trunk

    install <version>/YYYYMMDD  Install the nightly for the given date. <version> is one of 'trunk' or 
                                '2.9.2-SNAPSHOT'

    install-local <path>        Install from a local update site given by <path>

    uninstall                   Uninstall the currently installed Scala plugin

    install-bundle <id>         Install the given bundle

    uninstall-bundle <id>       Uninstall the given bundle

    help                        Print this help screen

EOF
    exit 1
}

#
# match the build dir name in the dir listing HTML response
#
build_dir_regex='s/.*<a href=\"\(.*\)\">.*/\1/'

#
# $1 - repo_base
# $2 - repo_date
#
function find_latest_build()
{
    matches=`curl -s "$1/" | grep $2 | sed "${build_dir_regex}"`

    arr_match=(${matches})
    len=${#arr_match[@]}

    if [[ len -eq 0 ]]; then
        echo "No repository found for $repo_date, probably no nightlies were pushed on that date."
        echo "Other nightlies on the same month (${2:0:6}):"
        curl  -s "$1/" | grep ${2:0:6} | sed "${build_dir_regex}"
        exit 1
    fi

    echo "found nightly on ${arr_match[len - 1]}"
    update_site="$1/${arr_match[len - 1]}/org.scala-ide.sdt.update-site"
}

#
# $1 - specification: trunk or trunk/YYYYMMDD (date)
#      similarly, 2.9.2-SNAPSHOT or 2.9.2-SNAPSHOT/YYYYMMDD
#
function install()
{
    case $1 in
        trunk )
            update_site="$scala_repo-$branch-trunk"
            ;;

        trunk/[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9] )
            echo "Looking for a specific nightly.."
            repo_date=`echo $1 | cut -d \/ -f 2`
            find_latest_build "http://download.scala-ide.org/builds/nightly-$branch-trunk" $repo_date
            ;;

        2.9.2-SNAPSHOT )
            update_site="$scala_repo-$branch-trunk"
            ;;

        2.9.2-SNAPSHOT/[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9] )
            repo_date=`echo $1 | cut -d \/ -f 2`
            find_latest_build "http://download.scala-ide.org/builds/nightly-$branch-2.9.2-SNAPSHOT" $repo_date
            ;;

        * )
            echo -e "Not understood: $1.\n"
            usage
            ;;
    esac

    echo "Installing from $update_site.."
    $eclipse_dir/eclipse $eclipse_opt \
        -application $app \
        -repository $update_site/ \
        -installIU org.scala-ide.sdt.feature.feature.group

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
            install $2
            shift 2
            ;;

        "install-local")
            echo "Installing $2.."
            $eclipse_dir/eclipse $eclipse_opt \
                -application $app \
                -repository "file://$2" \
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
