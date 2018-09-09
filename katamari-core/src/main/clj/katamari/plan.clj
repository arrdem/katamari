(ns katamari.plan
  ""
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [detritus.update :refer [fix]]))

(defn order-build-products
  "Given a simplified build, construct a dependency order plan for it."
  [simplified-build & [goal-target?]]
  (let [targets (:targets simplified-build)]
    (loop [plan []
           planned #{}
           unplanned (if goal-target?
                       (fix (fn [target-ids]
                              (into (sorted-set)
                                    (mapcat (fn [target]
                                              (cons target (get-in targets [target :options :dependencies])))
                                            target-ids)))
                            (sorted-set goal-target?))
                       (set (keys targets)))]
      (if (seq unplanned)
        (let [phase (keep #(when (every? (partial contains? planned)
                                         (get-in targets [% :options :dependencies] []))
                             %)
                          unplanned)]
          (recur (conj plan (vec phase))
                 (into planned phase)
                 (reduce disj unplanned phase)))
        plan))))
