(ns katamari.roll.extensions.jvm
  "Definitions of some JVM related targets."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [katamari.roll.extensions :refer [deftarget]]
            [katamari.roll.specs :as rs]))

;;;; Clojure library

;; It doesn't really get simpler than this (unless there's AOT)

(deftarget clojure-library
  (s/keys* :opt-un [::rs/deps
                    ::rs/paths]))

;;;; Java library

;; This needs to javac a bunch of stuff potentially

(deftarget java-library
  (s/keys* :opt-un [::rs/deps
                    ::rs/paths]))

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
