(ns katamari.targets.extensions.jvm
  "Definitions of some JVM related targets."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [katamari.targets.extensions :refer [deftarget]]
            [katamari.roll.specs :as rs]))

(deftarget java-library
  (s/keys* :opt-un [::rs/deps
                    ::rs/paths]))

(deftarget clojure-library
  (s/keys* :opt-un [::rs/deps
                    ::rs/paths]))

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
