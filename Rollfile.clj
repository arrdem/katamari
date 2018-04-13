;; Rollfile.clj
;;
;; This file is a normal Clojure program which produces a katamari build configuration.
;; The state it produces is represented in Rollfile.edn

(require '[katamari.core :as kat])

(defn contrib-style-clojure-library [build name options]
  (kat/clojure-library build name
     (-> options
         (update-in :base :paths (fnil into [])
                    [(str name "/src/main/clj")
                     (str name "/src/main/cljc")
                     (str name "/src/main/resources")])
         (update-in :dev :paths (fnil into [])
                    [(str name "/src/dev/clj")
                     (str name "/src/dev/cljc")
                     (str name "/src/dev/resources")])
         (update-in :test :paths (fnil into [])
                    [(str name "/src/test/clj")
                     (str name "/src/test/cljc")
                     (str name "/src/test/resources")]))))

(-> (kat/empty-build)
    (kat/load-profiles "/etc/katamari.edn")
    (kat/load-profiles "~/.katamari/profiles.edn")

    ;; Make dependencies available as targets
    (kat/mvn-dep '[org.clojure/clojure "1.9.0"])
    (kat/mvn-dep '[org.clojure/specs.alpha "0.1.143"])
    (kat/mvn-dep '[org.clojure/tools.deps.alpha "0.5.417"])
    (kat/mvn-dep '[org.clojure/test.check "0.9.0"])
    (kat/mvn-dep '[org.clojure/tools.nrepl "0.2.12"])
    (kat/mvn-dep '[io.replikativ/hasch "0.3.4"])

    ;; Clojure libraries
    (contrib-style-clojure-library 'katamari-core
      '{:dependencies [org.clojure/clojure
                       org.clojure/specs.alpha
                       org.clojure/tools.deps.alpha
                       io.replikativ/hasch]})

    (contrib-style-clojure-library 'katamari-server
      '{:dependencies [katamari-core
                       org.clojure/tools.nrepl]})

    ;; Roll everything up for easy deployment
    (kat/mvn-artifact 'me.arrdem/katamari
      '{:dependencies [katamari-server]
        :version "0.0.0"})

    ;; Lets do it!
    (kat/roll!))

