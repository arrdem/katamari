(ns katamari.roll.extensions.clj
  "A definition of `clojure-library`."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [katamari.roll.extensions :refer [defmanifest]]
            [katamari.roll.specs :as rs]))

;;;; Clojure library

;; It doesn't really get simpler than this (unless there's AOT)

(defmanifest clojure-library
  (s/keys* :opt-un [::rs/deps
                    ::rs/paths]))

;;;; Java library

;; This needs to javac a bunch of stuff potentially

(defmanifest java-library
  (s/keys* :opt-un [::rs/deps
                    ::rs/paths]))
