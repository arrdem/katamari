(ns katamari.deps.extensions.roll
  "A :roll coordinate extension for tools.deps.

  Used to implement Katamari's build targets as entities in deps' dependency graph."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
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
  [lib {:keys [deps] :as coord} manifest config]
  (or deps
      (get-in *buildgraph* [:targets lib :deps])))

;; FIXME (arrdem 2018-10-15):
;;
;;   This probably needs to interact with compilation - only trivial targets (eg. clojure-library)
;;   don't need to compile files/paths at all. A hypothetical `lessc` target, or the actual
;;   java-library target would need to at least check the product cache and probably compile before
;;   it could return a sequence of paths.
(defmethod ext/coord-paths :roll
  [lib {:keys [paths] :as coord} _manifest  _config]
  (or paths
      (get-in *buildgraph* [:targets lib :paths])))
