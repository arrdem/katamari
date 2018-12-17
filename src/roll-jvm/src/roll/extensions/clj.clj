(ns roll.extensions.clj
  "A definition of `clojure-library`."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.string :as str]
            [clojure.java.shell :as sh]
            [clojure.spec.alpha :as s]
            [roll.specs :as rs]
            [roll.extensions :as ext]
            [roll.extensions.jvm :as rejvm]
            [me.raynes.fs :as fs]))

;;;; Clojure library

;; It doesn't really get simpler than this.
;;
;; Unless there's AOT, which this target doesn't support yet.

(ext/defmanifest clojure-library
  (s/keys* :opt-un [::rs/deps
                    ::rs/paths]))

(defmethod ext/manifest-prep 'clojure-library [config buildgraph _manifest]
  (rejvm/init-deps config buildgraph))

(defmethod ext/rule-prep 'clojure-library [config buildgraph target rule]
  (rejvm/canonicalize-deps config buildgraph target rule))

(defmethod ext/rule-inputs 'clojure-library
  [config {:keys [targets] :as buildgraph} target rule]

  ;; We only need buildgraph internal targets to be built for us.
  {:targets (->> (:deps rule)
                 keys
                 (filter #(contains? targets %)))})

(defmethod ext/rule-build 'clojure-library
[config buildgraph target {:keys [paths aot deps] :as rule} products inputs]
(merge
 {:from target
  :mvn/manifest :roll}
 (select-keys rule [:paths :deps])))

;;;; Clojure binary

;; Clojure's AOT is transitive - it sucks in and AOTs dependencies - so it isn't
;; something that's really applicable to library development or deployment. It's
;; really only useful when building a final "closed world" application, and even
;; then has meaningful limits.
;;
;; Consequently, AOT isn't a feature of clojure-library, it's a separate thing
;; so that the pure clojure-library can stand apart from the extreme complexity
;; and strange hacks which surround Clojure AOT "for real".

(s/def ::aot
(s/or :all #{:all}
      :nss (s/coll-of
            (s/or :sym simple-symbol?
                  :re  #(instance? java.util.regex.Pattern %)))))

(ext/defmanifest clojure-binary
  (s/keys :opt-un [::rs/deps
                   ::rs/paths
                   ::aot]))

(defmethod ext/manifest-prep 'clojure-binary [config buildgraph _manifest]
  (rejvm/init-deps config buildgraph))

(defmethod ext/rule-prep 'clojure-binary [config buildgraph target rule]
  (rejvm/canonicalize-deps config buildgraph target rule))

(defmethod ext/rule-inputs 'clojure-binary
  [config {:keys [targets] :as buildgraph} target rule]
  
  ;; We only need buildgraph internal targets to be built for us.
  {:targets (->> (:deps rule)
                 keys
                 (filter #(contains? targets %)))})

(defmethod ext/rule-build 'clojure-binary
  [config buildgraph target {:keys [paths aot deps] :as rule} products inputs]
  (when aot
    (let [{:keys [classpath]}
          (rejvm/make-classpath config products
                                {:deps deps, :paths paths})

          source-files (->> paths
                            (map fs/file)
                            (mapcat (fn [file]
                                      (map #(vector file %)
                                           (remove #(.isDirectory ^java.io.File %)
                                                   (file-seq file)))))
                            (map (fn [[root file]]
                                   (.toString (.relativize (.toPath root) (.toPath file))))))

          arm (first aot)
          aot (second aot)

          libs (->> (case arm
                      :all source-files

                      :nss (into #{}
                                 (concat (filter symbol? aot)
                                         (mapcat (fn [rule]
                                                   (->> source-files
                                                        (filter #(re-find rule %))))
                                                 aot))))
                    (map #(-> %
                              (str/replace #"\.clj[sxc]?$" "")
                              (str/replace #"_" "-")
                              (str/replace #"/" ".")
                              symbol)))

          incantation
          (->> libs
               (map (fn [lib]
                      `(try (println "Compiling" '~lib)
                            (binding [*compile-path* ~(.getAbsolutePath fs/*cwd*)]
                              (compile '~lib))
                            (catch Exception e#
                              (println e#)
                              (System/exit 1)))))
               (map pr-str)
               (str/join "\n"))

          cmd ["java" "-cp" (str classpath ":" (.getAbsolutePath fs/*cwd*))
               "clojure.main"
               :dir fs/*cwd*
               :in (java.io.StringReader. incantation)]

          res
          (apply sh/sh cmd)]

      (when-not (zero? (:exit res))
        (let [e (ex-info "Failed to clj AOT"
                         (assoc res
                                :target target
                                :rule rule
                                :incantation incantation
                                :command cmd))]
          (reset! +e+ e)
          (throw e)))))

  ;; FIXME (reid.mckenzie 2018-11-12):
  ;;
  ;;   Leiningen has some machinery designed to blacklist and whitelist
  ;;   classes. Is that something I have to replicate? Here the child JVM runs
  ;;   with no injected deps whatsoever.

  {:from target
   :mvn/manifest :roll
   :deps deps
   :paths (conj paths (.getAbsolutePath fs/*cwd*))})
