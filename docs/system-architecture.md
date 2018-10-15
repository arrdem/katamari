# System Architecture
[⛓ README](/README.md)

Katamari is designed in response to `clojure-tools`, `boot` and `leiningen`.

`clojure-tools` and `lein` are implemented as Clojure + JVM applications, which start and stop.
This means that in general users suffer all the pains of starting Clojure based systems and really don't see many of the benefits.
Clojure itself takes a second or two to load on average, and every additional dependency just adds weight.
Furthermore in order to take advantage of graalvm or other Java optimizing tools it's imperative to strip the dependencies of these tools down which prevents their implementations from leveraging library ecosystems.

`boot` is designed to be used somewhat persistently, as boot builds consist of watch-based programs and the initial startup and dependency loading time is amortized over a session of sorts.
This makes boot more tractable, but boot was designed and optimized for solving the problem of updating built resources such as `less` files and `cljs` to be served in a browser.
Because in the context of a webapp all these resources must converge in the application, boot is not designed for a concept of targets or to support "partial" builds.

Katamari is designed as a small shell script - `kat`, which interacts with a persistent JVM server.
The shell script serves as a shim, which starts a server instance if there isn't an active one, and then uses the server to perform any required actions.

Beyond this, Katamari's architecture is currently somewhat murky.
As the Katamari server is intended to be durable, while it embeds a REPL (including CIDER), it's not really an appropriate vehicle from which to execute build steps.
These would seem more naturally and more interactively placed close to or even in the user's shell, rather than having the remote server opaquely and non-interactively execute many commends.

There would also seem to be cause to enable users to write their own shell scripts which interact with the Katamari server.
For instance one could imagine a `kat status` task, implemented as a shell script which uses the normal `kat` machinery to inspect the server, and can avoid the default server starting behavior.
