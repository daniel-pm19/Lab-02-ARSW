package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;

import java.util.concurrent.ThreadLocalRandom;

public final class SnakeRunner{
  private final Snake snake;
  private final Board board;
  private final int baseSleepMs = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;
  private long nextMoveAtMs = 0L;

  public SnakeRunner(Snake snake, Board board) {
    this.snake = snake;
    this.board = board;
  }

  public void tick(long nowMs) {
    // If snake is dead, don't move it anymore.
    if (!snake.isAlive()) return;

    if (nowMs < nextMoveAtMs) {
      return;
    }

    maybeTurn();

    Board.MoveResult res = board.step(snake);

    if (res == Board.MoveResult.HIT_OBSTACLE) {
      // Mark snake as dead instead of random-turning on obstacle.
      snake.die(nowMs);
      return;
    } else if (res == Board.MoveResult.ATE_TURBO) {
      turboTicks = 100;
    }

    int delay = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
    if (turboTicks > 0) {
      turboTicks--;
    }

    nextMoveAtMs = nowMs + delay;
    }

  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) randomTurn();
  }

  private void randomTurn() {
        Direction[] dirs = Direction.values();
        int idx = ThreadLocalRandom.current().nextInt(dirs.length);
        snake.turn(dirs[idx]);
    }
}
