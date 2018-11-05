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
  (getDiff [])
  (getContents []))

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

(defn empty-diff [val]
  (if (instance? IDiffTracking val)
    (.emptyDiff ^IDiffTracking val)
    val))

(defn without-diff [val]
  (if (instance? IDiffTracking val)
    (.getContents ^IDiffTracking val)
    val))

;;;; A change tracking map

(deftype DiffingMap [^clojure.lang.IPersistentMap contents
                     ^clojure.lang.IPersistentVector diff]
  IDiffTracking
  (getDiff [this]
    diff)

  (emptyDiff [this]
    (DiffingMap. contents nil))

  (getContents [this]
    contents)

  ADiffCapable
  (with-diff [this]
    this)

  clojure.lang.IPersistentMap
  (assoc [this k v]
    (let [v+ (without-diff v)]
      (DiffingMap.
       (.assoc contents k v+)
       (into (or diff [])
             (if (contains? contents k)
               (let [v- (get contents k)]
                 (when-not (= v+ v-)
                   [[:change k v- v+ (get-diff v)]]))
               [[:insert k nil v+ nil]])))))

  (assocEx [_ k v]
    ;; DEAD CODE
    (throw (Exception.)))

  (without [_ k]
    (DiffingMap.
     (.without contents k)
     (into (or diff [])
           (when (contains? contents k)
             [[:remove k (get contents k) nil nil]]))))

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
    (.equiv contents (without-diff o)))

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

;;;; A change tracking vector

(deftype DiffingVector [^clojure.lang.IPersistentVector contents
                        ^clojure.lang.IPersistentVector diff]
  ADiffCapable
  (with-diff [o] o)

  IDiffTracking
  (getDiff [this]
    diff)

  (emptyDiff [this]
    (DiffingVector. contents nil))

  (getContents [this]
    contents)

  clojure.lang.IPersistentVector
  (assoc [this k v]
    (let [v+ (without-diff v)]
      (DiffingVector.
       (.assoc contents k v+)
       (into (or diff [])
             (if (contains? contents k)
               (let [v- (get contents k)]
                 (when-not (= v+ v-)
                   [[:change k v- v+ (get-diff v)]]))
               [[:insert k nil v+ nil]])))))

  clojure.lang.IPersistentCollection
  (count [_]
    (.count contents))

  (cons [this o]
    (DiffingVector.
     (.cons contents (without-diff o))
     (into (or diff [])
           [[:insert (count contents) nil o (get-diff o)]])))

  (empty [_]
    (DiffingVector. (.empty contents) nil))

  (equiv [_ o]
    (.equiv contents (without-diff o)))

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
