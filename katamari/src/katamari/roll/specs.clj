(ns katamari.roll.specs
  "Specs used by `katamari.roll.reader` to parse Rollfiles."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.deps.alpha.specs :as tds]
            [katamari.targets.extensions :as ext]))

;; A name for a target which has not yet been disambiguated
(s/def ::unresolved-target-identifier
  symbol?)

;; A fully disambiguated name for a target
(s/def ::resolved-target-identifier
  qualified-symbol?)

(s/def ::def
  (s/and
   (s/cat :sym #{'deftarget}
          :name ::resolved-target-identifier
          :docstring (s/? string?)
          :metadata (s/? map?)
          :target ::target)
   (s/conformer
    (fn [v]
      (if (= ::s/invalid v) v
          (merge (dissoc v :target :sym) (:target v)))))))

;; Targets may have paths - a list of paths to be processed
(s/def ::paths
  (s/coll-of string? :type vector?))

;; Targets may name other artifacts on which they depend
(s/def ::deps
  #(s/valid? ::tds/deps %))

(s/def ::target
  (s/multi-spec ext/parse-target first))
