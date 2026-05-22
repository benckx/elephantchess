# About

[elephantchess.io](https://elephantchess.io) is a web application to play and
study [Chinese chess](https://en.wikipedia.org/wiki/Xiangqi) (or xiangqi 象棋).

By default, the project is under GPL-3.0 license. Libraries (like the Kotlin xiangqi-core or the
JavaScript [board-gui](https://elephantchess.io/about/developers/board-gui-example)) are under LGPL-3.0 license to allow
for a more permissive use (i.e. to re-use the libraries in a commercial application).

We have a little [Discord server](https://discord.gg/WEGDqnWXNg) for open discussion.

[![Build](https://github.com/benckx/elephantchess/actions/workflows/build.yml/badge.svg)](https://github.com/benckx/elephantchess/actions/workflows/build.yml)

## Features

The webapp offers the following features:

- Play against other players (or PvP), where users can play against each other in real-time and chat. Users can find
  each other in the Lobby or create a game and share the link with friends.
- Play against the bot (PvB), where user can play against the computer (i.e. the engine).
- Puzzles (or tactics), where users have to find the best moves from a given position.
- Database of tournaments games, events, players and statistic. To avoid confusion with the PostgreSQL database, we'll
  try to specify when we talk about the "PostgreSQL database" or the "Database" as a feature.
- Analysis board, where users can create complex analysis, with engine evaluation, multiple embedded variations,
  annotations (i.e. `??`, `?!` symbols) and comments.
- Manchu chess (Yitong 一统) variant: Red plays with a single super-chariot (Banner/统, FEN char `M`) instead of the
  usual chariots, horses and cannons. The super-chariot combines the movement powers of all three pieces. Supported in
  PvP and PvB (Fairy Stockfish only); analysis is not available for Manchu games.
- By default, users are assigned a temporary guest user id. They can also sign up with an email and password to keep
  their data across devices and access all features.

## Principles

- Keep the webapp free to use
- Keep the webapp ads free (although we sometimes cross-promote with other projects, and we use Google Ads to increase
  traffic)
- Minimize overall annoyances (banners, emails, notifications, etc.)
- Keep the GUI simple and intuitive, and that works properly even on older or cheaper devices
- Be transparent on how user data is used
- Keep the user in control
- We're trying to reach a financially self-sustained platform, based on users donations and merch, with the quality of
  the service as the priority

## Glossary

- The engines are AI (or bots) used for the Play-against-bot (PvB) or Analysis Board features. In the chess community,
  the term "engine" is preferred over AI or bot.
- In chess in general, the "columns" of the board are named "files" and the "rows" are named "ranks".
- The Forsyth–Edwards Notation (or FEN) is a standard notation to represent chess positions as a single line of text. In
  Chinese chess, the starting position is encoded as
  `rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0`. It is convenient for data transfer and
  indexing.
- The algebraic notation is a standard notation to represent chess moves. The "columns" or the board are called "files"
  and the "rows" are named "ranks". In the algebraic notation, the files are numbered from a to i and the ranks are
  numbered from 1 to 10. So the move `C2=5` is represented in algebraic notation as "h3e3" (i.e. move the piece from
  file h, rank 2 to file e, rank 2).
- The UCI (Universal Chess Interface) is a standard protocol to communicate with chess engines. It defines a set of
  commands and responses that allow a chess engine to be controlled by a user interface. For example, the command
  `position fen rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0` tells the engine to set up the
  board with the given FEN position; and the command `go depth 10` tells the engine to start calculating the best move
  from that position with a search depth of 10.
- The UCI move notation is similar to the algebraic notation, except that the ranks are 0-based. Therefore, any
  move can be encoded as 4 characters. For example, move h3e3 is encoded in UCI as h2e2.
- The WXF notation is the traditional Chinese chess notation (e.g. `C2=5`), widely in use. It's not as convenient
  technically because move can be ambiguous and the files are numbered from the right to the left relative to the
  player (so it's not the same numbers for the red and black players). Therefore, it's not used in code, expect for
  labeling moves in the GUI.

# Run Locally

This section describes how to run the back-end and front-end of [elephantchess.io](https://elephantchess.io) locally for
development or learning purposes.

## Pre-requisites

### Java 21

You need a JDK installed to run the back-end, version 21 or higher.

### Docker

You need Docker to run a local PostgreSQL instance. To check if you have Docker installed, you can run the following
command in your terminal:

```shell
➜  ~ docker --version
Docker version 29.4.2, build 055a478
```

## Set-up

### Engines

_Note: for the sake of simplicity, the engine binaries have been added to this repo, so you don't have anything else to
do to be able to run the webapp locally with the engines features. Nevertheless, this section covers engine set-up._

_Note: Those binaries (at least for Pikafish, not sure for Fairy Stockfish) don't work with ARM64 machines, but most
recent releases of Pikafish have multiple binaries that I don't fully understand yet, but I assume some of them may be
ARM64 compatible. But on my older Linux Mint laptop at least, that's the binaries I used._

The webapp assumes engine binaries can be found locally. So you need to create folder `engines` at the root of this
repository, download the binaries from their repositories and copying them in this format:

```
$ tree engines
engines
├── fairy
│   ├── 11.2
│   │   └── fairy-stockfish
│   └── 14.0.1
│       └── fairy-stockfish
└── pikafish
    └── 2023-03-05
        ├── pikafish-modern
        └── pikafish.nnue
```

Pikafish binaries can be found at https://github.com/official-pikafish/Pikafish/releases. Versions posterior to
2023-03-05 contain a number of binaries that I don't know how to use, so as of
now [elephantchess](https://elephantchess.io) uses Pikafish 2023-03-05.

Fairy Stockfish binaries can be found at https://github.com/fairy-stockfish/Fairy-Stockfish/releases. As of now
[elephantchess](https://elephantchess.io) uses 11.2 but is planning to upgrade to 14.0.1.

You can run the webapp locally without the engines by setting `engines=false` in the properties file, but of course you
won't be able to play against the bot or run analysis.

### PostgreSQL Docker container

You need to run a local PostgreSQL instance to run the webapp locally. The easiest way to do that is to run a PostgreSQL
Docker container with the following command:

```shell
docker run -d --rm \
    --name xiangqi-db \
    -p 5432:5432 \
    -e POSTGRES_PASSWORD=postgres \
    -e POSTGRES_DB=xiangqi \
    postgres:17.6
```

Once the container is running, you should see it in the list of running containers:

```shell
➜  ~ docker ps
CONTAINER ID   IMAGE           COMMAND                  CREATED         STATUS         PORTS                                         NAMES
c4e5aa14100b   postgres:17.6   "docker-entrypoint.s…"   9 seconds ago   Up 8 seconds   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp   xiangqi-db
```

You can then import the dev database (which contains a few games, puzzles and test users):

```shell
docker exec -i xiangqi-db \
    pg_restore --no-privileges --no-owner --verbose \
    -U postgres -d xiangqi -1 < dev_dataset.pgsql
```

## Build and Run

To build (we can skip unit tests to speed it up):

```shell
./gradlew clean build -x test
```

To run:

```shell
java -jar webapp/build/libs/webapp.jar
```

Then you should be able to access the webapp at http://localhost:8080.

Alternatively, you can launch the `Main` from your IDE of choice.

## Common Issues

### Port 8080 blocked

If you stop the process, it's possible that the 8080 port remains blocked when you try to re-start it later. You need to
check how to kill the zombie process still blocking the port.

On macOS, you can run the following command to kill the process blocking the port:

```shell
lsof -ti :8080 | xargs kill -9             
```

On Linux (on Mint):

```shell
fuser -k 8080/tcp
```

### Invalid guest session

If you run the webapp locally with a new database instance, you might see an error in the browser and in the console
that the guest user is invalid. This is perfectly normal since the token lives in the browser and on a new database
instance, that guest user will not be found. A new guest session will be created automatically and the page will
refresh.

# Back-End (Kotlin)

The back-end is written in Kotlin and based on KTor to serve REST and WebSocket endpoints for the front-end, as well as
HTML pages. The back-end also uses Koin for dependency injection.

The Main class is pretty concise:

```kotlin
fun main(args: Array<String>) {
    // read args, to know how to fetch the app_<profile>.properties
    val argsConfig = parseArgs(args)
    logger.info { "args: ${args.joinToString(" ")}" }
    logger.info { "starting with $argsConfig" }

    // dependency injection with Koin
    startKoin {
        modules(
            // one module for services (independent of any routing)
            serviceLayerModule(
                argConfig = argsConfig,
                eagerAllowed = true
            ),
            // one module for webapp (KTor, routes, endpoints, JavaScript and CSS assets, etc.)
            webAppKoinModule(eagerAllowed = true)
        )
    }

    // start the Ktor server
    embeddedServer(factory = Netty, port = 8080, module = Application::kTorModule)
        .start(wait = true)
}

// different KTor submodules for different purposes
private fun Application.kTorModule() {
    configureDefaultHeaders()
    exceptionHandler()
    cachingModule()
    staticAssetsModule()
    apiServiceModule() // all the routes defining the endpoints used by the front-end
    htmlRoutingModule() // all the routes to access the HTML pages
    sitemapRoutingModule()
    shutdownModule()
    healthCheckModule()
}
```

Except for KTor, Koin, a Kubernetes client, Apache Commons, the project has few dependencies.

## Gradle Modules

If you check the `settings.gradle.kts` file, you will see that the project is made of several modules:

```
include("utils")
include("engine-api")
include("xiangqi-core")
include("xiangqi-core-test-utils")
include("seven-kingdoms-core")
include("seven-kingdoms-core-test-utils")
include("webapp-config")
include("webapp-dao")
include("webapp-dao-migration")
include("webapp-html-renderer")
include("webapp-model-common")
include("webapp-service-layer")
include("webapp")
include("scripts")
```

### utils

Small module with a couple of utility functions.

### webapp-model-common

Small module with mostly enum which can be re-use by both DAO (i.e. for PostgreSQL database) and DTO.

### webapp-config

Small module to parse and load configuration properties files. Properties files names use the format
`app_<profile>.properties` where profile can be e.g. `local` or `prod`. This profile is passed to Main which resolves
the correct file.

This repository provides `app_local.properties` with default values for local development.

### webapp-dao

Contains "DAO services" like `PlayerVsPlayerGameDaoService` or `UserDaoService` that contains SQL queries.

Those services rely on the generated code by jOOQ. The POJO and fields are generated. The generation happens
automatically during the build.

For example, in the following function, table and columns `USER`, `USER.EMAIL`, etc. are generated by jOOQ codegen tool.

```kotlin
suspend fun fetchEmail(userId: String): String? {
    return dslContext
        .select(USER.EMAIL)
        .from(USER)
        .where(USER.ID.eq(userId))
        .and(USER.USER_TYPE.eq(AUTHENTICATED))
        .awaitSingleValue()
}

```

In order to generate the dao code, jOOQ needs the latest DDL. To do this, it loads the Liquibase migrations on a
in-memory H2 database to access tables definitions.

One quirk about this is that this H2 instance doesn't support some changes, so there is another version of the
`liquibase-changelog.xml` file (`liquibase-changelog-generation.xml`) which is generated from a script. So any change to
`liquibase-changelog.xml` should be followed by an execution of `LiquibaseGeneration` script before re-building, in
order to get the updated generated code.

Check the Gradle task `dao-code-gen` in the build file for more details.

The underlying driver is R2DBC which is a reactive driver, so queries are non-blocking and can be used in a coroutine
context.

### webapp-dao-migration

Tiny module to manage the Liquibase migration. The reason it's separated from `webapp-dao` is because it uses JDBC
driver which shouldn't be exposed to the rest of the app.

### webapp-html-renderer

Custom HTML renderer with custom template management.

It's a bit of a weird design choice. I had experimented with frameworks like Thymeleaf or Velocity, and I wasn't very
fond of them. Furthermore, at the beginning of the project, the HTML from the backend were very empty and the JavaScript
was filling the data in (which is still the case for games pages (PvP, PvB, puzzles)). So there was little need to
introduce a fancy framework.

Nowadays, the back-end produces a bit more dynamic HTML in a somewhat ad-hoc manner. But at the end of the day, it is
easy to maintain and allows quite some flexibility.

For example, it has the ability to fetch JavaScript and CSS assets from either the JAR or the CDN; as well as either the
minified or plain versions; remove HTML comments, cache the rendered HTML, etc.

### webapp-service-layer

Probably the largest module, since it contains the main business logic of the app, for example all the CRUD services to
manage games (create, play move, browse, analyze, etc.).

This module should not contain a dependency to KTor.

### webapp

KTor-based modules that links the service layer to the REST and WebSocket endpoints, as well as the HTML pages. It also
contains the JavaScript, CSS, images, etc. assets.

## Libraries

Below are the Gradle modules designed to be used as libraries and are published on JitPack.

### engine-api

Kotlin API to launch and communicate with chess engines running as system processes.

The entry point is the `EnginePool` service, which is a coroutine-safe pool of engine processes. It allows multiple
users to use the same pool of engine processes concurrently. For example, on [elephantchess](https://elephantchess.io),
multiple users can play against the bot at different depths. Their queries are "queued" so multiple PvB games can happen
at the same time (even though technically, at a given time, a given process is used by max one user, as the process is
lockable).

You can decide e.g. to run multiple engine processes of a given engine (by increasing `poolSize`) with 1 thread for
each (`numberOfThreads` option) if you want to optimize for concurrency; or choose to run fewer engine processes but
with more threads for each instance if you want to optimize for responsiveness.

On [elephantchess](https://elephantchess.io) for example, each Kubernetes pod has an `EnginePool` with one instance of
Pikafish and one instance of Fairy Stockfish, with one thread each (so the engine processes don't use more than one CPU
core and the rest of the app remains responsive, as each pod only has 2 CPU cores at the moment). It would probably be
sensible to use a similar setup on an Android app, given that not all mobile devices have a lot of CPU cores.

The `numberOfThreads` option is not used in the `EnginePool` itself, but is simply passed along to the engine process.
In Pikafish for example, it's passed to the engine process with command `setoption name Threads value 8` (you don't need
to input that command yourself, as it's abstracted away by the `engine-api` library).

The engines are queries with the [FEN notation](https://en.wikipedia.org/wiki/Forsyth%E2%80%93Edwards_Notation). In
xiangqi, the starting position is encoded as `rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0`.

### Configuration

Engines binaries location is configured by implementing `EngineProcessLocator`.

If the `engines` folder is located at the root of this repository (excluded by `.gitignore`), you can use the default
`LocalProcessLocator`:

```kotlin
package io.elephantchess.engines.protocol.commands

object LocalProcessLocator : EngineProcessLocator {

    override fun launchCommand(binFileName: String) = "./engines/$binFileName"

}
```

It assumes folder `engines` is structured as follows:

```
$ tree engines
engines
├── fairy
│   ├── 11.2
│   │   └── fairy-stockfish
│   └── 14.0.1
│       └── fairy-stockfish
└── pikafish
    ├── 2022-12-26
    │   ├── pikafish-modern
    │   └── pikafish.nnue
    ├── 2023-02-16
    │   ├── pikafish-modern
    │   └── pikafish.nnue
    └── 2023-03-05
        ├── pikafish-modern
        └── pikafish.nnue

7 directories, 8 files
```

In the above example, multiple binaries and versions are available, but you can use the `engine-api` library with just
one version:

```
engines
└── pikafish
    └── 2023-03-05
        ├── pikafish-modern
        └── pikafish.nnue
```

You can create your own `EngineProcessLocator`. For example, on [elephantchess](https://elephantchess.io), we use this
Dockerized version:

```kotlin
object DockerizedProcessLocator : EngineProcessLocator {

    override fun launchCommand(binFileName: String) =
        "/bin/bash -lc /app/engines/$binFileName"

}
```

Pikafish binaries can be found at https://github.com/official-pikafish/Pikafish/releases. Versions posterior to
2023-03-05 contain a number of binaries that I don't know how to use, so as of
now [elephantchess](https://elephantchess.io) uses Pikafish 2023-03-05.

Fairy Stockfish binaries can be found at https://github.com/fairy-stockfish/Fairy-Stockfish/releases. As of now
[elephantchess](https://elephantchess.io) supports versions 11.2 and 14.0.1, stored under the `fairy/<version>` folder.

### Example 1

In this example, we create a pool with one Pikafish process that uses 8 physical threads. So it will have good response
time, even with large depth values, but if it runs on a machine that has 8 CPU cores or less, it will use all the CPU
when queried.

```kotlin
import io.elephantchess.engines.process.EngineConfig
import io.elephantchess.engines.process.PikafishEngineId
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors.newFixedThreadPool

fun main() {
    val engineConfig = EngineConfig("2023-03-05", poolSize = 1, numberOfThreads = 8)
    val enginePool = EnginePool(mapOf(PikafishEngineId to engineConfig), newFixedThreadPool(2))

    runBlocking {
        val fen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0"
        val infoLinesResult = enginePool.queryForDepth(fen, PikafishEngineId, 10)
        val infoLineResult = infoLinesResult?.deepestResult()
        println("parsed engine result: $infoLineResult")
        println("best move: ${infoLineResult?.pv?.first()}")
    }

    enginePool.close()
}
```

outputs

```
10:21:17.168 [pool-1-thread-1] INFO  i.e.e.process.PikafishEngineProcess - running Pikafish engine, launching ./engines/pikafish/2023-03-05/pikafish-modern
10:21:17.199 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - sending to engine: setoption name Threads value 8
10:21:17.200 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - Pikafish 2023-03-05 by the Pikafish developers (see AUTHORS file)
10:21:17.222 [main] INFO  i.e.e.process.PikafishEngineProcess - Pikafish process has started
10:21:17.224 [main] DEBUG i.e.e.process.PikafishEngineProcess - sending to engine: isready
10:21:17.641 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - readyok
10:21:17.735 [main] INFO  i.e.e.process.PikafishEngineProcess - Pikafish process is ready
10:21:17.742 [main] DEBUG i.e.e.process.PikafishEngineProcess - sending to engine: position fen rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0
10:21:17.742 [main] DEBUG i.e.e.process.PikafishEngineProcess - sending to engine: go depth 10
10:21:17.743 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - info string NNUE evaluation using pikafish.nnue enabled
10:21:17.744 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - info depth 1 seldepth 1 multipv 1 score cp 5 nodes 97 nps 48500 hashfull 0 tbhits 0 time 2 pv h0g2
10:21:17.744 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - info depth 2 seldepth 2 multipv 1 score cp 24 nodes 238 nps 79333 hashfull 0 tbhits 0 time 3 pv h2e2
10:21:17.745 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - info depth 3 seldepth 2 multipv 1 score cp 30 nodes 406 nps 135333 hashfull 0 tbhits 0 time 3 pv b2e2
10:21:17.745 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - info depth 4 seldepth 2 multipv 1 score cp 331 nodes 476 nps 158666 hashfull 0 tbhits 0 time 3 pv h2e2
10:21:17.745 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - info depth 5 seldepth 2 multipv 1 score cp 353 nodes 543 nps 135750 hashfull 0 tbhits 0 time 4 pv b2e2
10:21:17.745 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - info depth 6 seldepth 3 multipv 1 score cp 1095 nodes 597 nps 149250 hashfull 0 tbhits 0 time 4 pv b2e2
10:21:17.749 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - info depth 7 seldepth 6 multipv 1 score cp 76 nodes 1560 nps 222857 hashfull 0 tbhits 0 time 7 pv b2e2 c9e7 b0c2
10:21:17.760 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - info depth 8 seldepth 6 multipv 1 score cp 60 nodes 4139 nps 229944 hashfull 1 tbhits 0 time 18 pv h2e2 h9g7 h0g2 h7h5 i0h0 i9h9
10:21:17.774 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - info depth 9 seldepth 8 multipv 1 score cp 54 nodes 7331 nps 222151 hashfull 3 tbhits 0 time 33 pv h2e2 b9c7 h0g2 h7e7 i0h0 h9g7
10:21:17.799 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - info depth 10 seldepth 11 multipv 1 score cp 44 nodes 14722 nps 253827 hashfull 6 tbhits 0 time 58 pv h2e2 b9c7 h0g2 b7a7 i0h0 a9b9
10:21:17.799 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - bestmove h2e2 ponder b9c7
10:21:17.843 [main] DEBUG i.e.e.process.PikafishEngineProcess - sending to engine: stop
parsed engine result: InfoLineResult(depth=10, time=55, mate=null, cp=32, pv=[h2e2, h9g7, h0g2, c6c5, i0h0, i9h9, h0h4, b9c7, b0c2], line=info depth 10 seldepth 13 multipv 1 score cp 32 nodes 151567 nps 2755763 hashfull 45 tbhits 0 time 55 pv h2e2 h9g7 h0g2 c6c5 i0h0 i9h9 h0h4 b9c7 b0c2)
best move: h2e2
10:21:17.868 [main] DEBUG i.e.e.process.PikafishEngineProcess - sending to engine: quit
```

### xiangqi-core

Kotlin library providing a representation of a Chinese chess board. Supports both standard Xiangqi and the Manchu chess
(Yitong) variant. In Manchu chess, Red's starting position is
`W1BAKAB2 w - - 0 0` (full FEN: `rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/9/9/W1BAKAB2 w - - 0 0`), where `W`
is the super-chariot (Banner/统) that combines chariot, horse and cannon movement.

#### Example 1

```kotlin
import io.elephantchess.xiangqi.Board

fun main() {
    val board = Board()
    println(board.outputFen())
    println()
    println(board.print())

    println()
    println()

    board.registerMove("h2e2") // C2=5
    board.registerMove("h9g7") // H8+7
    println(board.outputFen())
    println()
    println(board.print())
}
```

outputs

```
rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0

   a b c d e f g h i
            
9  r n b a k a b n r
8  . . . . . . . . .
7  . c . . . . . c .
6  p . p . p . p . p
5  . . . . . . . . .
4  . . . . . . . . .
3  P . P . P . P . P
2  . C . . . . . C .
1  . . . . . . . . .
0  R N B A K A B N R


rnbakab1r/9/1c4nc1/p1p1p1p1p/9/9/P1P1P1P1P/1C2C4/9/RNBAKABNR w - - 0 1

   a b c d e f g h i
            
9  r n b a k a b . r
8  . . . . . . . . .
7  . c . . . . n c .
6  p . p . p . p . p
5  . . . . . . . . .
4  . . . . . . . . .
3  P . P . P . P . P
2  . C . . C . . . .
1  . . . . . . . . .
0  R N B A K A B N R
```

### xiangqi-core-test-utils

Test data for unit tests of `xiangqi-core`.

### seven-kingdoms-core

This is the logic for the [Seven Kingdoms](https://elephantchess.io/7k/about) xiangqi variant, which is still in
development.

## Libraries Usage

[![](https://www.jitpack.io/v/benckx/elephantchess.svg)](https://www.jitpack.io/#benckx/elephantchess)

At the moment, you can use the libraries via JitPack. You only need to add the JitPack repository to your build
file:

```Groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

Then you can use the dependencies:

```Groovy
implementation "com.github.benckx.elephantchess:xiangqi-core:1.1.3"
implementation "com.github.benckx.elephantchess:engine-api:1.1.3"
```

_Note: I still have to check that it still works with the updated Gradle project._

# Front-End

## HTML

As mentioned earlier in the [webapp-html-renderer](#webapp-html-renderer) section, HTML rendering relies on a custom
system of templating, based on tags `{{tag_name}}`.

Tags can be resolved from static fragments of HTML, simple values, dynamic renderers or something custom.

### Static HTML fragments

For example, the `base_css.html` file contains all the CSS files we want to link in basically all the HTML pages:

```html

<link rel="stylesheet" href="/css/style.css">
<link rel="stylesheet" href="/css/board.css">
<link rel="stylesheet" href="/css/style-reactive.css">
<link rel="preload" href="/css/play-bot-modal.css" as="style" onload="this.onload=null;this.rel='stylesheet'">
<link rel="preload" href="/css/new-game-modal.css" as="style" onload="this.onload=null;this.rel='stylesheet'">
```

It can be referred from another HTML fragment, in this case `header_init.html`, which the header bits we need in each
page:

```html
{{google_tag_manager_head}}
{{base_js}}
{{base_css}}
{{google_analytics}}
```

... which in turn can be resolved from a page:

```html

<head>
    {{header_init}}
    <title>About elephantchess.io</title>
    <link rel="stylesheet" href="/css/about.css"/>
    <script defer src="/js/default-base-page.js"></script>
</head>
```

... rendering the final HTML page with - among other things - all the CSS and JavaScript assets we need:

### Iterations

There is also the ability to inject key, values pairs and to repeat the pattern, like what we do for game thumbs:

```html
{{game_thumb}}[[iterations:3; {gameType: 'pvp', parentClass: 'latest-pvp-game-thumb'}]]
```

Where `i` is the iterated value (0, 1, 2), and the key, value pairs (`gameType` and `parentClass`) are injected into the
resolved content:

```html

<div class="game-thumb ${parentClass} game-thumb-placeholder">
    <div class="board-outer-container">
        <div class="board-inner-container">
            <a class="board-outer-container-link-mask"></a>
            <div id="last-${gameType}-game-board-${i}"
                 class="board-container mini-board-container board-container-placeholder">
            </div>
        </div>
        <!-- [...] -->
    </div>
</div>
```

## JavaScript

The front-end doesn't use any framework. It's only vanilla JavaScript with a few libraries (dayjs, ApexCharts,
vanillajs-datepicker, jsdiff).

Under `webapp/src/main/resources/public/js` there's usually a sub-folder for each "app", i.e.:

- `analysis-board`
- `player-vs-player`
- `player-vs-bot`
- `puzzles`
- etc.

Complex app like PvP is usually organized with a **page** which updates the GUI, a **controller** which connects to
WebSockets and/or calls REST endpoints, sometimes a DTO file and/or a separate REST client file.

## JavaScript Libraries

Some widgets from the [https://elephantchess.io](https://elephantchess.io) front-end are available as JavaScript
libraries. At the moment only board-gui is available. The plan would be to make the move-tree also available.

https://elephantchess.io/about/developers/board-gui-example

## Minification

The minification of JavaScript and CSS assets is done via a REST call to https://www.toptal.com. To avoid reminify the
same files, we keep track of the checksum of the input files in a local `minified_files.csv` file. The endpoint we use
is rate limited, so we minify chuck of 20 files every 90 seconds.

It's a bit of a funny approach but Gradle plugins I tried wouldn't support the JavaScript files since they contained
private class fields (i.e. ES6).
