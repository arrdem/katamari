(ns roll.core
  "The API by which to execute rolling."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [maxwell.daemon :as diff]
            [roll.cache :as cache]
            [roll.specs :as rs]
            [roll.extensions :refer :all]))

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
                                       (vals (:targets buildgraph)))
         seen #{}]
    (if (not-empty wl)
      (let [seen*
            (conj seen manifest)

            [config* buildgraph*]
            (try
              (let [[c b] (manifest-prep config buildgraph manifest)]
                (when-not (instance? maxwell.daemon.DiffingMap b)
                  (throw (IllegalStateException.
                          "Manifest initialization discarded diff info!")))

                [c b])
              (catch Exception e
                (throw (ex-info "Exception in roll.prep.manifest"
                                {:manifest manifest}
                                e))))]
        (recur config*
               (diff/empty-diff buildgraph*)
               (->> (diff/get-diff buildgraph*)
                    (mapcat (fn [[op key oldval newval diff]]
                              (when (and (= key :targets)
                                         (#{:change :insert} op)
                                         (not= oldval newval))
                                diff)))
                    (keep (fn [[op key oldval newval diff]]
                            (when (and (#{:change :insert} op)
                                       (not= oldval newval))
                              (rule-manifest newval))))
                    (remove (into seen* wl*))
                    (into wl*))
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
         [[target rule] & wl* :as wl] (:targets buildgraph)]
    (if (not-empty wl)
      (let [[config* buildgraph*]
            (try
              (let [[c b] (rule-prep config buildgraph target rule)]
                (when-not (instance? maxwell.daemon.DiffingMap b)
                  (throw (IllegalStateException.
                          "Rule initialization discarded diff info!")))
                [c b])
              (catch Exception e
                (throw (ex-info "Exception in roll.prep.rule"
                                {:rule rule
                                 :manifest (rule-manifest rule)}
                                e))))]
        (recur config*
               (diff/empty-diff buildgraph*)
               (->> (diff/get-diff buildgraph*)
                    (mapcat (fn [[op key oldval newval diff]]
                              (when (and (= key :targets)
                                         (#{:change :insert} op)
                                         (not= oldval newval))
                                diff)))
                    (keep (fn [[op key oldval newval diff :as d]]
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
  [config {:keys [targets] :as buildgraph} & [targets?]]
  (let [->deps (memoize
                (fn [target]
                  (if-let [rule (get targets target)]
                    (->> rule
                         (rule-inputs config buildgraph target)
                         (mapcat val)
                         (set))
                    (throw (ex-info "No rule found for target"
                                    {:target target})))))]
    (loop [plan []
           planned #{}
           unplanned (if targets?
                       (fix (fn [target-ids]
                              (->> target-ids
                                   (mapcat (fn [target]
                                             (cons target (->deps target))))
                                   (into (sorted-set))))
                            (apply sorted-set targets?))
                       (set (keys targets)))]
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

(s/fdef plan
  :args (s/cat :conf any?
               :graph ::rs/buildgraph
               :target (s/? ::rs/target))
  :ret (s/tuple any? ::rs/buildgraph (s/coll-of (s/coll-of ::rs/target))))

(defn plan
  "Plan for a roll!

  Prep all the manifests, rules, and builds an ordering of the targets.

  Returns the (potentially updated!) config and build graph, along with the plan."
  [config buildgraph & [targets?]]
  (let [[config buildgraph] (prep config buildgraph)]
    [config buildgraph
     (try (order-buildgraph config buildgraph targets?)
          (catch Exception e
            (throw (ex-info "roll.plan" {} e))))]))

(s/fdef roll
  :args (s/cat :conf any?
               :cache any?
               :graph ::rs/buildgraph
               :targets (s/? (s/coll-of ::rs/target)))
  :ret (s/map-of ::rs/target any?))

(defn roll
  "Do a roll!

  Recursively builds all rules and their dependencies, or if `goal-target` is
  supplied only that target and its dependencies.

  Return a mapping from built targets to their products."
  [config cache buildgraph & [targets?]]
  (let [[config buildgraph plan] (plan config buildgraph targets?)]
    (reduce (fn [products target]
              (let [rule    (get-in buildgraph [:targets target])
                    inputs  (into {}
                                  (map (fn [[key targets]]
                                         [key (mapv #(get products %) targets)]))
                                  (rule-inputs config buildgraph target rule))
                    id (rule-id config buildgraph target rule products inputs)
                    _ (log/infof "Building %s@%s\n" target id)
                    product (try
                              (if-let [cached-product (cache/get-product cache id)]
                                (do (log/debugf "Hit the product cache!\n")
                                    cached-product)
                                ;; Fill the cache
                                (do (log/infof "Missed the cache, filling\n")
                                    (let [dir (cache/get-workdir cache id)
                                          _ (log/infof "Trying to build in workdir %s" dir)
                                          product (fs/with-cwd dir
                                                    (-> (rule-build config buildgraph
                                                                    target rule
                                                                    products inputs)
                                                        (assoc :id id)))]
                                      (cache/put-product cache id product)
                                      product)))
                              (catch Exception e
                                ;; Kill the cache entry
                                (cache/delete-product cache id)
                                (throw e)))]
                (assoc products
                       target product)))
            {} (apply concat plan))))
