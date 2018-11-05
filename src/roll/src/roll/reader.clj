(ns roll.reader
  "Tools for reading Rollfiles and providing partial parsing and
  refreshing of a project/repo's dependency graph."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [me.raynes.fs :as fs]
            [clojure.java.io :as jio]
            [clojure.data :refer [diff]]
            [roll.specs :as rs]
            [clojure.spec.alpha :as s]
            [clojure.tools.deps.alpha.reader :refer [canonicalize-all-syms]]
            [pandect.algo.sha256 :as hash])
  (:import [java.io File]))

;;;; I/O helpers

(defn find-rollfiles [root-file]
  (->> (file-seq root-file)
       (filter #(= "Rollfile" (.getName ^File %)))
       sort))

(defn read-all
  "Read all the available forms from the argument reader, returning a sequence of them."
  [rdr]
  (let [eof (Object.)]
    (take-while #(not= % eof)
                (repeatedly #(read rdr false eof)))))

(defn dir-canonicalize-paths
  "Helper for canonicalizing the `:paths` of a target."
  [^File rollfile]
  (fn [paths]
    (let [dir (.getParent rollfile)]
      (mapv (comp #(.getCanonicalPath %)
                  (partial fs/file dir))
            paths))))

;;;; The intentional API

(s/fdef read-rollfile
  :ret ::rs/targets)

(defn read-rollfile
  "Given a config and a file, attempt to parse the file and generate part of a
  buildgraph's `:targets` mapping corresponding to the targets in the file."
  [{:keys [repo-root] :as config} ^File rollfile]
  (->> (read-all ((comp #(java.io.PushbackReader. %) jio/reader) rollfile))
       (mapv (fn [read-data]
               (if-let [explain (s/explain-data ::rs/rule read-data)]
                 (throw (ex-info "Unable to parse rollfile!"
                                 (merge explain
                                        (meta read-data)
                                        {:file (.getCanonicalPath rollfile)
                                         :repo repo-root})))
                 (-> (s/conform ::rs/rule read-data)
                     (update :paths (dir-canonicalize-paths rollfile))
                     (update :deps canonicalize-all-syms)))))
       (map (juxt :target
                  #(assoc % :rollfile (.getCanonicalPath rollfile))))
       (into {})))

(defn error-on-conflicts
  "Helper used when merging partial target mappings to surface name conflicts."
  [{:keys [target] :as l l-file :rollfile}
   {r-file :rollfile :as r}]
  (if-not (= l r)
    (throw (IllegalStateException.
            (format "Found conflicting definitions of %s in files %s, %s"
                    target l-file r-file)))
    r))

(s/fdef targets-to-buildgraph
  :args (s/cat :targets ::rs/targets)
  :ret ::rs/buildgraph)

(defn targets-to-buildgraph
  "Given a target mapping, compute a buildgraph from them."
  [targets]
  {:targets targets
   :rollfiles (->> (vals targets)
                   (group-by :rollfile)
                   (map (fn [[f targets]]
                          [f {:mtime (.lastModified ^File (fs/file f))
                              :sha256sum (hash/sha256-file f)
                              :targets (mapv :target targets)}]))
                   (into {}))})

(s/fdef compute-buildgraph
  :ret ::rs/buildgraph)

(defn compute-buildgraph
  "Given a repository, (re)compute the entire build graph non-incrementally."
  [{:keys [repo-root] :as config}]
  (let [root-file (fs/file repo-root)]
    (->> (find-rollfiles root-file)
         (map (partial read-rollfile config))
         (apply merge-with error-on-conflicts)
         targets-to-buildgraph)))

(defn- refresh*
  "Implementation detail of `refresh-whole-buildgraph` and
  `refresh-buildgraph-for-target`.

  Used to factor out the common control flow of reloading only some set of
  entities, and report the diff thereof."
  [config
   {old-files :rollfiles
    old-targets :targets
    :as previous-graph}
   changed-rollfiles
   changed-paths]

  (let [changed-targets
        (mapcat #(get-in previous-graph [:rollfiles % :targets]) changed-paths)

        {new-targets :targets,
         new-files :rollfiles}
        (->> changed-rollfiles
             (map (partial read-rollfile config))
             (apply merge-with error-on-conflicts)
             targets-to-buildgraph)]
    {:targets
     (as-> (transient old-targets) %
       (reduce dissoc! % changed-targets)
       (reduce conj! % new-targets)
       (persistent! %))

     :rollfiles
     (as-> (transient old-files) %
       (reduce dissoc! % changed-paths)
       (reduce conj! % new-files)
       (persistent! %))

     ;; Overview of changes
     :diff
     (let [[added-targets deleted-targets updated-targets]
           (diff (set (keys new-targets)) (set changed-targets))

           [added-paths deleted-paths changed-paths]
           (diff (set (keys new-files)) (set changed-paths))]
       {:added-targets added-targets
        :deleted-targets deleted-targets
        :changed-targets changed-targets

        :added-rollfiles added-paths
        :changed-rolfiles changed-paths
        :deleted-rollfiles deleted-paths})}))

(s/fdef refresh-buildgraph-for-changes
  :ret ::rs/buildgraph)

(defn refresh-buildgraph-for-changes
  "Given a repository and a previous build graph, refresh any targets
  whose definitions could have changed as observed via mtime or
  content hash."
  [{:keys [repo-root] :as config}
   {old-files :rollfiles
    old-targets :targets
    :as previous-graph}]
  (let [[changed-rollfiles changed-paths]
        (->> (find-rollfiles (fs/file repo-root))
             (keep (fn [^File rollfile]
                     (let [path (.getCanonicalPath rollfile)]
                       (if-let [old-meta (get old-files path)]
                         (let [{old-mtime :mtime
                                old-shasum :sha256sum} old-meta]
                           (when (or (not= old-mtime (.lastModified rollfile))
                                     (not= old-shasum (hash/sha256-file rollfile)))
                             [rollfile path]))
                         [rollfile path]))))
             ((juxt (partial map first) (partial map second))))]
    (refresh* config
              previous-graph
              changed-rollfiles
              changed-paths)))

(s/fdef refresh-buildgraph-for-targets
  :ret ::rs/buildgraph)

(defn refresh-buildgraph-for-targets
  "Given a repository, a previous build graph, and a list of targets in
  that graph, refresh only the parts of the graph required to rebuild
  the selected targets."
  [config
   {old-files :rollfiles
    old-targets :targets
    :as previous-graph}
   refresh-targets]
  (let [refresh-paths (->> refresh-targets
                           (map #(get-in previous-graph [:targets % :rollfile]))
                           (into #{}))]
    (refresh* config
              previous-graph
              (map fs/file refresh-paths)
              refresh-paths)))
