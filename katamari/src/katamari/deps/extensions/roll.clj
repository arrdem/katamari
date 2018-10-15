(ns katamari.deps.extensions.roll
  (:require
   [clojure.java.io :as jio]
   [clojure.string :as str]
   [clojure.tools.deps.alpha.extensions :as ext]
   [pandect.algo.sha256 :refer [sha256]]))

(def ^{:dynamic true
       :doc "The active build graph."}
  *buildgraph* {})

(defmacro with-graph
  [libs & body]
  `(binding [*buildgraph* ~libs]
     ~@body))

(defmethod ext/dep-id :roll
  [lib coord config]
  (let [data (get-in *buildgraph* [:cord :rollfile] {})]
    (get-in *buildgraph* [:files (:rollfile data) :sha256sum])))

(defmethod ext/manifest-type :roll
  [lib coord config]
  {:deps/manifest :roll})

;; Extracting deps and paths from the registry

(defmethod ext/coord-deps :roll
  [lib coord _manifest config]
  (get-in *buildgraph* [:targets lib :deps]))

(defmethod ext/coord-paths :roll
  [lib coord _manifest _config]
  (get-in *buildgraph* [:targets lib :paths]))

