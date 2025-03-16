# About

[elephantchess.io](https://elephantchess.io) is a web application to play and study Chinese chess (or xiangqi 象棋).

Feel free to create issues in this repo to report bugs or request feature changes. The
current [roadmap](https://github.com/users/benckx/projects/2/views/1) is also available on this repo.

As of now, the public repository only contains a couple of libraries developed for the backend. Those are under LGPL-3.0
license. The rest of the webapp code is not open source yet.

# Modules

## engine-api

Kotlin API to launch and communicate with chess engines running as system processes.

The entry point is the `EnginePool` service, which is a coroutine-safe pool of engine processes. It allows multiple
users to use the same engine process with different positions. For example,
on [elephantchess](https://elephantchess.io), multiple users can play against the bot with different depths. Their
queries are "queued", so multiple PvB games can happen concurrently (even though technically one process is really only
used by one user at a given time)

You can decide to run multiple engine processes of a given engine (by increase `poolSize`) - with 1 thread for each - if
you want to optimize for concurrency, or have fewer engine processes - with more threads for each - if you want to
optimize for responsiveness.

On [elephantchess](https://elephantchess.io) for example, each Kubernetes pod has one engine pool with one instance of
Pikafish and one instance of Fairy Stockfish, with one thread each (so the engine processes don't use more than one CPU
core and the rest of the app remains responsible, as each pod only has 2 CPU cores at the moment).

The `numberOfThreads` option is not managed in the `EnginePool` itself, but is simply passed along to the engine
process. In Pikafish for example, this command is `setoption name Threads value 8`. But you don't need to input that
command yourself, as it's abstracted away by the `engine-api` library.

The engines are queries with [FEN notation](https://en.wikipedia.org/wiki/Forsyth%E2%80%93Edwards_Notation). In xiangqi,
the starting position is encoded as `rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0`.

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
├── fairy-stockfish
└── pikafish
    ├── 2022-12-26
    │         ├── pikafish-modern
    │         └── pikafish.nnue
    ├── 2023-02-16
    │         ├── pikafish-modern
    │         └── pikafish.nnue
    └── 2023-03-05
        ├── pikafish-modern
        └── pikafish.nnue

4 directories, 7 files
```

You can create your own `EngineProcessLocator`. For example - on [elephantchess](https://elephantchess.io) - we have a
Dockerized version:

```kotlin
object DockerizedProcessLocator : EngineProcessLocator {

    override fun launchCommand(binFileName: String) =
        "/bin/bash -lc /app/engines/$binFileName"

}
```

Pikafish binaries can be found at https://github.com/official-pikafish/Pikafish/releases. Versions posterior to
2023-03-05 contains a number of binaries that I don't know how to use, so as of
now [elephantchess](https://elephantchess.io) uses Pikafish 2023-03-05.

Fairy Stockfish binaries can be found at https://github.com/fairy-stockfish/Fairy-Stockfish/releases. As of now we only
used version 11.2; so it's not versioned in the `engines` folder.

### Example 1

```kotlin
import io.elephantchess.engines.process.EngineConfig
import io.elephantchess.engines.process.PikafishEngineId
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors.newFixedThreadPool

fun main() {
    val engineConfig = EngineConfig("2022-12-26", poolSize = 1, numberOfThreads = 8)
    val enginePool = EnginePool(mapOf(PikafishEngineId to engineConfig), newFixedThreadPool(2))

    runBlocking {
        val fen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0"
        val infoLinesResult = enginePool.queryForDepth(fen, PikafishEngineId, 10)
        val infoLineResult = infoLinesResult?.deepestResult()
        println("engine result: ${infoLineResult?.line}")
        println("best move: ${infoLineResult?.pv?.first()}")
    }

    enginePool.close()
}
```

outputs

```
10:21:17.168 [pool-1-thread-1] INFO  i.e.e.process.PikafishEngineProcess - running Pikafish engine, launching ./engines/pikafish/2022-12-26/pikafish-modern
10:21:17.199 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - sending to engine: setoption name Threads value 1
10:21:17.200 [pool-1-thread-1] DEBUG i.e.e.process.PikafishEngineProcess - Pikafish 2022-12-26 by the Pikafish developers (see AUTHORS file)
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
engine result: info depth 10 seldepth 11 multipv 1 score cp 44 nodes 14722 nps 253827 hashfull 6 tbhits 0 time 58 pv h2e2 b9c7 h0g2 b7a7 i0h0 a9b9
best move: h2e2
10:21:17.868 [main] DEBUG i.e.e.process.PikafishEngineProcess - sending to engine: quit
```

## xiangqi - core

Kotlin library providing a representation of a Chinese chess board .

### Example 1

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

## xiangqi-core-test-utils

Test data for unit tests of `xiangqi-core`.

# Libraries Usage

At the moment, you can use the libraries via JitPack. You only need to add the JitPack repository to your build.gradle
file:

```Groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

Then you can use the dependencies:

```Groovy
implementation "com.github.benckx.elephantchess:engine-api:1.1.0"
implementation "com.github.benckx.elephantchess:xiangqi-core:1.1.0"
```
