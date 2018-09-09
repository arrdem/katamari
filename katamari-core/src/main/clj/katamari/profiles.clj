(ns katamari.profiles
  ""
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require clojure.pprint
            [detritus.update :refer [fix]]))

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
  (->> (for [[id definition] target-map]
         [id (update definition
                     :options (fn [m]
                                (->> (concat (map #(get m % {})
                                                  (keep keyword? active-profiles))
                                             (keep map? active-profiles))
                                     (apply merge-with merge*))))])
       (into {})))

(defn apply-build-profiles
  "Given a build and a (possibly empty) sequence of profiles, apply all selected profiles returning a
  simplified build.

  This may not be appropriate for a final implementation of a profile system because its merge is
  naive and set theoretic, lein style sequences of named and raw profiles are not supported."
  [build active-profiles]
  (let [active-profiles (or (seq active-profiles) #{:katamari/default})
        ;; This is a subset of lein's profilels / project map merging behavior
        ;;
        ;; Particularly, ^:replace and other metadata isn't respected.
        ;;
        ;; Activating anonymous profiles is now barely supported.
        active-profiles (fix (fn [profiles]
                               (into #{}
                                     (mapcat (fn [profile]
                                               {:post [(do (clojure.pprint/pprint %) true)
                                                       (sequential? %)
                                                       (every? (some-fn keyword? map?) %)]}
                                               (if (keyword? profile)
                                                 (let [to-activate (get (:profiles build) profile [])]
                                                   (if (and (sequential? to-activate)
                                                            (every? (some-fn map? keyword?) to-activate))
                                                     (cons profile (seq to-activate))
                                                     (list profile)))
                                                 (list profile)))
                                             profiles)))
                             active-profiles)]
    (clojure.pprint/pprint
     active-profiles)
    (update (assoc build :active-profiles active-profiles)
            :targets activate-profiles* active-profiles)))
