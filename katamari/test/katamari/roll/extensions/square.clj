(ns katamari.roll.extensions.square
  "A test extension - to prove out the roll API.

  Rolling square wheels."
  {:authors ["Reid 'arrdem' Mckenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [katamari.roll.specs :as rs]
            [katamari.roll.extensions :as ext]))

(ext/defmanifest square
  (s/keys* :opt-un [::rs/paths
                    ::rs/deps]))

#_(defmethod ext/manifest-prep 'square [config buildgraph _manifest]
    )

(defmethod ext/rule-prep 'square [config buildgraph target rule]
  [config
   ;; Inject an implicit square/square so that we can test rule prep iteration
   (assoc buildgraph 'square/square
          {:target 'square/square
           :roll/manifest 'square})])

(defmethod ext/rule-inputs 'square [config buildgraph target rule]
  {:deps (cond-> (or (some-> rule :deps keys) [])
           (not= 'square/square target) (conj 'square/square))})

(defmethod ext/rule-build 'square [config buildgraph target rule inputs]
  {:type ::product
   :from target})
