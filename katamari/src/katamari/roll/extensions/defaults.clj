(ns katamari.roll.extensions.defaults
  "Sane-ish default implementations of the roll extensions API."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [katamari.roll.extensions :as ext]
            [me.raynes.fs :as fs]
            [hasch.core :as hasch]
            [pandect.algo.sha256 :refer [sha256-file]]))

(defmethod ext/manifest-prep :default [config buildgraph manifest]
  #_(printf "No configured prep.manifest for %s\n" manifest)
  [config buildgraph])

(defmethod ext/rule-prep :default [config buildgraph target rule]
  #_(printf "No configured prep.rule for manifest %s (target %s)\n"
            (rule-manifest rule) target)
  [config buildgraph])

(defmethod ext/rule-inputs :default [config buildgraph target rule]
  (throw (ex-info "No `rule-inputs` implementation for manifest!"
                  {:target target
                   :rule rule
                   :manifest (ext/rule-manifest rule)})))

(defmethod ext/rule-id :default [config buildgraph target rule products inputs]
  (str (hasch/uuid [target rule inputs
                    (into (sorted-set)
                          (comp (map fs/file)
                                (mapcat file-seq)
                                (remove #(.isDirectory ^java.io.File %))
                                (map sha256-file))
                          (:paths rule))])))
