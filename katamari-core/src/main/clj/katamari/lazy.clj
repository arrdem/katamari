(ns katamari.lazy
  "Helpers for writing extremely lazy code.")

(defmacro lazy-call
  "Helper enabling lazy loading of other namespaces.

  This allows me to - by partitioning the katamari codebase, achieve some amount of lazy loading and
  reduce start-up time by trying to avoid loading non-essential classes."
  [qual-sym & args]
  `(do (require '~(symbol (namespace qual-sym)))
       ((resolve '~qual-sym) ~@args)))
