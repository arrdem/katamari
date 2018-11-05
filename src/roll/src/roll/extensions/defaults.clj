(ns roll.extensions.defaults
  "Sane-ish default implementations of the roll extensions API."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [roll.extensions :as ext]
            [me.raynes.fs :as fs]
            [hasch.core :as hasch]
            [pandect.algo.sha256 :refer [sha256-file]])
  (:import java.nio.file.Paths))

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

(defmethod ext/rule-id :default [{:keys [repo-root] :as config}
                                 buildgraph target rule products inputs]
  (let [root-path (.toPath (fs/file repo-root))]
    (-> [target rule inputs
         (into (sorted-set)
               (comp (map fs/file)
                     (mapcat (fn [file]
                               (map #(vector file %)
                                    (remove #(.isDirectory ^java.io.File %)
                                            (file-seq file)))))
                     (map (fn [[root file]]
                            [(.toString (.relativize (.toPath root) (.toPath file)))
                             (sha256-file file)])))
               (:paths rule))]
        (hasch/uuid)
        (str))))
