(ns katamari.roll.specs
  "Specs used by `katamari.roll.reader` to parse Rollfiles and define the API."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.deps.alpha.specs :as tds]
            [katamari.spec :refer [defkeys]]))

(s/def ::target
  qualified-symbol?)

(defkeys ::meta
  [doc :? string?
   flakey :? boolean?])

(s/def ::manifest
  simple-symbol?)

(defkeys ::rule*
  [manifest :- ::manifest
   target :- qualified-symbol?
   rollfile :- string?
   meta :? ::meta])

(defmulti ^:private parse-manifest
  "this will be exposed in the extensions namespace."
  first)

(s/def ::manifest-expr
  (s/multi-spec parse-manifest first))

;; conforms to a ::rule*
(s/def ::deftarget-expr
  (s/and
   (s/cat :sym #{'deftarget}
          :target ::target
          :docstring (s/? string?)
          :metadata (s/? map?)
          :manifest ::manifest-expr)
   (s/conformer
    (fn [v]
      (if (= ::s/invalid v) v
          (merge (dissoc v :manifest :sym) (:manifest v)))))))

(s/def ::rule
  (s/and (s/or :unparsed ::deftarget-expr
               :parsed   ::rule*)
         (s/conformer
          (fn [v] (if (not= v ::s/invalid) (second v) v))
          (fn [v] [:parsed v]))))

;; Targets may have paths - a list of paths to be processed
(s/def ::paths
  (s/coll-of string? :type vector?))

;; Targets may name other artifacts on which they depend
(s/def ::deps
  #(s/valid? ::tds/deps %))

;;;; API specs

;;; The roll diff record.

(defkeys ::roll-diff
  [mtime :- pos-int?
   sha256sum :- string?
   targets :- (s/coll-of qualified-symbol?)])

;;; The build graph itself

;; FIXME (arrdem 2018-10-21):
;;   Is this the "targets" map because it's targets -> rules or
;;   is this the "rules" map because it defines rules for targets or ...
(s/def ::targets
  (s/map-of ::target ::rule))

(defkeys ::buildgraph
  [targets :- ::targets
   rollfiles :-  (s/map-of string? ::roll-diff)])

;;; build products

;; FIXME (arrdem 2018-10-21):
;;
;;   Rule implementations implicitly define products for which there should be a
;;   spec. Specifically some keys like the `:manifest` and `:target` need to be
;;   preserved from the rule to the product(s).
(s/def ::product
  any?)
