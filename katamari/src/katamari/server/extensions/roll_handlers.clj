(ns katamari.server.extensions.roll-handlers
  "Katamari tasks for working with the build graph."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [me.raynes.fs :as fs]

            ;; Katamari
            [roll.core :as roll]
            [roll.reader :refer [compute-buildgraph refresh-buildgraph-for-changes]]
            [roll.cache :as cache]
            [katamari.server.extensions :refer [defhandler defwrapper]]

            ;; Ring
            [ring.util.response :as resp])
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
    (handler (assoc config
                    :buildgraph graph
                    :buildcache (cache/->buildcache
                                 (fs/file (:repo-root config)
                                          (:server-work-dir config)
                                          (:server-build-cache config))))
             stack request)))

(defhandler compile
  "Compile specified target(s), producing any outputs.

Usage:
  ./kat compile target1 target2...

Causes the specified targets to be compiled.

Produces a map from target identifiers to build products."
  [handler config stack request]
  (case (first request)
    "compile"
    (if-let [targets (map symbol (rest request))]
      (-> (roll/roll config (:buildcache config) (:buildgraph config) targets)
          (assoc :intent :json)
          (resp/response)
          (resp/status 200))

      (-> {:intent :msg
           :mgs "No target provided!"}
          resp/response
          (resp/status 400)))

    (handler config stack request)))

(defhandler list-targets
  "Enumerate all the available Rollfile targets."
  [handler config stack request]
  (-> {:intent :json
       :targets (-> config :buildgraph :targets keys)}
      (resp/response)
      (resp/status 200)))

(defhandler clean-cache
  "Flush the cache on a TTL basis.

Usage:
  ./kat clean-cache [ttl-ms]

Removes roll cache entries older than the specified number of milliseconds,
defaulting to the server's configured cache TTL."
  [handler config stack request]
  (case (first request)
    "clean-cache"
    (if-let [ttl (or (some-> request second Long/parseLong)
                     (some-> config :server-build-cache-ttl Long/parseLong))]
      (let [products (cache/filter-cache-by-ttl (:buildcache config) ttl)]
        (cache/clean-products products)
        (-> {:intent :json
             :deleted-keys (map first products)}
            resp/response
            (resp/status 200)))

      (-> {:intent :msg
           :msg "No TTL provided!"}
          (resp/response)
          (resp/status 400)))))
