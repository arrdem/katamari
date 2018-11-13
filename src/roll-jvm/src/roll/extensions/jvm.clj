(ns roll.extensions.jvm
  "A definition of `java-library`."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.java.shell :as sh]
            [clojure.spec.alpha :as s]

            ;; kat
            [roll.specs :as rs]
            [roll.extensions :as ext]

            ;; deps
            [clojure.tools.deps.alpha.reader :as reader]
            [clojure.tools.deps.alpha.extensions :as deps.ext]
            [clojure.tools.deps.alpha.script.make-classpath :as mkcp]
            [clojure.tools.deps.alpha.script.parse :as deps-parser]

            ;; fs
            [me.raynes.fs :as fs]))

;;;; Interacting with deps

;;; Injecting roll based deps into the deps map.

(defmethod deps.ext/dep-id :roll
  [lib coord config]
  ;; FIXME (reid.mckenzie 2018-11-02):
  ;;   er.
  nil)

(defmethod deps.ext/manifest-type :roll
  [lib coord config]
  {:deps/manifest :roll})

(defmethod deps.ext/coord-deps :roll
  [lib {:keys [deps] :as coord} manifest config]
  deps)

(defmethod deps.ext/coord-paths :roll
  [lib {:keys [paths] :as coord} _manifest  _config]
  paths)

;;; Building a deps map for the roll graph

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

(defn make-classpath [{init-deps ::deps
                       :as config}
                      products
                      deps]
  {:pre [(contains? deps :deps)]}
  (let [deps (-> (merge-deps init-deps deps)
                 ;; Inject the targets "profile"
                 (assoc-in [:aliases ::roll :override-deps]
                           (products->default-deps products)))
        opts {:aliases [::roll]}]

    (mkcp/create-classpath
     deps
     ;; Bolt on our two magical internal profiles
     opts)))

(defn init-deps
  [{:keys [repo-root
           deps-defaults-file
           deps-defaults-data
           deps-resolve-file
           ::deps]
    :as config}
   buildgraph]
  (if-not deps
    (let [deps (cond-> {}
                 (not-empty deps-resolve-file)
                 (assoc-in [:aliases ::roll]
                           ;; FIXME (arrdem 2018-10-21):
                           ;;   Cache this
                           (deps-parser/parse-config
                            (slurp
                             (fs/file repo-root deps-resolve-file))))

                 ;; defaults file
                 (not-empty deps-defaults-file)
                 (merge-deps (reader/read-deps
                              [(fs/file repo-root deps-defaults-file)]))

                 ;; defaults data
                 (not-empty deps-defaults-data)
                 (merge-deps (deps-parser/parse-config deps-defaults-data)))]
      [(assoc config ::deps deps) buildgraph])
    [config buildgraph]))

(defn- use-dep [default-deps override-deps [lib coord]]
  (vector lib
          (or (get override-deps lib)
              (if-let [n (namespace lib)]
                (get override-deps (symbol n)))
              coord
              (get default-deps lib)
              (if-let [n (namespace lib)]
                (get default-deps (symbol n))))))

(defn canonicalize-deps [{:keys [::deps] :as config} buildgraph target rule]
  [config
   (-> buildgraph
       (update-in [:targets target :deps]
                  (fn [target-deps]
                    (when target-deps
                      (let [{:keys [default-deps
                                    override-deps]}
                            (-> deps :aliases ::roll)

                            target-deps*
                            (into {}
                                  (map (partial use-dep default-deps override-deps))
                                  target-deps)]
                        (if (not= target-deps* target-deps)
                          target-deps* target-deps))))))])

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

(defmethod ext/manifest-prep 'java-library [config buildgraph _manifest]
  (init-deps config buildgraph))

(defmethod ext/rule-prep 'java-library [config buildgraph target rule]
  (canonicalize-deps config buildgraph target rule))

(defmethod ext/rule-inputs 'java-library
  [config {:keys [targets] :as buildgraph} target rule]

  ;; Only expose deps on things in the build graph.
  ;; Everything else we can punt on. Or we will assume we can.
  {:targets (->> (:deps rule)
                 keys
                 (filter #(contains? targets %)))})

(defmethod ext/rule-build 'java-library
  [config buildgraph target
   {:keys [deps
           paths
           source-version
           target-version]
    :as rule}
   products
   inputs]

  (let [source-files (->> (map (partial fs/file) paths)
                          (mapcat file-seq)
                          (filter #(.isFile %))
                          (map #(.getCanonicalPath %)))
        dest-dir (.getCanonicalPath fs/*cwd*)]
    (when source-files
      (let [cp (make-classpath config products
                               {:deps (:deps target)
                                :paths paths})
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

    {:from target
     :mvn/manifest :roll
     :deps (:deps rule {})
     :paths [dest-dir]}))
