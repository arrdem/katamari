(ns katamari.roll.core
  "The API by which to execute rolling."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [katamari.diff :as diff]
            [katamari.roll.extensions :refer :all]))

(defn- prep-manifests
  "Execute any required manifest prep.

  Diffing is used to continue initializing manifests if more manifests
  are added to the build graph while prepping other manifests. This
  should be a pathological case, but it's supported."
  [config buildgraph]
  (loop [config config
         buildgraph (diff/->DiffingMap buildgraph nil)
         [manifest & wl* :as wl] (into #{}
                                       (map rule-manifest)
                                       (vals buildgraph))
         seen #{}]
    (if (not-empty wl)
      (let [seen*
            (conj seen manifest)

            [config* buildgraph*]
            (try
              (let [[c b] (manifest-prep config buildgraph manifest)]
                (when-not (instance? katamari.diff.DiffingMap b)
                  (throw (IllegalStateException.
                          "Manifest initialization discarded diff info!")))
                [c b])
              (catch Exception e
                (throw (ex-info "Exception in roll.prep.manifest"
                                {:manifest manifest}
                                e))))]
        (recur config*
               (diff/without-diff buildgraph*)
               (->> (diff/diff buildgraph*)
                    (keep (fn [[op _ oldval newval]]
                            (when (and (#{:change :insert} op)
                                       (not= oldval newval))
                              newval)))
                    (map rule-manifest)
                    (into wl*)
                    (remove seen*))
               seen*))
      [config buildgraph])))

(defn- prep-rules
  "Execute any required rule prep.

  As with `#'prep-manifests` this isn't completely trivial because
  each rule has the opportunity to inject more `[target, rule]` pairs
  which means that prep has to continue until a fixed point is
  reached."
  [config buildgraph]
  (loop [config config
         buildgraph (diff/->DiffingMap buildgraph nil)
         [[target rule] & wl* :as wl] buildgraph]
    (if (not-empty wl)
      (let [[config* buildgraph*]
            (try
              (let [[c b] (rule-prep config buildgraph target rule)]
                (when-not (instance? katamari.diff.DiffingMap b)
                  (throw (IllegalStateException.
                          "Rule initialization discarded diff info!")))
                [c b])
              (catch Exception e
                (throw (ex-info "Exception in roll.prep.rule"
                                {:rule rule
                                 :manifest (rule-manifest rule)}
                                e))))]
        (recur config*
               (diff/without-diff buildgraph*)
               (->> (diff/diff buildgraph*)
                    (keep (fn [[op key oldval newval]]
                            (when (and (#{:change :insert} op)
                                       (not= oldval newval))
                              [key newval])))
                    (into wl*))))
      [config buildgraph])))

(defn- prep
  "Execute any required prep plugins / tasks."
  [config buildgraph]
  (try
    (let [[config buildgraph] (prep-manifests config buildgraph)
          ;; FIXME (arrdem 2018-10-20):
          ;;   Does this need to be in topsort order first, or do the rules get to init?
          [config buildgraph] (prep-rules config buildgraph)]
      [config buildgraph])
    
    (catch Exception e
      (throw (ex-info "Exception in roll.prep"
                      {:config config
                       :buildgraph buildgraph}
                      e)))))

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
                  (->> (get buildgraph target)
                       (rule-inputs config buildgraph target)
                       (mapcat val)
                       (set))))]
    (loop [plan []
           planned #{}
           unplanned (if goal-target?
                       (fix (fn [target-ids]
                              (->> target-ids
                                   (mapcat (fn [target]
                                             (cons target (->deps target))))
                                   (into (sorted-set))))
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
    [config buildgraph
     (try (order-buildgraph config buildgraph goal-target?)
          (catch Exception e
            (throw (ex-info "roll.plan" {} e))))]))

(defn roll
  "Do a roll!

  Recursively builds all rules and their dependencies, or if `goal-target` is
  supplied only that target and its dependencies."
  [config buildgraph & [goal-target?]]
  (let [[config buildgraph plan] (plan config buildgraph goal-target?)]
    (reduce (fn [products target]
              (let [rule (get buildgraph target)
                    inputs (into {}
                                 (map (fn [[key targets]]
                                        [key (mapv #(get products %) targets)]))
                                 (rule-inputs config buildgraph target rule))]
                (assoc products
                       target (rule-build config buildgraph target rule inputs))))
            ;; FIXME (arrdem 2018-10-20):
            ;;   Lol no product caching
            {}
            ;; FIXME (arrdem 2018-10-20):
            ;;   Lol sequential execution
            (apply concat plan))))
