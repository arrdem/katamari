(ns katamari.roll.specs
  "Specs used by `katamari.roll.reader` to parse Rollfiles."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.deps.alpha.specs :as tds]
            [katamari.roll.extensions :as ext]))

;; A name for a target which has not yet been disambiguated
(s/def ::unresolved-target-identifier
  symbol?)

;; A fully disambiguated name for a target
(s/def ::resolved-target-identifier
  qualified-symbol?)

(s/def ::def
  (s/and
   (s/cat :sym #{'deftarget}
          :target ::resolved-target-identifier
          :docstring (s/? string?)
          :metadata (s/? map?)
          :rule ::rule)
   (s/conformer
    (fn [v]
      (if (= ::s/invalid v) v
          (merge (dissoc v :rule :sym) (:rule v)))))))

;; Targets may have paths - a list of paths to be processed
(s/def ::paths
  (s/coll-of string? :type vector?))

;; Targets may name other artifacts on which they depend
(s/def ::deps
  #(s/valid? ::tds/deps %))

(s/def ::rule
  (s/multi-spec ext/parse-manifest first))
