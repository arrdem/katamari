(ns roll.cache
  "Provides a pseudo-tempdir cache tree.

  Used to provide a somewhat content addressable store of build products by
  their build rule ID.

  "
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [me.raynes.fs :as fs]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]))

;; FIXME (arrdem 2018-10-28):
;; 
;;   Rethink this API so that buildcaches are hierarchical, and remote fetch is
;;   achievable. It should be possible to build a shared say team / org
;;   buildcache which nobody ever really misses in.

(defn ->buildcache
  "Given a root path, returns a `::buildcache` usable with this API."
  [root]
  (let [^java.io.File root (fs/file root)]
    (.mkdirs root)
    {:type ::buildcache
     :root root}))

(defn- key-prefix
  "Used to emulate Git's two level content tree addressing.

  This reduces strain on the host directory system, as keys are grouped
  by (small!) prefixes, reducing total directory size of the cache root and of
  the buckets below the root."
  [key]
  (.substring ^String key 0 2))

(defn ^java.io.File get-key*
  "Implementation detail.

  Used to return the root for a cache key."
  [{:keys [^java.io.File root]} key]
  (fs/file root (key-prefix key) key))

(defn get-product
  "Given a cache key, attempts to return the cache entry dir for that key.

  Returns `nil` if there is no such entry."
  [buildcache key]
  (let [product-root (get-key* buildcache key)]
    (when (and (.exists product-root) (.isDirectory product-root))
      ;; Mark the dir so we can track when it was last used.  This lets us use
      ;; dir modified timestamps to order build products in least frequently
      ;; used order.
      (.setLastModified product-root (System/currentTimeMillis))
      (edn/read (java.io.PushbackReader.
                 (java.io.BufferedReader.
                  (java.io.FileReader.
                   (fs/file product-root "product.edn"))))))))

(defn get-workdir
  "Given a cache key, creates and returns the product directory for the specified key."
  [buildcache key]
  (let [target-dir (fs/file (get-key* buildcache key) "target")]
    (.mkdirs target-dir)
    target-dir))

(defn put-product
  "Given a cache key and a product as a serializable datastructure, dumps that
  product into the given cache key.

  Future calls to `#'get-product` shall return an equivalent structure if at all
  possible."
  [buildcache key product]
  (let [product-root (get-key* buildcache key)]
    (binding [*out* (java.io.FileWriter. (fs/file product-root "product.edn"))]
      (prn product))))

;;; Cache enumeration

;; FIXME (arrdem 2018-10-28):
;;   There are DEFINITELY concurrent modification bugs here

(defn seq-products
  "Given a buildcache, returns a `[k, v]` seq of all keys in the cache and their
  roots."
  [{:keys [^java.io.File root] :as buildcache}]
  (for [^java.io.File child (.listFiles root)
        :when (.isDirectory child)
        ^java.io.File key (.listFiles child)
        :when (.isDirectory key)]
    [(.getName key) key]))

;;; Cache cleaning

(defn filter-cache-by-ttl
  "Given a TTL in milliseconds, select cache products which haven't been accessed
  in at least that long, sorting by least recently accessed.

  Returns a `[k, v]` seq of all keys in the cache, same as `#'seq-products`."
  [buildcache ttl]
  (let [now (System/currentTimeMillis)]
    (->> (seq-products buildcache)
         (filter (fn [[k ^java.io.File f]]
                   (< (.lastModified f) (- now ttl))))
         (sort-by (fn [[_ ^java.io.File %]] (.lastModified %))))))

(def +thirty-days-ms+ (* 1 1000 60 60 24 30))
(def +seven-days-ms+ (* 1 1000 60 60 24 30))

;; FIXME (arrdem 2018-10-28):
;;   There are DEFINITELY concurrent modification bugs to be stomped here.

(defn clean-products
  "Given a `[k, v]` seq of products say from `#'filter-cache-by-ttl`, recursively
  delete the selected cache keys and all files under them."
  [products]
  (doseq [[_ ^java.io.File f] products
          child (reverse (file-seq f))]
    (printf "Deleting file %s\n" child)
    (.delete child)))

(defn delete-product
  "Nuke a single product out of the cache.

  Used for cleaning up failed builds."
  [buildcache key]
  (let [^java.io.File root (get-key* buildcache key)]
    (when (and (.exists root)
               (.isDirectory root))
      (doseq [^java.io.File f (reverse (file-seq root))]
        (.delete f)))))
