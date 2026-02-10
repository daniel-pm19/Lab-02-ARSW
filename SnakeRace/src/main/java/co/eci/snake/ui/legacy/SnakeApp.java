package co.eci.snake.ui.legacy;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class SnakeApp extends JFrame {

  private final Board board;
  private final GamePanel gamePanel;
  private final JButton actionButton;
  private final GameClock clock;
  private final java.util.List<Snake> snakes = new java.util.ArrayList<>();
  private java.util.concurrent.ExecutorService exec;
  private volatile boolean started = false;

  public SnakeApp() {
    super("The Snake Race");
    this.board = new Board(35, 28);

    int N = Integer.getInteger("snakes", 2);
    for (int i = 0; i < N; i++) {
      int x = 2 + (i * 3) % board.width();
      int y = 2 + (i * 2) % board.height();
      var dir = Direction.values()[i % Direction.values().length];
      snakes.add(Snake.of(x, y, dir));
    }

  this.gamePanel = new GamePanel(board, () -> snakes);
  this.actionButton = new JButton("Iniciar");

    setLayout(new BorderLayout());
    add(gamePanel, BorderLayout.CENTER);
    add(actionButton, BorderLayout.SOUTH);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);

    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint));

  // Do not start threads/clock automatically. Start when user clicks "Iniciar".
  actionButton.addActionListener((ActionEvent e) -> togglePause());

    gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "pause");
    gamePanel.getActionMap().put("pause", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        togglePause();
      }
    });

    var player = snakes.get(0);
    InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap am = gamePanel.getActionMap();
    im.put(KeyStroke.getKeyStroke("LEFT"), "left");
    im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
    im.put(KeyStroke.getKeyStroke("UP"), "up");
    im.put(KeyStroke.getKeyStroke("DOWN"), "down");
    am.put("left", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.LEFT);
      }
    });
    am.put("right", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.RIGHT);
      }
    });
    am.put("up", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.UP);
      }
    });
    am.put("down", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.DOWN);
      }
    });

    if (snakes.size() > 1) {
      var p2 = snakes.get(1);
      im.put(KeyStroke.getKeyStroke('A'), "p2-left");
      im.put(KeyStroke.getKeyStroke('D'), "p2-right");
      im.put(KeyStroke.getKeyStroke('W'), "p2-up");
      im.put(KeyStroke.getKeyStroke('S'), "p2-down");
      am.put("p2-left", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.LEFT);
        }
      });
      am.put("p2-right", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.RIGHT);
        }
      });
      am.put("p2-up", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.UP);
        }
      });
      am.put("p2-down", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.DOWN);
        }
      });
    }

    setVisible(true);
  }

  private void startGame() {
    if (started) return;
    started = true;
    exec = Executors.newVirtualThreadPerTaskExecutor();
    for (Snake s : snakes) {
      var runner = new SnakeRunner(s, board);
      exec.submit(() -> {
        while (!Thread.currentThread().isInterrupted()) {
          runner.tick(System.currentTimeMillis());
          try {
            Thread.sleep(10);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      });
    }
    clock.start();
    actionButton.setText("Pausar");
  }

  private void togglePause() {
    var text = actionButton.getText();
    if ("Iniciar".equals(text)) {
      // Start the game
      startGame();
      return;
    }

    if ("Pausar".equals(text)) {
      // Request pause and capture a consistent snapshot of the longest alive snake
      actionButton.setText("Reanudar");
      // pause clock first to stop scheduled repaints
      clock.pause();

      // Capture a stable snapshot while holding the board lock so snake steps
      // cannot interleave with our capture (Board.withLocked added).
      var pair = board.withLocked(() -> {
        java.util.List<Snake> sList = snakes;
        int bestIdx = -1;
        int bestSize = -1;
        int worstIdx = -1;
        long worstTime = Long.MAX_VALUE;
        java.util.List<java.util.List<Position>> snaps = new java.util.ArrayList<>();
        for (Snake s : sList) {
          var dq = s.snapshot();
          java.util.List<Position> copy = new java.util.ArrayList<>(dq);
          snaps.add(copy);
        }
        for (int i = 0; i < snaps.size(); i++) {
          Snake s = sList.get(i);
          int sz = snaps.get(i).size();
          if (s.isAlive()) {
            if (sz > bestSize) {
              bestSize = sz;
              bestIdx = i;
            }
          } else {
            long dt = s.diedAt();
            if (dt >= 0 && dt < worstTime) {
              worstTime = dt;
              worstIdx = i;
            }
          }
        }
        java.util.List<Position> best = (bestIdx >= 0 && bestSize > 0) ? snaps.get(bestIdx) : null;
        java.util.List<Position> worst = (worstIdx >= 0) ? snaps.get(worstIdx) : null;
        return java.util.Map.of("best", best, "worst", worst);
      });

      @SuppressWarnings("unchecked")
      java.util.List<Position> bestSnap = (java.util.List<Position>) pair.get("best");
      @SuppressWarnings("unchecked")
      java.util.List<Position> worstSnap = (java.util.List<Position>) pair.get("worst");

      gamePanel.setPausedSnapshots(bestSnap, worstSnap);
      // One last repaint on EDT to make sure UI shows the frozen state
      SwingUtilities.invokeLater(gamePanel::repaint);
    } else if ("Reanudar".equals(text)) {
      // Resume
      gamePanel.clearPausedLongestSnapshot();
      clock.resume();
      actionButton.setText("Pausar");
    }
  }

  public static final class GamePanel extends JPanel {
    private final Board board;
    private final Supplier snakesSupplier;
  private volatile java.util.List<Position> pausedLongestSnapshot;
  private volatile java.util.List<Position> pausedWorstSnapshot;
    private final int cell = 20;

    @FunctionalInterface
    public interface Supplier {
      List<Snake> get();
    }

    public GamePanel(Board board, Supplier snakesSupplier) {
      this.board = board;
      this.snakesSupplier = snakesSupplier;
      setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
      setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width(); x++)
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++)
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);

      // Obstáculos
      g2.setColor(new Color(255, 102, 0));
      for (var p : board.obstacles()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillRect(x + 2, y + 2, cell - 4, cell - 4);
        g2.setColor(Color.RED);
        g2.drawLine(x + 4, y + 4, x + cell - 6, y + 4);
        g2.drawLine(x + 4, y + 8, x + cell - 6, y + 8);
        g2.drawLine(x + 4, y + 12, x + cell - 6, y + 12);
        g2.setColor(new Color(255, 102, 0));
      }

      // Ratones
      g2.setColor(Color.BLACK);
      for (var p : board.mice()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 8, y + 8, cell - 16, cell - 16);
        g2.setColor(Color.BLACK);
      }

      // Teleports (flechas rojas)
      Map<Position, Position> tp = board.teleports();
      g2.setColor(Color.RED);
      for (var entry : tp.entrySet()) {
        Position from = entry.getKey();
        int x = from.x() * cell, y = from.y() * cell;
        int[] xs = { x + 4, x + cell - 4, x + cell - 10, x + cell - 10, x + 4 };
        int[] ys = { y + cell / 2, y + cell / 2, y + 4, y + cell - 4, y + cell / 2 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Turbo (rayos)
      g2.setColor(Color.BLACK);
      for (var p : board.turbo()) {
        int x = p.x() * cell, y = p.y() * cell;
        int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
        int[] ys = { y + 2, y + 2, y + 8, y + 8, y + 16, y + 10 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Serpientes
      java.util.List<Snake> snakes = snakesSupplier.get();

      if (pausedLongestSnapshot != null || pausedWorstSnapshot != null) {
        // Draw worst (first died) in red-orange below, then longest alive in green on top.
        if (pausedWorstSnapshot != null) {
          int i = 0;
          for (Position p : pausedWorstSnapshot) {
            int shade = Math.max(0, 40 - i * 4);
            Color base = new Color(255, 102, 0);
            g2.setColor(new Color(
                Math.min(255, base.getRed() + shade),
                Math.min(255, base.getGreen() + shade),
                Math.min(255, base.getBlue() + shade)));
            g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
            i++;
          }
        }

        if (pausedLongestSnapshot != null) {
          int i = 0;
          for (Position p : pausedLongestSnapshot) {
            int shade = Math.max(0, 40 - i * 4);
            Color base = new Color(0, 170, 0);
            g2.setColor(new Color(
                Math.min(255, base.getRed() + shade),
                Math.min(255, base.getGreen() + shade),
                Math.min(255, base.getBlue() + shade)));
            g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
            i++;
          }
        }
      } else {
        int idx = 0;
        for (Snake s : snakes) {
          Position[] body = s.snapshot().toArray(new Position[0]);
          for (int i = 0; i < body.length; i++) {
            Position p = body[i];
            Color base = (idx == 0) ? new Color(0, 170, 0) : new Color(0, 160, 180);
            int shade = Math.max(0, 40 - i * 4);
            g2.setColor(new Color(
                Math.min(255, base.getRed() + shade),
                Math.min(255, base.getGreen() + shade),
                Math.min(255, base.getBlue() + shade)));
            g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
          }
          idx++;
        }
      }
      g2.dispose();
    }

    public void setPausedSnapshots(java.util.List<Position> longest, java.util.List<Position> worst) {
      this.pausedLongestSnapshot = longest;
      this.pausedWorstSnapshot = worst;
      repaint();
    }

    public void clearPausedLongestSnapshot() {
      this.pausedLongestSnapshot = null;
      this.pausedWorstSnapshot = null;
      repaint();
    }
  }

  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}
