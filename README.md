# Katamari
<img align="right" src="/etc/katamari.jpg" width=300/>

[![Clojars Project](http://clojars.org/me.arrdem/katamari/latest-version.svg)](https://clojars.org/me.arrdem/katamari)

> King of All Cosmos: We are moved to tears by the size of this thing.

**TL;DR** A file sets based build tool. In Clojure, for Clojure.

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [Katamari](#katamari)
    - [Quickstart](#quickstart)
        - [Project demo](#project-demo)
        - [Tasks](#tasks)
    - [Documentation](#documentation)
    - [License](#license)

<!-- markdown-toc end -->

## Quickstart

From the [releases](https://github.com/arrdem/katamari/releases/latest) page, download the latest `kat`.

The `kat` script is self-bootstrapping.
Place it somewhere on your path, I like `~/bin/kat`.

Then from a git repo, run

```
$ ./kat start-server
```

This will cause Katamari to self-bootstrap, downloading the latest server standalone jar and creating a couple files.
You'll see a `kat.conf`, a `kat-deps-defaults.edn` and a `kat-deps-resolve.edn`, as well as the new directories `.kat.d` and `target`.

The `kat.conf` file is a simple key-value config file used to configure both the `kat` shell client, and consumed by the server when handling requests.
It sets important properties like what the entrypoint namespace is, and what ports the server should attempt to use.

The files `kat-deps-defaults.edn` and `kat-deps-resolve.edn` are `deps.edn` data, and their names are specified in `kat.conf` so you can rename them however you wish as long as `kat.conf` points to them.
Respectively, they provide default dependency data and a default profile used by Katamari when building classpaths.
One possible use case is to provide `:default-versions` in `kat-deps-resolve.edn`, so that by referring to libs at the coordinate `nil` your targets can get the repo pinned default version of the lib.

The `.kat.d` directory is Katamari's workspace.
It contains files like the bootstrap jar, the server's logfile(s), any cached information and any build history.
You should be able to add it to your `.gitignore` and not touch it.
If you feel the urge to rename it, you can as long as `kat.conf` points to it.

The `target` drectory is the default location for build products.
When you build something with Katamari, it'll wind up there.

Okay.
So now lets build something!

### Project demo

A Katamari project is a git repository containing one or more files named `Rollfile`.
Rollfiles are somewhat like Makefiles - they define a graph of targets and their production rules.

In Leiningen, one could write the following `project.clj` -

```clj
(defproject simple-app
  :java-sources ["src/jvm/main"]
  :sources ["src/clj/main"]
  :dependencies [[org.clojure/clojure "1.9.0"]]
```

When Leiningen operates on this project, it will first use `javac` to produce the classfiles for your Java sources, and then do whatever you asked for in a classpath containing those classfiles, your Clojure sources and dependencies.
But this is clearly a two step process.
First you `javac`, then you work with the Clojure application.

In Katamari, this dependency is explicit.
One could write the following rollfile -

```clj
(deftarget my/simple-app
  (clojure-library
   :paths ["src/clj/main"]
   :deps {my/java-library nil
          org.clojure/clojure nil}))
  
(deftarget my/java-library
  (java-library
   :paths ["src/main/jvm"]))
```

Katamari, like Leiningen, will compile your Java sources before doing anything with your Clojure target.
However this dependency tree can go as deep as you would like it to.
Your Java target could in turn depend on another Java target, or a Kotlin target, or what have you.

Katamari is designed to enable incremental builds wherever possible.
This means that, unless there's no cache data available, Katamari should never do a full rebuild of your project(s).
For instance in this example, if there were no changes to the definition of your Java library, its dependencies or its files, why should it be rebuilt?
It's already "up to date".

### Tasks

Katamari's tasks are extremely a work in progress, so more documentation here will have to come later.

## Documentation

- [Manifesto](/docs/manifesto.md)
- [System Architecture](/docs/system-architecture.md)
- [Build Graph & Targets](/docs/build-graph.md)

## License

Copyright © 2018 Reid "arrdem" McKenzie

Distributed under the [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html) either version 1.0 or (at your option) any later version.
