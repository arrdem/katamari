(ns katamari.tasks.tools-deps
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [clojure.java.shell :as jsh]
            [me.raynes.fs :as fs]
            ;; Deps
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.reader :as reader]
            [clojure.tools.deps.alpha.util.io :as io :refer [printerrln]]
            ;; Ring
            [ring.util.response :as resp]))

(defn read-deps
  "Given a sequence of filenames, load them as deps data, reducing
  `merge-deps` over the loaded data to return a composite
  \"effective\" deps mapping."
  [filenames]
  (->> filenames
       (map (comp edn/read
                  #(java.io.PushbackReader. %)
                  jio/reader
                  fs/file))
       (reader/merge-deps)))

(defn combine-deps-and-config
  "Given a configuration for `config-files` and optional `config-data`,
  read and merge into a combined deps map."
  [{:keys [config-files config-data] :as opts}]
  (let [deps-map (read-deps config-files)]
    (if config-data
      (reader/merge-deps [deps-map config-data])
      deps-map)))

(defn create-classpath
  "Given parsed-opts describing the input config files, and aliases to use,
  return the output lib map and classpath."
  [deps-map {:keys [resolve-aliases makecp-aliases aliases] :as opts}]
  (let [resolve-args (deps/combine-aliases deps-map (concat aliases resolve-aliases))
        cp-args (deps/combine-aliases deps-map (concat aliases makecp-aliases))
        libs (deps/resolve-deps deps-map resolve-args)
        cp (deps/make-classpath libs (:paths deps-map) cp-args)]
    {:lib-map libs
     :classpath cp}))

;;;; Handlers

(defn handle-classpath
  {:kat/request-name "classpath"
   :kat/doc "Compute a classpath via deps.edn"}
  [handler]
  (fn [config stack request]
    (case (first request)
      "meta"
      (update (handler config stack request)
              :body conj (meta #'handle-classpath))

      "classpath"
      (-> {:msg (:classpath (fs/with-cwd (:cwd config)
                              (-> (combine-deps-and-config
                                   {:config-files [(fs/file (:repo-root config)
                                                            (:deps-defaults-file config))
                                                   "deps.edn"]
                                    :config-data (edn/read-string (:deps-defaults-data config))})
                                  (create-classpath {}))))}
          resp/response
          (resp/status 200))

      (handler config stack request))))
