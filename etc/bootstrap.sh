#!/bin/bash

# A helper script for developing Katamari - lets kat re-uberjar itself, backs the current jar up,
# tries to reboot into the new jar, and if that fails puts the old jar back.

jar=target/katamari-standalone.jar

[ ! -f "$jar" ] || rm "${jar}"

./kat -j compile me.arrdem/katamari+uberjar

if [ ! -f "$jar" ]; then
    exit 1
else
    mv .kat.d/bootstrap.jar kat-backup.jar
    cp $jar .kat.d/bootstrap.jar

    # Try standalone
    KAT_SERVER_CP=.kat.d/bootstrap.jar ./kat restart-server && \
        ./kat compile me.arrdem/katamari+uberjar || (
        cp kat-backup.jar .kat.d/bootstrap.jar
        ./kat start-server
    )

    echo "Standalone seemingly OK..."

    # Try in "normal" configuration with the sources paths
    ./kat restart-server && \
        ./kat compile me.arrdem/katamari+uberjar || (
        cp kat-backup.jar .kat.d/bootstrap.jar
        ./kat start-server
    )

    echo "Still looking good!"
fi
