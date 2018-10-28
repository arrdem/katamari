(ns katamari.roll.extensions.jvm
  "A definition of `java-library`."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.java.shell :as sh]
            [clojure.pprint :refer [pprint]]
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

(defn products->default-deps [products]
  (->> products
       (map (fn [[name coord]]
              [name (assoc (select-keys coord [:paths :deps])
                           :deps/manifest :roll
                           :roll/name (:name coord)
                           :roll/file (:rollfile coord))]))
       (into {})))

(defn merge-deps [& deps]
  (reader/merge-deps deps))

(defn make-classpath [{:keys [repo-root
                              deps-defaults-file
                              deps-defaults-data
                              deps-resolve-file]
                       :as config}
                      products
                      deps]
  (let [deps (cond-> (-> deps
                         ;; Inject the defaults "profile"
                         (assoc-in [:aliases ::roll]
                                   ;; FIXME (arrdem 2018-10-21):
                                   ;;   Cache this
                                   (deps-parser/parse-config
                                    (slurp
                                     (fs/file repo-root deps-resolve-file))))
                         ;; Inject the targets "profile"
                         (assoc-in [:aliases ::roll :override-deps]
                                   (products->default-deps products)))

               ;; defaults file
               (not-empty deps-defaults-file)
               (merge-deps (reader/read-deps
                            [(fs/file repo-root deps-defaults-file)]))

               ;; defaults data
               (not-empty deps-defaults-data)
               (merge-deps (deps-parser/parse-config deps-defaults-data)))
        opts {:aliases [::roll]}]

    (mkcp/create-classpath
     deps
     ;; Bolt on our two magical internal profiles
     opts)))

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

(defmethod ext/rule-inputs 'java-library
  [config {:keys [targets] :as buildgraph} target rule]

  ;; Only expose deps on things in the build graph.
  ;; Everything else we can punt on. Or we will assume we can.
  {:targets (->> (:deps rule)
                 keys
                 (filter #(contains? targets %)))})

(defmethod ext/rule-build 'java-library
  [config buildgraph target
   {:keys [paths
           deps
           source-version
           target-version]
    :as rule}
   products
   inputs]

  (fs/with-cwd (:repo-root config)
    (let [source-files (->> (map (partial fs/file) paths)
                            (mapcat file-seq)
                            (filter #(.isFile %))
                            (map #(.getCanonicalPath %)))
          dest-dir fs/*cwd*]

      ;; FIXME (arrdem 2018-10-21):
      ;;   Capture the exit results! at all! nicely for extra credit.
      (when source-files
        (let [cp (make-classpath config products
                                 {:deps (:deps target)})
              cmd (cond-> ["javac"]
                    (:classpath cp) (into ["-cp" (:classpath cp)])
                    source-version (into ["-source" source-version])
                    target-version (into ["-target" target-version])
                    true (-> (into ["-d" dest-dir])
                             (into source-files)))]
          ;; FIXME (arrdem 2018-10-28):
          ;;   Capture failures!
          (apply sh/sh cmd)))

      {:type ::product
       :from target
       :mvn/manifest :roll
       :deps (:deps rule {})
       :paths [dest-dir]})))
