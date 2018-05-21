(ns katamari.core
  "FIXME"
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s])
  (:import [java.nio.file Paths]))

(defn rank-sort-graph
  "Given a dependency graph - a map from node IDs to sequences of
  dependence - produce a dependency rank ordering.

  Returns a sequence of sequences of node IDs, where each sequence of
  IDs is a \"phase\" of equal rank - that is all the dependencies of
  the rank are simultaneously satisfied and any operations of that
  rank could occur in parallel because their data dependencies are
  satisfied.

  Consider the trivial graph `b => a`, the rank ordering of this graph
  would be `[[:a] [:b]]` as b depends on a and thus cannot occur at
  the same time as or before a.

  ```clj
  (rank-sort-graph
    {:a []
     :b [:a]})
  ;; => [#{:a} #{:b}]
  ```

  The graph `b => a, c => a` is slightly more interesting in that both
  b and c depend on a, and so are of equal rank.

  ```clj
  (rank-sort-graph
    {:a []
     :b [:a]
     :c [:a]})
  ;; => [#{:a} #{:b :c}]
  ```
  
  This function only produces such a total rank ordering on the graph
  - no support is provided for graph shrinking.

  Throws if a depended node doesn't exist in the graph.

  Throws if there's an unresolvable cyclic dependency in the graph."
  [graph]
  ;; Check that all depended edges exist
  (doseq [dep (set (apply concat (vals graph)))
          :when (not (contains? graph dep))]
    (throw (IllegalArgumentException.
            (format "Found missing dependency %s!" dep))))

  ;; Try to compute a ranking
  (loop [ranks []
         graph graph
         satisfied #{}]
    (if (empty? graph)
      ;; We've run out of graph to resolve!
      ranks

      ;; Common case - there's work left to do
      (let [newly-satisfied (keep (fn [[target deps]]
                                    (when (every? satisfied deps)
                                      target))
                                  graph)]
        (if-not (empty? newly-satisfied)
          ;; Progress was made
          (recur (conj ranks (set newly-satisfied))
                 (apply dissoc graph newly-satisfied)
                 (into satisfied newly-satisfied))

          ;; We aren't able to make any more progress due to a cyclic dependency
          (throw (IllegalArgumentException.
                  (format "Ranked %s, but unable to order cyclic dependency graph %s!"
                          (pr-str ranks) (pr-str graph)))))))))

(defn fix
  "Eagerly compute the fixed point of `f` over `x`.

  WARNING: `f` MUST converge over `x` - no check or bound is provided
  to ensure that this occurs."
  [f x]
  (loop [x x
         x* (f x)]
    (if (not= x x*)
      (recur x* (f x*))
      x)))

(defn minimize-graph
  "Given a dependency graph - a map from node IDs to sequences of
  dependencies - and a sequence of nodes to select, produce a
  minimized graph containing only the selected nodes and their
  dependencies.

  Consider the graph `b => a, c => a`. With respect to b, we can
  minimize this graph to `b => a` because c is irrelevant.

  This is useful when computing minimized build graphs from a tree of
  many targets and dependencies of which only some small part may be
  selected or activated at any point in time.

  ```clj
  (minimize-graph {:a [] :b [:a] :c [:a]} [:a])
  ;; => {:a []}

  (minimize-graph {:a [] :b [:a] :c [:a]} [:b])
  ;; => {:a [] :b [:a]}

  (minimize-graph {:a [] :b [:a] :c [:a]} [:b :c])
  ;; => {:a [] :b [:a] :c [:a]}
  ```"
  [graph nodes]
  (let [keys (fix (fn [keyseq]
                    (set (mapcat #(cons % (get graph % [])) keyseq)))
                  (set nodes))]
    (select-keys graph keys)))

;; FIXME (arrdem 2018-05-20):
;;   Is this even useful? It's correct, but changed? status is hard to get in general
(defn rebuild-ordering
  "Given a dependency graph - a map from node IDs to sequences of
  dependencies - a predicate of a node ID indicating whether the node
  has \"changed\" and a sequence of selected node IDs, produce the
  rank sort of the minimal subgraph such that the only occuring nodes
  have \"changed\" or depend on \"changed\" nodes.

  This indicates the subset of the dependency graph which must be
  rebuilt due to direct or transitive change.

  ```clj
  (rebuild-ordering {:a [], :b [:a], :c [:b]} #{:c} [:c])
  ;; => [#{:c}]

  (rebuild-ordering {:a [], :b [:a], :c [:b]} #{:b} [:c])
  ;; => [#{:b} #{:c}]

  (rebuild-ordering {:a [], :b [:a], :c [:b]} #{:a} [:c])
  ;; => [#{:a} #{:b} #{:c}]

  (rebuild-ordering {:a [], :b [:a], :c [:b] :d [:b]} #{:a} [:c :d])
  ;; => [#{:a} #{:b} #{:c :d}]
  ```"
  [graph changed? nodes]
  (let [graph (minimize-graph graph nodes) ; We only care about the depended subgraph
        ordering (rank-sort-graph graph)];
    (second
     (reduce
      (fn [[dirty? acc :as state] nodes]
        (let [dirty* (filter
                      (fn [node]
                        (or (changed? node)
                            (some dirty? (get graph node []))))
                      nodes)]
          (if-not (empty? dirty*)
            [(into dirty? dirty*) (conj acc (set dirty*))]
            state)))
      [#{} []] ordering))))

;; Okay. Now we've got the machinery to - given the information that something has changed - figure
;; out what needs to be re-built. Now the problem of change detection confronts us. A target
;; is "dirty" if any of its inputs is dirty, but what constitutes an input and how/when do those get
;; checked?
;;
;; In Make and such, tasks are functions of a fileset to a fileset - the produced fileset just
;; happens to be a singleton conflated with the identifier for the rule/target.
;;
;; In Pants / Bazel, products are actually potentially composite entities such as classpaths or
;; filesets.

(defn ->fileset
  "Constructor for a fileset product.

  Returns a product representing a group of files."
  [& path-or-paths]
  {:type :product/fileset
   :paths (fix #(set
                 (mapcat (fn [path-or-paths]
                           (if (sequential? path-or-paths)
                             path-or-paths
                             (if (string? path-or-paths)
                               [(.toAbsolutePath
                                 (Paths/get ^String path-or-paths (into-array String [])))]
                               [path-or-paths])))
                         %))
               path-or-paths)})

(defn ->classpath
  "Constructor for a classpath product.

  Returns a product representing the given classpath elements.

  Classpath order is NOT maintained."
  [& path-or-paths]
  {:type :product/classpath
   :paths (fix (fn [paths]
                 (set
                  (mapcat
                   (fn [path-or-paths]
                     (if (sequential? path-or-paths)
                       path-or-paths
                       (if (string? path-or-paths)
                         (map #(.toAbsolutePath (Paths/get ^String % (into-array String [])))
                              (str/split path-or-paths #":"))
                         [path-or-paths])))
                   paths)))
               path-or-paths)})

;; Products are produced from zero or more other products via steps.
;;
;; Steps are delayed, packaged computations carrying metadata about their dependencies. They are
;; represented simply as functions with non-nil metadata.
;;
;; Realizing a step to a product is achieved by simply applying the function encoding the step to
;; appropriate arguments - products already known to the build system.



;; 
