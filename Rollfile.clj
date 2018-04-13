{;; Profiles are a mechanism for providing configuration both of targets and tasks.
 ;; Profiles are either maps of profile data, or a sequence containing either keywords naming other
 ;; profiles to apply or maps of profile data.
 :profiles
 {;; The default profile stack which Katamari attempts to apply.
  ;; FIXME: How does profile de-activation work? Does it work? Do we need it?
  :katamari/default
  [:base :system :user :provided :dev]

  ;; Base is the most fundamental profile, and will come from Katamari itself.
  ;; There shouldn't really be a reason to manipulate it ever.
  :base
  {;; Options used by all Maven targets
   :maven
   {:repo "~/.m2"
    :repositories [{:names ["central", "maven-central"]
                    :urls ["https://repo1.maven.org/maven2"]
                    :snapshots false}
                   {:names ["clojars"]
                    :urls ["https://repo.clojars.org"]}]}}

  ;; System is a concept which exists in Leiningen and may or may not make sense.  The system
  ;; profile may be defined in an /etc/katamari.edn file, to provide global options.  Consequently
  ;; system profiles should be minimal, and are probably best used only for organizational
  ;; configuration such as defining repositories.
  :system
  {}

  ;; User may be defined in ~/.katamari/profiles.clj
  ;; The user profile is intended for providing user-specific environment customization.
  ;; For instance setting a prompt, or adding ones own toolkit.
  :user
  {}

  ;; Tasks, such as test, compile, uberjar, repl and soforth all execute with a special-purpose
  ;; profile named for the task. User defined tasks likewise execute with an additional profile
  ;; named for the task's alias.
  :test
  {}

  :compile
  {}

  :repl
  {}

  :uberjar
  {}

  :deploy
  {}
  }

 :targets
 {;; Targets are things which Katamari "knows" how to build.
  ;; They're named by a symbol, and defined by a pair `[target-type target-config]`.
  ;; Targets become "configured" based on the profiles which Katamari is rolling with.
  ;; Consequently target configuration is a map from profile names to configuration.
  ;; Targets usually just specify the `:default` profile.

  org.clojure/clojure
  ;; This is a Maven target.
  ;; It uses the `:maven` profile options together with whatever configuration is applied here.
  ;; Maven artifacts are downloaded from a `:repository` which must name a known Maven repository
  ;; at a given `:group`, `:artifact`, `:version`, `:qualifier` address.
  ;;
  ;; Maven targets implicitly depend on their own dependencies.
  ;; Users are not required to manage those.
  ;; When Maven targets are used as dependencies of other targets, dependency resolution occurs.
  ;; That is, version conflicts are deferred until Katamari rolls a target which forces a conflict.
  ;;
  ;; This behavior is chosen to align with Leiningen and Twitter's Pants, as opposed to Facebook's
  ;; Buck which requires that transitive dependencies be manually managed.
  [:maven
   {:base
    {:repository "central"
     :coordinates {:group "org.clojure"
                   :artifact "clojure"
                   :version "1.9.0"}}}]

  org.clojure/specs.alpha
  [:maven
   {:base
    {:repository  "central"
     :coordinates {:group    "org.clojure"
                   :artifact "specs.alpha"
                   :version  "0.1.143"}}}]

  org.clojure/tools.deps.alpha
  [:maven
   {:base
    {:repository  "central"
     :coordinates {:group    "org.clojure"
                   :artifact "tools.deps.alpha"
                   :version  "0.5.417"}}}]

  org.clojure/test.check
  [:maven
   {:base
    {:repository  "central"
     :coordinates {:group    "org.clojure"
                   :artifact "test.check"
                   :version  "0.9.0"}}}]

  org.clojure/tools.nrepl
  [:maven
   {:base
    {:repository  "central"
     :coordinates {:group    "org.clojure"
                   :artifact "tools.nrepl"
                   :version  "0.2.12"}}}]

  io.replikativ/hasch
  [:maven
   {:base
    {:repository  "clojars"
     :coordinates {:group    "io.replikativ"
                   :artifact "hasch"
                   :version  "0.3.4"}}}]

  katamari-core
  ;; This is a Clojure target.  It has `:paths`, being directory trees to be added to the classpath,
  ;; eg. for adding Clojure sources or Java resources and `:dependencies`, being a sequence of other
  ;; targets which it depends on. Depended targets are checked and executed as needed in topological
  ;; sort order when they change.
  [:clojure
   {:base
    {:paths ["katamari-core/src/main/cljc"
             "katamari-core/src/main/clj"
             "katamari-core/src/main/resources"]
     :dependencies [org.clojure/clojure
                    org.clojure/specs.alpha
                    org.clojure/tools.deps.alpha
                    io.replikativ/hasch]}
    ;; This target provides some additional configuration for development use
    :dev
    {:paths ["katamari-core/src/dev/cljc"
             "katamari-core/src/dev/clj"
             "katamari-core/src/dev/resources"]}
    ;; And for testing
    :test
    {:paths ["katamari-core/src/test/cljc"
             "katamari-core/src/test/clj"
             "katamari-core/src/test/resources"]
     :dependencies [org.clojure/test.check]}]}

  katamari-server
  [:clojure-library
   {:base
    {:paths ["katamari-server/src/main/cljc"
             "katamari-server/src/main/clj"]
     :dependencies [katamari-core
                    org.clojure/tools.nrepl]}
    :dev
    {:paths ["katamari-core/src/dev/cljc"
             "katamari-core/src/dev/clj"]}}]

  katamari
  [:clojure-library
   {:dependencies #{katamari-core
                    katamari-server}}]}}
