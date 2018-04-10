# System Architecture
[⛓ README](/README.md)

Katamari is a two part system, consisting of a build server and a CLI client / launcher.
The client simply provides a shim for locating the server, and calling into it to provide task execution.

The server is a persistent JVM process used to amortize JVM startup costs and build graph analysis across multiple task invocations.

Katamari consumes `Rollfile`s, which provide definitions of build targets, target types and build plugins.

When the CLI client is invoked, it sends the current working directory and arguments to the server.
The server provides all task parsing, `Rollfile` location, and task execution.

For debugging, the server provides an embedded nREPL server.

