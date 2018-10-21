(ns katamari.roll.extensions.jar
  "A definition of `jar`.

  Note that the definitions of `jar` and `uberjar` are independent."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [katamari.roll.extensions :refer [defmanifest]]
            [katamari.roll.specs :as rs]))

;;;; Jarfile

;; This is only kinda messy because there's a manifest involved.

(s/def ::target
  ::rs/resolved-target-identifier)

(s/def ::entry-point
  string?)

(s/def ::manifest
  (s/map-of string? string?))

(s/def ::jar-name
  string?)

(deftarget jarfile
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
