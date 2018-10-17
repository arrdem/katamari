(ns katamari.server.extensions.roll-handlers
  "Katamari tasks for working with the build graph."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
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

(defn buildgraph->default-deps [buildgraph]
  (->> buildgraph
       :targets
       (map (fn [[name coord]]
              [name (assoc (select-keys coord [:paths :deps])
                           :deps/manifest :roll
                           :roll/name (:name coord)
                           :roll/file (:rollfile coord))]))
       (into {})))

(defhandler classpath
  "Usage:
  ./kat classpath [deps-options] -- [target ...]

Compute a classpath and libs mapping for selected target(s)"
  [handler config stack request]
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
                                (:buildgraph config))))
            opts (update opts :aliases (partial concat [::defaults ::roll]))]
        (der/with-graph (:buildgraph config)
          (mkcp/create-classpath
           deps
           ;; Bolt on our two magical internal profiles
           opts)))
      (assoc :intent :json)
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
  "Produce an uberjar, according to the target's config"

  [handler config stack request]
  (if-let [target (second request)]
    (if-let [target-coord (get-in config [:buildgraph :targets (symbol target)])]
      (let [classpath (-> (stack config stack (list "classpath" "--" target))
                          :body :classpath)
            target-dir (fs/file (:repo-root config)
                                (:target-dir config))
            jar-name (:jar-name target-coord (str (name (:name target-coord)) ".jar"))
            jar-file (fs/file target-dir jar-name)
            canonical-path (.getCanonicalPath jar-file)
            jar-path (.toPath jar-file)
            msgs (with-out-str
                   (binding [*err* *out*]
                     (let [tmp (Files/createTempDirectory "uberjar" (make-array FileAttribute 0))]
                       (run! #(ds/copy-source % tmp {}) (str/split classpath #":"))
                       (ds/write-jar tmp jar-path))
                     (println "Wrote jar" canonical-path)))]
        (-> {:intent :msg, :msg msgs, :jar canonical-path}
            resp/response
            (resp/status 200)))
      (-> "Could not produce an uberjar, no target coordinate was loaded!"
          resp/response
          (resp/status 400)))
    (-> "Could not produce an uberjar, no target provided!"
        resp/response
        (resp/status 400))))
