(ns maxwell.daemon
  "We can't reverse entropy. But we can track it.

  Implements change tracking data structures.

  May or may not be a good idea. Inspired by the general impossibility of
  recovering correct, diff information without integration into the change
  machinery. clojure.data/diff and other equivalent machinery ultimately has to
  try and reconstruct the edit sequence, which is what makes correct minimal
  diffing hard.

  Spying on how your structures are manipulated however is easy.

  For all that it may not be appropriate to large structures or lazy seqs."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]})

(set! *warn-on-reflection* true)

(definterface IDiffTracking
  (emptyDiff [])
  (getDiff []))

(defprotocol ADiffCapable
  (with-diff [o]))

(extend-protocol ADiffCapable
  java.lang.Object
  (with-diff [o] o)

  nil
  (with-diff [o] o))

(defn get-diff [val]
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

(deftype DiffingMap [^clojure.lang.IPersistentMap contents
                     ^clojure.lang.IPersistentVector diff]
  IDiffTracking
  (getDiff [this]
    diff)

  (emptyDiff [this]
    (DiffingMap. contents nil))

  ADiffCapable
  (with-diff [this]
    this)

  clojure.lang.IPersistentMap
  (assoc [this k v]
    (DiffingMap.
     (.assoc contents k (without-diff v))
     (vconj diff
            (if (contains? contents k)
              [:change k (get contents k) v (get-diff v)]
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
    (if-let [e (.entryAt contents k)]
      (clojure.lang.MapEntry. (key e) (with-diff (val e)))))

  clojure.lang.IPersistentCollection
  (count [_]
    (.count contents))

  (cons [this o]
    (if-not (nil? o)
      (apply assoc this o)
      this))

  (empty [_]
    (DiffingMap. (.empty contents) nil))

  (equiv [_ o]
    (.equiv contents o))

  clojure.lang.Seqable
  (seq [_]
    (.seq contents))

  clojure.lang.ILookup
  (valAt [_ k]
    (with-diff (.valAt contents k)))

  (valAt [_ k not-found]
    (let [sentinel (Object.)
          val (.valAt contents k sentinel)]
      (if (= val sentinel) not-found (with-diff val)))))

(extend-protocol ADiffCapable
  clojure.lang.IPersistentMap
  (with-diff [e]
    (DiffingMap. e nil)))

;; FIXME (arrdem 2018-10-21):
;;   Implement an equivalent diff tracking vector.
;;   That covers most of my non-atomic use cases.

(deftype DiffingVector [^clojure.lang.IPersistentVector contents
                        ^clojure.lang.IPersistentVector diff]
  ADiffCapable
  (with-diff [o] o)

  IDiffTracking
  (getDiff [this]
    diff)

  (emptyDiff [this]
    (DiffingVector. contents nil))

  clojure.lang.IPersistentVector
  (assoc [this k v]
    (DiffingVector.
     (.assoc contents k (without-diff v))
     (vconj diff
            (if (contains? contents k)
              [:change k (get contents k) v (get-diff v)]
              ;; This may be dead code for vectors?
              [:insert k nil v nil]))))

  clojure.lang.IPersistentCollection
  (count [_]
    (.count contents))

  (cons [this o]
    (DiffingVector.
     (.cons contents (without-diff o))
     (vconj diff [:insert (count contents) nil o (get-diff o)])))

  (empty [_]
    (DiffingVector. (.empty contents) nil))

  (equiv [_ o]
    (.equiv contents o))

  clojure.lang.Seqable
  (seq [_]
    (.seq contents))

  clojure.lang.ILookup
  (valAt [_ k]
    (with-diff (.valAt contents k)))

  (valAt [_ k not-found]
    (let [sentinel (Object.)
          val (.valAt contents k sentinel)]
      (if (= val sentinel) not-found (with-diff val)))))

(extend-protocol ADiffCapable
  clojure.lang.IPersistentVector
  (with-diff [e]
    (DiffingVector. e nil)))
