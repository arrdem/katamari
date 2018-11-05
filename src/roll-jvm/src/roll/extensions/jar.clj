(ns roll.extensions.jar
  "A definition of `jar` and `uberjar` based on `depstar`."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [me.raynes.fs :as fs]
            [hf.depstar.uberjar :as ds]
            [roll.specs :as rs]
            [roll.extensions :as ext]
            [roll.extensions.jvm :as rejvm])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

;;;; Helpers

(defn build-jar! [jar-file paths]
  (let [tmp (Files/createTempDirectory "jar" (make-array FileAttribute 0))]
    (run! #(do (prn {:message "copying source" :source %})
               (ds/copy-source % tmp {}))
          paths)
    (ds/write-jar tmp (.toPath jar-file))))

;;;; Specs

(s/def ::entry-point
  string?)

(s/def ::manifest
  (s/map-of string? string?))

(s/def ::jar-name
  string?)

;;;; Mere jars

(ext/defmanifest jar
  (s/keys* :req-un [::rs/deps]
           :opt-un [::jar-name
                    ::entry-point
                    ::manifest]))

(defmethod ext/manifest-prep 'jar [config buildgraph _manifest]
  (rejvm/init-deps config buildgraph))

(defmethod ext/rule-prep 'jar [config buildgraph target rule]
  (rejvm/canonicalize-deps config buildgraph target rule))

(defmethod ext/rule-inputs 'jar
  [config {:keys [targets] :as buildgraph} target rule]

  ;; We only need buildgraph internal targets to be built for us.
  {:targets (->> (:deps rule)
                 keys
                 (filter #(contains? targets %)))})

(defmethod ext/rule-build 'jar
  [config buildgraph target rule products {:keys [targets] :as inputs}]

  (let [target-dir fs/*cwd*
        jar-name (:jar-name rule (str (name target) ".jar"))
        jar-file (fs/file target-dir jar-name)
        canonical-path (.getCanonicalPath jar-file)]

    (build-jar! jar-file (mapcat :paths targets))

    (merge
     {:type ::product
      :from target
      :mvn/manifest :roll
      :paths [canonical-path]}
     (select-keys rule [:deps]))))

;;;; Uberjars

(ext/defmanifest uberjar
  (s/keys* :req-un [::rs/deps]
           :opt-un [::jar-name
                    ::entry-point
                    ::manifest]))

(defmethod ext/manifest-prep 'uberjar [config buildgraph _manifest]
  (rejvm/init-deps config buildgraph))

(defmethod ext/rule-prep 'uberjar [config buildgraph target rule]
  (rejvm/canonicalize-deps config buildgraph target rule))

(defmethod ext/rule-inputs 'uberjar
  [config {:keys [targets] :as buildgraph} target rule]

  ;; We only need buildgraph internal targets to be built for us.
  {:targets (->> (:deps rule)
                 keys
                 (filter #(contains? targets %)))})

(defmethod ext/rule-build 'uberjar
  [config buildgraph target rule products inputs]

  (let [{:keys [classpath lib-map]}
        (rejvm/make-classpath config products
                              {:deps (:deps rule)})

        target-dir fs/*cwd*
        jar-name (:jar-name rule (str (name target) ".jar"))
        jar-file (fs/file target-dir jar-name)
        canonical-path (.getCanonicalPath jar-file)]

    (when (empty? classpath)
      (throw (IllegalStateException.)))

    (build-jar! jar-file (str/split classpath #":"))

    (merge
     {:type ::product
      :from target
      :mvn/manifest :roll
      :paths [canonical-path]}
     (select-keys rule [:deps]))))
