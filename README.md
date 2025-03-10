# About

[elephantchess.io](https://elephantchess.io) is a web application to play and study Chinese chess (or xiangqi 象棋).

Feel free to create issues in this repo to report bugs or request feature changes. The
current [roadmap](https://github.com/users/benckx/projects/2/views/1) is also available on this repo.

As of now, the public repository only contains a couple of libraries developed for the backend. Those are under LGPL-3.0
license. The rest of the webapp code is not open source yet.

# Modules

## xiangqi-core

Kotlin library providing a representation of a Chinese chess board.

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

### Output 1

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
implementation "com.github.benckx.elephantchess:xiangqi-core:1.0.7"
```
