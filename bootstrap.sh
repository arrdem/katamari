#!/bin/bash

CLOJURE_TOOLS_VERSION=1.9.0.394

if [ ! -d target ]; then
    mkdir target
fi

if [ ! -f "clojure-tools/clojure-tools-${CLOJURE_TOOLS_VERSION}.jar" ]; then
    (cd target;
     TARFILE="clojure-tools-${CLOJURE_TOOLS_VERSION}.tar.gz"
     JARFILE="clojure-tools-${CLOJURE_TOOLS_VERSION}.jar"
     if [ ! -f "${TARFILE}" ]; then
         wget "https://download.clojure.org/install/clojure-tools-${CLOJURE_TOOLS_VERSION}.tar.gz"
     fi
     
     # just extract get the jarfile, which is used for bootstrapping
     tar -xzf "clojure-tools-${CLOJURE_TOOLS_VERSION}.tar.gz" \
         "clojure-tools/${JARFILE}"
     mv "${JARFILE}" "../clojure-tools/"
    )
fi

if [ ! -f kat.conf ] || true; then
    cat <<EOF > kat.conf
# Katamari's config file

# This can be any port.
# By default 3636 is used.
server_http_port=3636
server_nrepl_port=3637

# The address the build server should bind to
server_addr=localhost

# How long to wait before declaring the server a failure to start
server_start_sec=15

# A classpath string to use when booting the server
# Used when bootstrapping Kat
#
# FIXME (arrdem 2018-09-29):
#   How do I get away from having to code this? Bootstrapping without a dist is HARD
server_classpath=$(cd katamari; bash ../clojure-tools/clojure -Spath)

# The log to record build history and any errors
log_file=kat.log

# Where to put cached build products and analysis data
# This cache lives at the repo root
server_work_dir=.kat.d

EOF
fi
