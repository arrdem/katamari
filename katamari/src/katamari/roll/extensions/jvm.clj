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

(defn canonicalize-deps [deps config]
  (let [{:keys [default-deps override-deps]} (get-in config [:deps :aliases ::roll])]
    (into {}
          (map (fn [[lib coord]]
                 ;; cribbed from clojure.tools.deps.alpa/use-dep, private
                 [lib
                  (or
                   ;; precise overrides win
                   (get override-deps lib)
                   ;; followed by group level overrides
                   (if-let [n (namespace lib)]
                     (get override-deps (symbol n)))
                   ;; any non-nil pinning should win over any defaults
                   coord
                   ;; precise defaults win
                   (get default-deps lib)
                   ;; group defaults are lowest priority
                   (if-let [n (namespace lib)]
                     (get default-deps (symbol n))))]))
          deps)))

(defn make-classpath [{:keys [deps]
                       :as config}
                      products
                      deps*]
  (mkcp/create-classpath
   ;; Inject the (dynamic!) overrides for product coords
   (-> (merge-deps deps deps*)
       (update-in [:aliases ::roll :override-deps]
                  merge (products->default-deps products)))
   ;; Bolt on our magical internal profiles
   {:aliases [::roll]}))

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

(defmethod ext/rule-prep 'java-library [config targets target rule]
  [config (update-in targets [target :deps] canonicalize-deps config)])

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

  (let [source-files (->> (map (partial fs/file) paths)
                          (mapcat file-seq)
                          (filter #(do (prn %)
                                       (.isFile %)))
                          (map #(.getCanonicalPath %)))
        dest-dir (.getCanonicalPath fs/*cwd*)]
    (when source-files
      (let [cp (make-classpath config products
                               {:deps (:deps target)})
            cmd (cond-> ["javac"]
                  (:classpath cp) (into ["-cp" (:classpath cp)])
                  source-version (into ["-source" source-version])
                  target-version (into ["-target" target-version])
                  true (-> (into ["-d" dest-dir])
                           (into source-files)
                           (into [:dir fs/*cwd*])))
            res (apply sh/sh cmd)]

        (when-not (zero? (:exit res))
          (throw (ex-info "Failed to javac"
                          (assoc res
                                 :target target
                                 :rule rule
                                 :command cmd))))))

    {:type ::product
     :from target
     :mvn/manifest :roll
     :deps (:deps rule {})
     :paths [dest-dir]}))
