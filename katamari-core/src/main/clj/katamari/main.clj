(ns katamari.main
  "Katamari's entry point."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as spec]
            [clojure.tools.deps.alpha :as deps]
            [hasch.core :refer [uuid]])
  (:gen-class))

(defn load-steps
  [])

(defn load-targets
  [])

(defn load-tasks
  [])

(defn merge* [a b]
  (cond (or (and (vector? a) (or (vector? b) (not b)))
            (and (vector? b) (or (vector a) (not a))))
        (vec (into (into (sorted-set) b) a))

        (or (and (map? a) (or (map? b) (not b)))
            (and (or (map? a) (not a)) (map? b)))
        (merge-with merge* a b)

        (and a (not b)) a
        (not a) b))
        
(defn activate-profiles* [target-map active-profiles]
  (into {}
          (for [[id definition] target-map]
            [id (update definition
                      :options (fn [m]
                                 (apply merge-with merge*
                                             (map #(get m % {}) active-profiles))))])))

(defn fix
  "Iterate `f` over `x` until it converges - that is `(= (f x*) (f (f x*)))` and return the first `x*`."
  [f x]
  (let [x' (f x)]
    (if-not (= x x')
      (recur f x')
      x')))

(defn apply-profiles
  "Given a build and a (possibly empty) sequence of profiles, apply all selected profiles returning a
  simplified build."
  [build active-profiles]
  (let [active-profiles (or (seq active-profiles) #{:katamari/default})
        ;; This is a subset of lein's profilels / project map merging behavior
        active-profiles (fix (fn [profiles]
                               (into (sorted-set)
                                     (mapcat #(let [to-activate (get (:profiles build) % [])]
                                                (cons %
                                                      (when (every? keyword? to-activate)
                                                        to-activate)))
                                             profiles)))
                             active-profiles)]
    (update build :targets
            activate-profiles* active-profiles)))

(defn plan
  "Given a simplified build, construct an execution plan for it."
  [simplified-build target]
  (let [default-target (or target (:default-target simplified-build))]
    ))

(defn -main
  ""
  [& args])
