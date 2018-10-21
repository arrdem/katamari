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
;; Also determining when you're "done" diffing isn't great. You wind up having a
;; notion somewhere that your state is "finished" and that diffs should be
;; collected.
(deftype DiffingMap [contents diff]
  IDiffTracking
  (getDiff [this]
    diff)

  ;; Recursively truncate logs
  #_(emptyDiff [this]
      (DiffingMap.
       (into {}
             (map (fn [[k v]]
                    [(without-diff k) (without-diff v)]))
             contents)
       nil))

  ;; Lazy version - full tree diffing is a right pain anyway.
  (emptyDiff [this]
    (DiffingMap. contents nil))

  clojure.lang.IPersistentMap
  (assoc [this k v]
    (DiffingMap.
     (.assoc contents k v)
     (vconj diff
            (if (contains? contents k)
              [:change k (get contents k) v]
              [:insert k nil v]))))

  (assocEx [_ k v]
    ;; DEAD CODE
    (throw (Exception.)))

  (without [_ k]
    (DiffingMap.
     (.without contents k)
     (if (contains? contents k)
       (vconj diff [:remove k])
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
