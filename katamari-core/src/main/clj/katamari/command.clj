(ns katamari.command
  "Implementation of Katamari's command and dispatching system."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.spec.alpha :as s]
            [katamari.lazy :refer [lazy-call]])
  (:import katamari.ex.CommandNotFoundException))

(defn find-commands
  "Walk the classpath looking for kat.cmd files.

  These files are EDN mappings from unqualified symbols naming commands to qualified symbols naming
  functions implementing the command. Functions implementing commands MUST have an `:args` fspec,
  being a spec of a sequence of strings. Functions implementing commands will be applied to the
  result of conforming the command's arguments to their `:args` fspec.

  WARNING: In keeping with Java's classpath behavior, while many kat.cmd files may be loaded, the
  first file on the classpath to report a mapping for a command wins."
  []
  )

;; FIXME (arrdem 2018-09-09):
;;  How do commands interact with the build graph and config? Do they 

(defn -command
  "The entry point used by katamari.core/-main for running commands.

  Throws `katamari.ex.CommandNotFoundException` if the specified command cannot be located."
  [command-name unparsed-args]
  (if-let [qsym (get (find-commands) (symbol command-name))]
    (do (require qsym)
        (let [spec (:args (s/get-spec qsym))]
          (apply (resolve qsym) (s/conform spec unparsed-args))))
    (throw (CommandNotFoundException. "Unable to locate the given command!"))))
