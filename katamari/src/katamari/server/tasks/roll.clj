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

            ;; Katamari
            [katamari.roll.reader :refer [compute-buildgraph refresh-buildgraph-for-changes]]
            [katamari.deps.extensions.roll :as der]

            ;; Ring
            [ring.util.response :as resp]

            [hf.depstar.uberjar :as ds])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

;;;; Handlers

(defonce +buildgraph-cache+
  (atom {}))

(defn wrap-buildgraph
  [handler]
  (fn [config stack request]
    (let [graph (get (swap! +buildgraph-cache+
                            update (:repo-root config)
                            #(or (and % (refresh-buildgraph-for-changes config %))
                                 (compute-buildgraph config)))
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

(defn handle-list-targets
  {:kat/request-name "list-targets"
   :kat/doc "Enumerate all the available Rollfile targets."}
  [handler]
  (fn [config stack request]
    (case (first request)
      "meta"
      (update (handler config stack request)
              :body conj (meta #'handle-list-targets))

      "list-targets"
      (-> (:buildgraph config)
          (select-keys [:targets])
          resp/response
          (resp/status 200))

      (handler config stack request))))

(defn handle-uberjar
  {:kat/request-name "uberjar"
   :kat/doc "Produce an uberjar, according to the target's config"}
  [handler]
  (fn [config stack request]
    (case (first request)
      "meta"
      (update (handler config stack request)
              :body conj (meta #'handle-uberjar))

      "uberjar"
      (if-let [target (second request)]
        (if-let [target-coord (get-in config [:buildgraph :targets (symbol target)])]
          (let [classpath (-> (stack config stack (list "classpath" "--" target))
                              :body)
                target-dir (fs/file (:repo-root config)
                                    (:target-dir config))
                jar-name (:jar-name target-coord (str (name (:name target-coord)) ".jar"))
                jar-file (fs/file target-dir jar-name)
                jar-path (.toPath jar-file)
                msgs (with-out-str
                       (binding [*err* *out*]
                         (let [tmp (Files/createTempDirectory "uberjar" (make-array FileAttribute 0))]
                           (run! #(ds/copy-source % tmp {}) (str/split classpath #":"))
                           (ds/write-jar tmp jar-path))))]
            (-> {:msg msgs
                 :jar-path (.getCanonicalPath jar-file)}
                resp/response
                (resp/status 200)))
          (-> "Could not produce an uberjar, no target coordinate was loaded!"
              resp/response
              (resp/status 400)))
        (-> "Could not produce an uberjar, no target provided!"
            resp/response
            (resp/status 400)))

      (handler config stack request))))
