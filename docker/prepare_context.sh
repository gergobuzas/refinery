#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2023-2024 The Refinery Authors <https://refinery.tools/>
#
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

refinery_version="$(./get_version.sh)"
cli_distribution_name="refinery-generator-cli-${refinery_version}"
web_distribution_name="refinery-language-web-${refinery_version}"
generator_distribution_name="refinery-generator-server-${refinery_version}"

rm -rf "${cli_distribution_name}" "${web_distribution_name}" \
 "${generator_distribution_name}" {cli,web,generator}_dist context/extracted

tar -xvf "../subprojects/generator-cli/build/distributions/${cli_distribution_name}.tar"
mv "${cli_distribution_name}" cli_dist
tar -xvf "../subprojects/language-web/build/distributions/${web_distribution_name}.tar"
mv "${web_distribution_name}" web_dist
tar -xvf "../subprojects/generator-server/build/distributions/${generator_distribution_name}.tar"
mv "${generator_distribution_name}" generator_dist
mkdir -p context/extracted/{cli,web,generator}_{,app_}lib \
    context/extracted/common_{,amd64_,arm64_}lib \
    context/extracted/{cli,web,generator}_{amd64,arm64}_bin

move_application_jars() {
    prefix="$1"
    # Our application itself is very small, so it will get added as the last layer
    # of both containers.
    mv "${prefix}"_dist/lib/refinery-* "context/extracted/${prefix}_app_lib"
}

move_application_jars cli
move_application_jars web
move_application_jars generator

# THE NEXT THREE PARTS are for  Dependency deduplication for optimizing Docker \
  # image creation

# Dependency deduplication for optimizing Docker image creation
# TODO refactor to function

# Helper function to check if file exists in another distribution
#check_file_exists() {
#    local source_file="$1"
#    local other_dist="$2"
#    local filename="${source_file##*/}"
#    [[ -f "${other_dist}/lib/${filename}" ]]
#}
#
## Function to move a file to common lib and remove duplicates
#move_to_common() {
#    local source_file="$1"
#    local filename="${source_file##*/}"
#    mv "$source_file" "context/extracted/common_lib/${filename}"
#    # Remove duplicates from other distributions if they exist
#    if [[ -f "web_dist/lib/${filename}" ]]; then
#      rm "web_dist/lib/${filename}"
#    fi
#    if [[ -f "generator_dist/lib/${filename}" ]]; then
#      rm "generator_dist/lib/${filename}"
#    fi
#    if [[ -f "cli_dist/lib/${filename}" ]]; then
#      rm "cli_dist/lib/${filename}"
#    fi
#}
#
## Process all files in each distribution
#for dist in cli web generator; do
#    echo "dist:"$dist
#    for file in ${dist}_dist/lib/*;
#    do
#        echo "file:"$file
#        if [[ -f "$file" ]]; then
#          case $dist in
#              "cli")
#                  # Check if file exists in web or generator
#                  if check_file_exists "$file" "web_dist" || check_file_exists "$file" "generator_dist"; then
#                      move_to_common "$file"
#                  fi
#                  ;;
#              "web")
#                  # Check if file exists in generator (cli already processed)
#                  if check_file_exists "$file" "generator_dist"; then
#                      move_to_common "$file"
#                  fi
#                  ;;
#              "generator")
#                  # All files already processed, no need to check
#                  ;;
#          esac
#        fi
#    done
#done
#
#echo "LOL"

for i in cli_dist/lib/*; do
    j="web${i#cli}"
    if [[ -f "$j" ]]; then
        mv "$i" "context/extracted/common_lib${i#cli_dist/lib}"
        rm "$j"
    fi
done
#
#for i in cli_dist/lib/*; do
#    j="generator${i#cli}"
#    c="context/extracted/common_lib${i#cli_dist/lib}"
#    if [[ -f "$j" ]]; then
#        mv "$i" "context/extracted/common_lib${i#cli_dist/lib}"
#        rm "$j"
#    fi
#done
#
#for i in generator_dist/lib/*; do
#    j="web${i#generator}"
#    echo j $j
#    if [[ -f "$j" ]]; then
#        mv "$i" "context/extracted/common_lib${i#generator_dist/lib}"
#        rm "$j"
#    fi
#done

# Move architecture-specific jars to their repsective directories.
mv context/extracted/common_lib/ortools-linux-x86-64-*.jar context/extracted/common_amd64_lib
mv context/extracted/common_lib/ortools-linux-aarch64-*.jar context/extracted/common_arm64_lib
rm context/extracted/common_lib/ortools-{darwin,win32}-*.jar

prepare_application() {
    prefix="$1"
    suffix="$2"
    # Move the applications jars for the dependencies into a separate Docker layer
    # to enable faster updates.
    mv "${prefix}"_dist/lib/* "context/extracted/${prefix}_lib"
    # Omit references to jars not present for the current architecture from the
    # startup scripts.
    sed 's/:\$APP_HOME\/lib\/ortools-\(darwin\|win32\|linux-aarch64\)[^:]\+\.jar//g' \
        "${prefix}_dist/bin/refinery-${suffix}" \
        > "context/extracted/${prefix}_amd64_bin/refinery-${suffix}"
    sed 's/:\$APP_HOME\/lib\/ortools-\(darwin\|win32\|linux-x86-64\)[^:]\+\.jar//g' \
        "${prefix}_dist/bin/refinery-${suffix}" \
        > "context/extracted/${prefix}_arm64_bin/refinery-${suffix}"
    chmod a+x "context/extracted/${prefix}"_{amd64,arm64}_bin/refinery-"${suffix}"
}

prepare_application cli generator-cli
prepare_application web language-web
prepare_application generator generator-server

rm -rf {cli,web,generator}_dist
