;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.util.maven
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.tools.deps.alpha.util.io :refer [printerrln]])
  (:import
    ;; maven-resolver-api
    [org.eclipse.aether RepositorySystem RepositorySystemSession]
    [org.eclipse.aether.artifact Artifact DefaultArtifact]
    [org.eclipse.aether.repository LocalRepository RemoteRepository RemoteRepository$Builder]
    [org.eclipse.aether.graph Dependency Exclusion]
    [org.eclipse.aether.transfer TransferListener TransferEvent TransferResource]

    ;; maven-resolver-spi
    [org.eclipse.aether.spi.connector RepositoryConnectorFactory]
    [org.eclipse.aether.spi.connector.transport TransporterFactory]
    [org.eclipse.aether.spi.locator ServiceLocator]

    ;; maven-resolver-connector-basic
    [org.eclipse.aether.connector.basic BasicRepositoryConnectorFactory]

    ;; maven-resolver-transport-file
    [org.eclipse.aether.transport.file FileTransporterFactory]

    ;; maven-resolver-transport-http
    [org.eclipse.aether.transport.http HttpTransporterFactory]

    ;; maven-resolver-transport-wagon
    [org.eclipse.aether.transport.wagon WagonTransporterFactory WagonProvider]

    ;; maven-aether-provider
    [org.apache.maven.repository.internal MavenRepositorySystemUtils]

    ;; maven-resolver-util
    [org.eclipse.aether.util.repository AuthenticationBuilder]

    ;; maven-core
    [org.apache.maven.settings DefaultMavenSettingsBuilder]

    ;; maven-settings-builder
    [org.apache.maven.settings.building DefaultSettingsBuilderFactory]
    ))

(set! *warn-on-reflection* true)

;; Remote repositories

(def standard-repos {"central" {:url "https://repo1.maven.org/maven2/"}
                     "clojars" {:url "https://repo.clojars.org/"}})

(defn- set-settings-builder
  [^DefaultMavenSettingsBuilder default-builder settings-builder]
  (doto (.. default-builder getClass (getDeclaredField "settingsBuilder"))
    (.setAccessible true)
    (.set default-builder settings-builder)))

(defn- get-settings
  ^org.apache.maven.settings.Settings []
  (.buildSettings
    (doto (DefaultMavenSettingsBuilder.)
      (set-settings-builder (.newInstance (DefaultSettingsBuilderFactory.))))))

(defn remote-repo
  ^RemoteRepository [[^String name {:keys [url]}]]
  (let [repository (RemoteRepository$Builder. name "default" url)
        ^org.apache.maven.settings.Server server-setting
        (first (filter
                 #(.equalsIgnoreCase name
                                     (.getId ^org.apache.maven.settings.Server %))
                 (.getServers (get-settings))))]
    (cond-> repository
      server-setting
      (.setAuthentication (-> (AuthenticationBuilder.)
                              (.addUsername (.getUsername server-setting))
                              (.addPassword (.getPassword server-setting))
                              (.addPrivateKey (.getPrivateKey server-setting)
                                              (.getPassphrase server-setting))
                              (.build)))
      true
      (.build))))

;; Local repository

(def ^:private home (System/getProperty "user.home"))
(def default-local-repo (.getAbsolutePath (jio/file home ".m2" "repository")))

(defn make-local-repo
  ^LocalRepository [^String dir]
  (LocalRepository. dir))

;; Maven system and session

;; TODO: in the future this could be user-extensible
(deftype CustomProvider []
  WagonProvider
  (lookup [_ role-hint]
    (if (contains? #{"s3" "s3p"} role-hint)
      (org.springframework.build.aws.maven.PrivateS3Wagon.)
      (throw (ex-info (str "Unknown wagon provider: " role-hint) {:role-hint role-hint}))))
  (release [_ wagon]))

;; Delay creation, but then cache Maven ServiceLocator instance
(def the-locator
  (delay
    (doto (MavenRepositorySystemUtils/newServiceLocator)
      (.addService RepositoryConnectorFactory BasicRepositoryConnectorFactory)
      (.addService TransporterFactory FileTransporterFactory)
      (.addService TransporterFactory HttpTransporterFactory)
      (.addService TransporterFactory WagonTransporterFactory)
      (.setService WagonProvider CustomProvider))))

(defn make-system
  ^RepositorySystem []
  (.getService ^ServiceLocator @the-locator RepositorySystem))

(def ^TransferListener console-listener
  (reify TransferListener
    (transferStarted [_ event]
      (let [event ^TransferEvent event
            resource (.getResource event)
            name (.getResourceName resource)]
        (printerrln "Downloading:" name "from" (.getRepositoryUrl resource))))
    (transferCorrupted [_ event]
      (printerrln "Download corrupted:" (.. ^TransferEvent event getException getMessage)))
    (transferFailed [_ event]
      ;; This happens when Maven can't find an artifact in a particular repo
      ;; (but still may find it in a different repo), ie this is a common event
      #_(printerrln "Download failed:" (.. ^TransferEvent event getException getMessage)))))

(defn make-session
  ^RepositorySystemSession [^RepositorySystem system local-repo]
  (let [session (MavenRepositorySystemUtils/newSession)
        local-repo-mgr (.newLocalRepositoryManager system session (make-local-repo local-repo))]
    (.setLocalRepositoryManager session local-repo-mgr)
    (.setTransferListener session console-listener)
    session))

(defn exclusions->data
  [exclusions]
  (when (and exclusions (pos? (count exclusions)))
    (into #{}
      (map (fn [^Exclusion exclusion]
             (symbol (.getGroupId exclusion) (.getArtifactId exclusion))))
      exclusions)))

(defn dep->data
  [^Dependency dep]
  (let [scope (.getScope dep)
        optional (.isOptional dep)
        exclusions (exclusions->data (.getExclusions dep))
        ^Artifact artifact (.getArtifact dep)
        classifier (.getClassifier artifact)
        ext (.getExtension artifact)]
    [(symbol (.getGroupId artifact) (.getArtifactId artifact))
     (cond-> {:mvn/version (.getVersion artifact)}
       (not (str/blank? classifier)) (assoc :classifier classifier)
       (not= "jar" ext) (assoc :extension ext)
       scope (assoc :scope scope)
       optional (assoc :optional true)
       (seq exclusions) (assoc :exclusions exclusions))]))

(defn coord->artifact
  ^Artifact [lib {:keys [mvn/version classifier extension] :or {classifier "", extension "jar"}}]
  (let [version (or version "LATEST")
        artifactId (name lib)
        groupId (or (namespace lib) artifactId)
        artifact (DefaultArtifact. groupId artifactId classifier extension version)]
    artifact))

(defn version-range?
  [version]
  (boolean (re-find #"\[|\(" version)))
