;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.reader
  (:require [clojure.edn :as edn]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [clojure.tools.deps.alpha.util.coll :as coll]
            [clojure.tools.deps.alpha.util.io :as io])
  (:import [java.io File IOException FileReader PushbackReader]))

(defn- scrape-clojure-env
  []
  (let [{:keys [out exit] :as result} (sh/sh "clojure" "-Sdescribe")]
    (if (zero? exit)
      (read-string out)
      (throw (ex-info "Unable to locate Clojure's edn files" result)))))

(def clojure-env
  "Returns a map describing the environment known to clj/clojure:
  {:config-files [ ... ]}"
  (memoize scrape-clojure-env))

(defn- io-err
  ^Throwable [fmt ^File f]
  (let [path (.getAbsolutePath f)]
    (ex-info (format fmt path) {:path path})))

(defn- slurp-edn-map
  "Read the file specified by the path-segments, slurp it, and read it as edn."
  [^File f]
  (let [val (io/slurp-edn f)]
    (if (map? val)
      val
      (throw (io-err "Expected edn map in: %s" f)))))

(defn- canonicalize-sym [s]
  ;; FIXME (arrdem 2018-10-15):
  ;;   The :group thing is a dirty hack. There's gotta be a better way.
  (if (and (symbol? s) (nil? (namespace s)) (not (:group (meta s))))
    (as-> (name s) n (symbol n n))
    s))

(defn- canonicalize-all-syms
  [deps-map]
  (walk/postwalk
    #(cond-> % (map? %) (coll/map-keys canonicalize-sym))
    deps-map))

(defn slurp-deps
  "Read a single deps.edn file from disk and canonicalize symbols,
  return a deps map."
  [dep-file]
  (-> dep-file slurp-edn-map canonicalize-all-syms))

(defn- merge-or-replace
  "If maps, merge, otherwise replace"
  [& vals]
  (when (some identity vals)
    (reduce (fn [ret val]
              (if (and (map? ret) (map? val))
                (merge ret val)
                (or val ret)))
      nil vals)))

(defn merge-deps
  "Merge multiple deps maps from left to right into a single deps map."
  [deps-maps]
  (apply merge-with merge-or-replace deps-maps))

(defn read-deps
  "Read a set of user deps.edn files and merge them left to right into a single deps map."
  [deps-files]
  (->> deps-files (map slurp-deps) merge-deps))

