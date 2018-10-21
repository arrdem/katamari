(ns katamari.server.extensions.roll-handlers
  "Katamari tasks for working with the build graph."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [clojure.java.shell :as jsh]
            [clojure.set :refer [rename-keys]]
            [me.raynes.fs :as fs]

            ;; Deps
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.reader :as reader]
            [clojure.tools.deps.alpha.util.io :as io :refer [printerrln]]
            [clojure.tools.deps.alpha.script.make-classpath :as mkcp]
            [clojure.tools.deps.alpha.script.parse :as deps-parser]

            ;; Katamari
            [katamari.roll.reader :refer [compute-buildgraph refresh-buildgraph-for-changes]]
            [katamari.deps.extensions.roll :as der]
            [katamari.server.extensions :refer [defhandler defwrapper]]

            ;; Ring
            [ring.util.response :as resp]

            [hf.depstar.uberjar :as ds])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

;;;; Handlers

(defonce +buildgraph-cache+
  (atom {}))

(defwrapper wrap-buildgraph
  [handler config stack request]
  (let [graph (get (swap! +buildgraph-cache+
                          update (:repo-root config)
                          #(or (and % (refresh-buildgraph-for-changes config %))
                               (compute-buildgraph config)))
                   (:repo-root config))]
    (handler (assoc config :buildgraph graph) stack request)))

(defhandler classpath
  "Usage:
  ./kat classpath [deps-options] -- [target ...]

Compute a classpath and libs mapping for selected target(s)"
  [handler config stack request]
  (-> _
      (rename-keys {:classpath :msg})
      (assoc :intent :msg)
      resp/response
      (resp/status 200)))

(defhandler list-targets
  "Enumerate all the available Rollfile targets."
  [handler config stack request]
  (-> (:buildgraph config)
      (select-keys [:targets])
      resp/response
      (resp/status 200)))

(defhandler uberjar
  "Usage:
  ./kat uberjar [target]

Given a single target, produce an uberjar according to the target's config.

WARNING: As this is a special case of the compile task, it may be removed."

  [handler config stack request]
  (if-let [target (second request)]
    (if-let [target-coord (get-in config [:buildgraph :targets (symbol target)])]
      (-> _
          resp/response
          (resp/status 200))
      (-> "Could not produce an uberjar, no target coordinate was loaded!"
          resp/response
          (resp/status 400)))
    (-> "Could not produce an uberjar, no target provided!"
        resp/response
        (resp/status 400))))
