(ns katamari.roll.extensions.uberjar
  "A definition of `jar` and `uberjar` based on `depstar`."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [katamari.roll.extensions :as ext]
            [katamari.roll.specs :as rs]))

;;;; (uber)jarfile

;; This is only kinda messy because there's a manifest involved.

(s/def ::target
  ::rs/resolved-target-identifier)

(s/def ::entry-point
  string?)

(s/def ::manifest
  (s/map-of string? string?))

(s/def ::jar-name
  string?)

(ext/defmanifest uberjar
  (s/keys* :req-un [::rs/deps]
           :opt-un [::jar-name
                    ::entry-point
                    ::manifest]))

;; The kernel uberjaring logic
#_(let [classpath (-> (stack config stack (list "classpath" "--" target)) :body :msg)
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
    {:intent :msg, :msg msgs, :jar canonical-path})
