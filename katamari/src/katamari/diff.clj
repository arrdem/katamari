(ns katamari.diff
  "Change tracking.

  May or may not be a good idea. Inspired by the general impossibility
  of recovering correct, diff information without integration into the
  change machinery. clojure.data/diff and other equivalent machinery
  ultimately has to try and reconstruct the edit sequence, which is
  what makes correct minimal diffing hard.

  Spy on the editing be it in the data structures or your editor
  entirely obviates this problem."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]})

(definterface IDiffTracking
  (emptyDiff [])
  (getDiff []))

(defn diff [val]
  (if (instance? IDiffTracking val)
    (.getDiff ^IDiffTracking val)
    nil))

(defn without-diff [val]
  (if (instance? IDiffTracking val)
    (.emptyDiff ^IDiffTracking val)
    val))

(def ^:private vconj (fnil conj []))

;; A type which tracks inserts/updates/deletes
;;
;; Ideally this would be baked into Clojure's collection libs as metadata, but
;; that tracing has nonzero cost and :shrug:
;;
;; This implementation strikes a compromise by recursively subsuming all the
;; diffs of updated child structures into the parent structure. This lets
;; inserting, deleting and clearing diff data remain constant cost.

;; FIXME (arrdem 2018-10-21):
;;   When getting values out, they should be shimmed to do diff tracking.

(deftype DiffingMap [contents diff]
  IDiffTracking
  (getDiff [this]
    diff)

  (emptyDiff [this]
    (DiffingMap. contents nil))

  clojure.lang.IPersistentMap
  (assoc [this k v]
    (DiffingMap.
     (.assoc contents k (without-diff v))
     (vconj diff
            (if (contains? contents k)
              [:change k (get contents k) v (diff v)]
              [:insert k nil v nil]))))

  (assocEx [_ k v]
    ;; DEAD CODE
    (throw (Exception.)))

  (without [_ k]
    (DiffingMap.
     (.without contents k)
     (if (contains? contents k)
       (vconj diff [:remove k (get contents k) nil nil])
       diff)))

  java.lang.Iterable
  (iterator [this]
    (.iterator contents))

  clojure.lang.Associative
  (containsKey [_ k]
    (.containsKey contents k))

  (entryAt [_ k]
    (.entryAt contents k))

  clojure.lang.IPersistentCollection
  (count [_]
    (.count contents))

  (cons [this o]
    (apply assoc this o))

  (empty [_]
    (DiffingMap. (.empty contents) nil))

  (equiv [_ o]
    (.equiv contents o))

  clojure.lang.Seqable
  (seq [_]
    (.seq contents))

  clojure.lang.ILookup
  (valAt [_ k]
    (.valAt contents k))

  (valAt [_ k not-found]
    (.valAt contents k not-found)))

;; FIXME (arrdem 2018-10-21):
;;   Implement an equivalent diff tracking vector.
;;   That covers most of my non-atomic use cases.
