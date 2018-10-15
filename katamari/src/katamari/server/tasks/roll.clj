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
                         (update :config-files (partial cons
                                                        (fs/file (:repo-root config)
                                                                 (:deps-defaults-file config))))
                         (update :config-data #(or % (deps-parser/parse-config
                                                      (:deps-defaults-data config)))))
                deps (-> (mkcp/combine-deps-files opts)
                         ;; Splice in CLI targets
                         (assoc :deps (zipmap (map symbol (:arguments opts)) (repeat nil)))
                         ;; Inject the defaults "profile"
                         (assoc-in [:aliases ::defaults]
                                   (deps-parser/parse-config
                                    (slurp
                                     (fs/file (:repo-root config)
                                              (:deps-resolve-file config)))))
                         ;; Inject the targets "profile"
                         (assoc-in [:aliases ::roll :override-deps]
                                   (buildgraph->default-deps
                                    (:buildgraph config))))]
            (der/with-graph (:buildgraph config)
              (mkcp/create-classpath
               deps
               ;; Bolt on our two magical internal profiles
               (update opts :aliases (partial concat [::defaults ::roll])))))
          :classpath
          resp/response
          (resp/status 200))

      (handler config stack request))))
