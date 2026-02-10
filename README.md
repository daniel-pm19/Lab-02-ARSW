# Lab 02 — Concurrent Programming (ARSW)

Author: Daniel Patiño Mejía

Overview
--------
This repository contains two exercises from the ARSW course lab:

- Part I: a multithreaded "prime finder" demonstration using wait/notify and synchronized.
- Part II: a concurrent SnakeRace game where each snake is autonomous and the UI coordinates pausing and snapshotting.

Table of contents
-----------------
- Part I — Prime finder (warm-up)
- Part II — SnakeRace (concurrent core)
    - Concurrency analysis
    - Minimal fixes and critical regions
    - Safe UI control
    - Robustness under load
- How to build and run

Part I — Prime finder (warm-up)
----
The warm-up exercise implements a multithreaded prime-search program that periodically pauses all worker threads and reports how many primes have been found so far.

Key points:

- Threads compute primes over a range while the main controller periodically pauses them using wait()/notifyAll() and reports progress.
- The implementation uses Thread.sleep to drive the periodic pause, and locks + wait/notify to synchronise pausing and resuming.
- When the range is fully processed the program prints the final total of primes found.

Test screenshots:

![Prime finder progress](/img/image1.png)

![Prime finder snapshot](/img/image.png)

Part II — SnakeRace (concurrent core)
---
The project implements a simple Snake-like game where multiple snakes move autonomously on a shared board. The goal was to identify concurrency issues and apply minimal, safe changes that make the simulation correct and pausable without visual tearing.

Concurrency analysis
---

- How threads are used:
    - Each snake is controlled by a runner that advances it periodically. To avoid races when multiple snakes try to move simultaneously, board updates are protected by locks.

- Potential race conditions:
    - Two snakes could move into the same cell at the same time or try to eat the same mouse simultaneously.
    - Reading and modifying shared board collections without proper locking can produce inconsistent or lost updates.

- Unsafe collections and structures:
    - The board holds shared sets/maps (mice, obstacles, turbo and teleports). These are accessed and mutated by multiple threads, so they must be accessed under a lock.

- Busy-wait and timing issues:
    - Avoid tight polling loops with tiny sleeps. Use scheduled ticks or coordinated sleep intervals to reduce CPU waste.

Minimal fixes and critical regions
---
The following minimal changes were made to improve safety and consistency while keeping the design simple:

- Board.step() now performs the move computation and the call to snake.advance() while holding the board lock. This prevents interleaved moves that could cause inconsistent states.
- Board exposes a withLocked helper so the UI can capture a consistent snapshot while holding the same lock.
- GameClock.java uses a single thread scheduled executor to issue repaint ticks; pause or resume cancels and re-schedules the tick task.
- SnakeRunner.java was adjusted so a runner marks a snake as dead when it hits an obstacle and ignores further ticks for dead snakes.

These changes reduce the risk of race conditions without a heavy redesign.

Safe UI control
---
The UI must display a consistent snapshot when the user pauses the game. To achieve that we:

1. Pause the GameClock to stop scheduled repaints.
2. Use board.withLocked(...) to capture snapshots of all snakes while holding the board lock so no snake movement can interleave.
3. From that consistent snapshot we select:
     - the longest still-alive snake, and
     - the worst snake (the one that died first — earliest death timestamp).
4. The paused view then renders the worst snake (in orange) and the longest alive snake (in green) together, guaranteeing there is no tearing or half-updated frames.

One aspect to highlight is that the statement is contradictory because initially it doesn't say that the snakes die, and here they ask for a picture of the first snake to die, so it doesn't make much sense. What I did was change the rules so that when it collided with a wall, this snake would die.


Robustness under load
---
The implementation has been tested with many simultaneous snakes. With 20 snakes the program remains responsive thanks to the reduced critical regions and light-weight tick loops.

Example paused view with the worst + longest highlighted:

![Paused view: worst + longest](/img/image3.png)

Stress test: 20 snakes

![20 snakes stress test](/img/image2.png)

How to build and run
---
Requirements: JDK 21 and Maven.

SnakeRacePoint:

```bash
cd SnakeRace
mvn clean verify
mvn -q -DskipTests exec: java -Dsnakes=N
```
N is an arbitrary number of snakes

WaitNotifyExcercise:

```bash
cd wait-notify-excercise
mvn clean verify
mvn -q -DskipTests exec: java
```





