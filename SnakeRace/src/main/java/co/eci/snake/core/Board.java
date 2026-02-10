package co.eci.snake.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

public final class Board {
  private final int width;
  private final int height;

  private final Set<Position> mice = new HashSet<>();
  private final Set<Position> obstacles = new HashSet<>();
  private final Set<Position> turbo = new HashSet<>();
  private final Map<Position, Position> teleports = new HashMap<>();

  private final ReentrantLock lock = new ReentrantLock();

  public enum MoveResult { MOVED, ATE_MOUSE, HIT_OBSTACLE, ATE_TURBO, TELEPORTED }

  public Board(int width, int height) {
  if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
  this.width = width;
  this.height = height;

  lock.lock();
  try {
    for (int i = 0; i < 6; i++) mice.add(randomEmptyLocked());
    for (int i = 0; i < 4; i++) obstacles.add(randomEmptyLocked());
    for (int i = 0; i < 3; i++) turbo.add(randomEmptyLocked());
    createTeleportPairsLocked(2);
  } finally {
    lock.unlock();
  }
  }

  public int width() { return width; }
  public int height() { return height; }

  public Set<Position> mice() { 
    lock.lock();
    try {
      return new HashSet<Position>(mice);
    } finally {
        lock.unlock();
    }
  }
  public Set<Position> obstacles() {
    lock.lock();
    try {
        return new HashSet<Position>(obstacles);
    } finally {
        lock.unlock();
    }
  }
  public Set<Position> turbo() {
        lock.lock();
        try {
            return new HashSet<Position>(turbo);
        } finally {
            lock.unlock();
        }
    }
    
  public Map<Position, Position> teleports() {
        lock.lock();
        try {
            return new HashMap<Position, Position>(teleports);
        } finally {
            lock.unlock();
        }
    }

  public MoveResult step(Snake snake) {
    Objects.requireNonNull(snake, "snake");
    var head = snake.head();
    var dir = snake.direction();
    Position next = new Position(head.x() + dir.dx, head.y() + dir.dy).wrap(width, height);

    boolean ateMouse = false;
    boolean ateTurbo = false;
    boolean teleported = false;
    MoveResult result;
    
    lock.lock();

    try {
        if (obstacles.contains(next)) {
            return MoveResult.HIT_OBSTACLE;
        }

        if (teleports.containsKey(next)) {
            next = teleports.get(next);
            teleported = true;
        }

        ateMouse = mice.remove(next);
        ateTurbo = turbo.remove(next);

        if (ateMouse) {
            mice.add(randomEmptyLocked());
            obstacles.add(randomEmptyLocked());
            if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                turbo.add(randomEmptyLocked());
            }
        }

        if (ateTurbo) {
            result = MoveResult.ATE_TURBO;
        } else if (ateMouse) {
            result = MoveResult.ATE_MOUSE;
        } else if (teleported) {
            result = MoveResult.TELEPORTED;
        } else {
            result = MoveResult.MOVED;
        }
    } finally {
        lock.unlock();
    }

    snake.advance(next, ateMouse);

    return result;
  }

  private void createTeleportPairsLocked(int pairs) {
        for (int i = 0; i < pairs; i++) {
            Position a = randomEmptyLocked();
            Position b = randomEmptyLocked();
            teleports.put(a, b);
            teleports.put(b, a);
        }
  }

  private Position randomEmptyLocked() {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      Position p;
      int guard = 0;

      do {
          p = new Position(rnd.nextInt(width), rnd.nextInt(height));
          guard++;
          if (guard > width * height * 2) break;
      } while (mice.contains(p) || obstacles.contains(p) || turbo.contains(p) || teleports.containsKey(p));

      return p;
  }
}
