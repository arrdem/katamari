(ns katamari.server.tasks.roll
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [clojure.java.shell :as jsh]
            [me.raynes.fs :as fs]

            ;; Deps
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.reader :as reader]
            [clojure.tools.deps.alpha.util.io :as io :refer [printerrln]]
            [clojure.tools.deps.alpha.script.make-classpath :as mkcp]
            [clojure.tools.deps.alpha.script.parse :as deps-parser]

            ;;katamari
            [katamari.roll.reader :refer [compute-buildgraph refresh-buildgraph-for-changes]]
            [katamari.deps.extensions.roll :as der]

            ;; Ring
            [ring.util.response :as resp]))

;;;; Handlers

(defonce +buildgraph-cache+
  (atom {}))

(defn wrap-buildgraph
  [handler]
  (fn [config stack request]
    (let [graph (get (swap! +buildgraph-cache+
                            assoc (:repo-root config) (compute-buildgraph config))
                     (:repo-root config))]
      (handler (assoc config :buildgraph graph) stack request))))

(defn buildgraph->default-deps [buildgraph]
  (->> buildgraph
       :targets
       (map (fn [[name coord]]
              [name (assoc (select-keys coord [:paths :deps])
                           :deps/manifest :roll
                           :roll/name (:name coord)
                           :roll/file (:rollfile coord))]))
       (into {})))

(defn handle-classpath
  {:kat/request-name "classpath"
   :kat/doc "Compute a classpath for selected target(s)"}
  [handler]
  (fn [config stack request]
    (case (first request)
      "meta"
      (update (handler config stack request)
              :body conj (meta #'handle-classpath))

      "classpath"
      (-> (let [opts (-> (rest request)
                         (mkcp/parse-opts)
                         (update :config-files (partial concat (:deps-defaults-files config)))
                         (update :config-data #(or % (deps-parser/parse-config
                                                      (:deps-defaults-data config))))
                         (update :aliases conj ::roll))
                _ (prn opts)
                deps (-> (mkcp/combine-deps-files opts)
                         (assoc :deps (zipmap (map symbol (:arguments opts)) (repeat nil)))
                         (assoc-in [:aliases ::roll :default-deps]
                                   (buildgraph->default-deps
                                    (:buildgraph config))))
                _ (prn deps)]
            (der/with-graph (:buildgraph config)
              (mkcp/create-classpath deps opts)))
          :classpath
          resp/response
          (resp/status 200))

      (handler config stack request))))
