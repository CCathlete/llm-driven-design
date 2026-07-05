#!/usr/bin/env sh
# dtr-builder.bst — Bootstrap script for the DTR Builder executable.
# Calls the self-executing dtr-builder fat JAR.
# Usage: ./dtr-builder.bst --out output.dtr [options]

exec "$(dirname "$0")/dtr-builder" "$@"
