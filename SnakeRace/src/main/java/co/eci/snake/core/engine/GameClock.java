package co.eci.snake.core.engine;

import co.eci.snake.core.GameState;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class GameClock implements AutoCloseable {
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final long periodMillis;
  private final Runnable tick;
  private final java.util.concurrent.atomic.AtomicReference<GameState> state = new AtomicReference<>(GameState.STOPPED);
  
  private volatile java.util.concurrent.ScheduledFuture<?> future;
  
  public GameClock(long periodMillis, Runnable tick) {
    if (periodMillis <= 0) throw new IllegalArgumentException("periodMillis must be > 0");
    this.periodMillis = periodMillis;
    this.tick = java.util.Objects.requireNonNull(tick, "tick");
  }

  public void start() {
    if (state.compareAndSet(GameState.STOPPED, GameState.RUNNING)) {
      future = scheduler.scheduleAtFixedRate(tick, 0L, periodMillis, TimeUnit.MILLISECONDS);
    }
  }

  public void pause() {
    GameState prev = state.getAndSet(GameState.PAUSED);
    if (prev == GameState.RUNNING) {
        java.util.concurrent.ScheduledFuture<?> f = future;
        if (f != null) {
            f.cancel(false);
        }
    }
  }

  public void resume() {
    GameState prev = state.getAndSet(GameState.RUNNING);
    if (prev == GameState.PAUSED) {
      future = scheduler.scheduleAtFixedRate(tick, 0L, periodMillis, TimeUnit.MILLISECONDS);
    }
  }

  public void stop() {
    state.set(GameState.STOPPED);
    java.util.concurrent.ScheduledFuture<?> f = future;
    if (f != null) {
      f.cancel(false);
    }
  }

  @Override public void close() { scheduler.shutdownNow(); }
}
