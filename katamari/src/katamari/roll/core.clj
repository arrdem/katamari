(ns katamari.roll.core
  "The API by which to execute rolling."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [katamari.roll.extensions :refer :all]))

(defn- prep-manifests
  "Execute any required manifest prep."
  [config buildgraph]
  (reduce
   (fn [[config buildgraph] manifest]
     (try
       (manifest-prep config buildgraph manifest)
       (catch Exception e
         (throw (ex-info "Exception in roll.prep.manifest"
                         {:manifest manifest}
                         e)))))
   [config buildgraph]
   (into #{}
         (map rule-manifest)
         (vals buildgraph))))

(defn- prep-rules
  "Execute any required rule prep."
  [config buildgraph]
  (reduce
   (fn [[config buildgraph] rule]
     (try
       (rule-prep config buildgraph rule)
       (catch Exception e
         (throw (ex-info "Exception in roll.prep.rule"
                         {:rule rule
                          :manifest (rule-manifest rule)})))))
   [config buildgraph]
   (vals buildgraph)))

(defn- prep
  "Execute any required prep plugins / tasks."
  [config buildgraph]
  (try
    (let [[config buildgraph] (roll-prep-manifests config buildgraph)
          ;; FIXME (arrdem 2018-10-20):
          ;;   Does this need to be in topsort order first, or do the rules get to init?
          [config buildgraph] (roll-prep-rules config buildgraph)]
      [config buildgraph])

    (catch Exception e
      (throw (ex-info "Exception in roll.prep"
                      {:config config
                       :buildgraph buildgraph})))))

(defn- fix [f x]
  (let [x* (f x)]
    (if (not= x x*) (recur f x*) x)))

(defn- order-buildgraph
  "Given a simplified build, construct a dependency order plan for it.

  The plan is a sequence of phases - groups of targets which could be compiled
  simultaneously."
  [config buildgraph & [goal-target?]]
  (let [->deps (memoize
                (fn [target]
                  (set (vals (rule-inputs config buildgraph target (get buildgraph target))))))]
    (loop [plan []
           planned #{}
           unplanned (if goal-target?
                       (fix (fn [target-ids]
                              (into (sorted-set)
                                    (mapcat (fn [target]
                                              (cons target (->deps target)))
                                            target-ids)))
                            (sorted-set goal-target?))
                       (set (keys buildgraph)))]
      (if (seq unplanned)
        (let [phase (keep #(when (every? (partial contains? planned)
                                         (->deps %))
                             %)
                          unplanned)]
          (if (not-empty phase)
            ;; If we've made headway, keep going
            (recur (conj plan (vec phase))
                   (into planned phase)
                   (reduce disj unplanned phase))
            ;; If we got stuck and can't make headway, blow up
            (throw (ex-info "got stuck on a circular dependency!"
                            {:plan plan
                             :planned planned
                             :unplanned unplanned}))))
        plan))))

(defn plan
  "Plan for a roll!

  Prep all the manifests, rules, and builds an ordering of the targets.

  Returns the (potentially updated!) config and build graph, along with the plan."
  [config buildgraph & [goal-target?]]
  (let [[config buildgraph] (prep config buildgraph)]
    [config buildgraph (order-buildgraph config buildgraph goal-target?)]))

(defn roll
  "Do a roll!

  Recursively builds all rules and their dependencies, or if `goal-target` is
  supplied only that target and its dependencies."
  [config buildgraph & [goal-target?]]
  (let [[config buildgraph plan] (plan config buildgraph goal-target?)]
    (reduce (fn [products target]
              (let [rule (get buildgraph target)
                    inputs (into {}
                                 (map (fn [[key target]]
                                        [key (get products target)]))
                                 (rule-inputs config buildgraph target rule))]
                (assoc products
                       target (rule-build config buildgraph target rule inputs))))
            ;; FIXME (arrdem 2018-10-20):
            ;;   Lol no product caching
            {}
            ;; FIXME (arrdem 2018-10-20):
            ;;   Lol sequential execution
            (apply concat plan))))
