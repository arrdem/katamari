(ns katamari.roll.extensions.jvm
  "A definition of `java-library`."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.java.shell :as sh]
            [clojure.spec.alpha :as s]

            ;; kat
            [katamari.roll.reader :refer [compute-buildgraph refresh-buildgraph-for-changes]]
            [katamari.roll.specs :as rs]
            [katamari.roll.extensions :as ext]
            [katamari.deps.extensions.roll :as der]

            ;; deps
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.reader :as reader]
            [clojure.tools.deps.alpha.util.io :as io :refer [printerrln]]
            [clojure.tools.deps.alpha.script.make-classpath :as mkcp]
            [clojure.tools.deps.alpha.script.parse :as deps-parser]

            ;; fs
            [me.raynes.fs :as fs])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

;;;; Interacting with deps

(defn buildgraph->default-deps [buildgraph]
  (->> buildgraph
       :targets
       (map (fn [[name coord]]
              [name (assoc (select-keys coord [:paths :deps])
                           :deps/manifest :roll
                           :roll/name (:name coord)
                           :roll/file (:rollfile coord))]))
       (into {})))

(defn make-classpath [config deps]
  (let [opts (-> []
                 (mkcp/parse-opts)
                 (update :config-files
                         (partial cons
                                  (fs/file (:repo-root config)
                                           (:deps-defaults-file config))))
                 (update :config-data
                         #(or % (deps-parser/parse-config
                                 (:deps-defaults-data config)))))
        deps (-> deps
                 ;; Inject the defaults "profile"
                 (assoc-in [:aliases ::defaults]

                           ;; FIXME (arrdem 2018-10-21):
                           ;;   Cache this
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
       opts))))

;;;; Java library

;; This needs to javac a bunch of stuff potentially

(s/def ::jvm-version
  (into #{}
        (mapcat (fn [x] [(str x) (str "1." x)]))
        (range 1 11)))

(s/def ::source-version
  ::jvm-version)

(s/def ::target-version
  ::jvm-version)

(ext/defmanifest java-library
  (s/keys* :opt-un [::rs/deps
                    ::rs/paths
                    ::source-version
                    ::target-version]))

#_(defmethod ext/rule-prep 'java-library [config buildgraph target rule]
    )

(defmethod ext/rule-inputs 'java-library [config buildgraph target rule]
  ;; Only expose deps on things in the build graph.
  ;; Everything else we can punt on. Or we will assume we can.
  {:targets (->> (:deps target)
                 (keys)
                 (keep (set (keys buildgraph))))})

(defmethod ext/rule-build 'java-library [config buildgraph target
                                         {:keys [paths
                                                 deps
                                                 source-version
                                                 target-version]
                                          :as rule}
                                         inputs]
  (let [source-files (->> (map (partial fs/file (:repo-root config)) paths)
                          (mapcat file-seq)
                          (filter #(.isFile %))
                          (map #(.getCanonicalPath %)))
        dest-dir (->> (into-array FileAttribute [])
                      (Files/createTempDirectory "javac")
                      (.toFile)
                      (.getCanonicalPath))]

    ;; FIXME (arrdem 2018-10-21):
    ;;   Capture the exit results nicely
    (when source-files
      (let [deps (into deps
                       (map (fn [{:keys [from] :as product}]
                              [from product]))
                       (:targets inputs))
            cp (make-classpath config {:deps deps})
            cmd (cond-> ["javac"]
                  (:classpath cp) (into ["-cp" (:classpath cp)])
                  source-version (into ["-source" source-version])
                  target-version (into ["-target" target-version])
                  true (-> (into ["-d" dest-dir])
                           (into source-files)))]
        (apply sh/sh cmd)))

    {:type ::product
     :from target
     :mvn/manifest :roll
     :deps (:deps rule {})
     :paths [dest-dir]}))
