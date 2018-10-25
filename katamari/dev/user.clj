(ns user
  (:require [katamari.roll.reader]))

(def +root+
  "/Users/reid.mckenzie/Documents/dat/git/arrdem/katamari")

(def +conf+
  (merge (katamari.conf/load (str +root+ "/kat.conf")
                             katamari.server.web-server/key-fn)
         {:repo-root +root+}))

(def +graph+
  (katamari.roll.reader/compute-buildgraph +conf+))
