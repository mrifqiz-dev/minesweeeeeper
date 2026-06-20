package com.zetcode;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

public class Board extends JPanel {
    private boolean isFirstClick;
    private final int NUM_IMAGES = 13;

    private final int DRAW_MINE = 9;
    private final int DRAW_COVER = 10;
    private final int DRAW_MARK = 11;
    private final int DRAW_WRONG_MARK = 12;

    private Cell[][] grid;
    private boolean inGame;
    private int minesLeft;
    private Image[] img;

    private GameConfig.Difficulty difficulty;
    private GameConfig.Mode mode;

    private int timeElapsed;
    private Timer gameTimer;
    private Timer vfxTimer;
    private final JLabel statusbar;

    private int offsetX = 0;
    private int offsetY = 0;
    private int scaledSize = 20;

    private boolean isShieldActive;
    private boolean isScannerActive;
    private int shieldCount;
    private int scannerCount;

    private List<MagicEffect> activeEffects;
    private List<BackgroundParticle> bgParticles;
    private float shieldPulseAlpha = 0f;
    private boolean shieldPulseUp = true;

    private Image bgImage; 

    private int hoverRow = -1;
    private int hoverCol = -1;

    private final Queue<FloodNode> floodQueue = new ArrayDeque<>();
    private final Set<Long> floodScheduled = new LinkedHashSet<>();

    private float endGameAlpha = 0f;       
    private boolean endGameWon = false;
    private boolean endGameFading = false;
    
    // Leaderboard Input States
    private boolean waitingForName = false;
    private StringBuilder playerName = new StringBuilder();

    public Board(JLabel statusbar) {
        this.statusbar = statusbar;
        this.difficulty = GameConfig.Difficulty.EASY;
        this.mode = GameConfig.Mode.FANTASY;
        this.activeEffects = new ArrayList<>();
        this.bgParticles = new ArrayList<>();
        initBoard();
    }

    private void initBoard() {
        setFocusable(true);
        setBackground(new Color(15, 18, 22));

        img = new Image[NUM_IMAGES];
        for (int i = 0; i < NUM_IMAGES; i++) {
            var path = "/resources/" + i + ".png";
            java.net.URL imgUrl = getClass().getResource(path);

            if (imgUrl != null) {
                img[i] = new ImageIcon(imgUrl).getImage();
            } else {
                System.err.println("[ERROR] Asset not found at: " + path);
                java.net.URL fallbackUrl = getClass().getResource("/" + i + ".png");
                if (fallbackUrl != null) {
                    img[i] = new ImageIcon(fallbackUrl).getImage();
                }
            }
        }

        initParticles();
        initTimers();
        addMouseListener(new MinesAdapter());
        addMouseMotionListener(new HoverAdapter());
        addKeyListener(new FantasyKeyAdapter());
        newGame();
    }

    private void initParticles() {
        Random rand = new Random();
        bgParticles.clear();
        for (int i = 0; i < 75; i++) {
            bgParticles.add(new BackgroundParticle(rand.nextInt(2000), rand.nextInt(1000)));
        }
        
        java.net.URL bgUrl = getClass().getResource("/resources/bg_fantasy.png");
        if (bgUrl != null) {
            bgImage = new ImageIcon(bgUrl).getImage();
        } else {
            System.err.println("[ERROR] Background image bg_fantasy.png not found!");
        }
    }

    private void initTimers() {
        gameTimer = new Timer(1000, e -> {
            if (inGame) {
                timeElapsed++;
                updateStatusDisplay();
            }
        });

        vfxTimer = new Timer(16, e -> {
            updateEffects();
            updateParticles();
            updateShieldPulse();
            updateFloodQueue();
            updateEndGameFade();
            repaint();
        });
        vfxTimer.start();
    }

    private void updateEffects() {
        Iterator<MagicEffect> it = activeEffects.iterator();
        while (it.hasNext()) {
            MagicEffect effect = it.next();
            effect.update();
            if (effect.isDead()) it.remove();
        }
    }

    private void updateParticles() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        for (BackgroundParticle p : bgParticles) {
            p.update(w, h);
        }
    }

    private void updateShieldPulse() {
        if (isShieldActive) {
            shieldPulseAlpha += shieldPulseUp ? 0.02f : -0.02f;
            if (shieldPulseAlpha >= 0.6f) shieldPulseUp = false;
            if (shieldPulseAlpha <= 0.1f) shieldPulseUp = true;
        } else {
            shieldPulseAlpha = 0f;
        }
    }

    private void updateFloodQueue() {
        if (floodQueue.isEmpty()) return;

        int revealed = 0;
        while (!floodQueue.isEmpty() && revealed < 3) {
            FloodNode node = floodQueue.peek();
            node.delay--;

            if (node.delay <= 0) {
                floodQueue.poll();
                Cell cell = node.cell;

                if (!cell.isRevealed() && !cell.isFlagged() && !cell.isMine()) {
                    cell.setRevealed(true);
                    triggerVFX(cell.getCol(), cell.getRow(), MagicEffect.Type.CELL_RIPPLE);
                    revealed++;

                    if (cell.getAdjacentMines() == 0) {
                        scheduleFloodFill(cell);
                    }
                }
            } else {
                break;
            }
        }

        if (floodQueue.isEmpty() && inGame) {
            checkVictory();
        }
    }

    private void updateEndGameFade() {
        if (endGameFading && endGameAlpha < 1f) {
            endGameAlpha += 0.02f;
            if (endGameAlpha > 1f) endGameAlpha = 1f;
        }
    }

    public void pauseGame() {
        if (gameTimer.isRunning()) gameTimer.stop();
    }

    public void resumeGame() {
        if (inGame && !gameTimer.isRunning()) gameTimer.start();
    }

    public void updateConfiguration(GameConfig.Difficulty diff, GameConfig.Mode m) {
        this.difficulty = diff;
        this.mode = m;
        newGame();
    }

    public GameConfig.Mode getGameMode() { return mode; }
    public GameConfig.Difficulty getDifficulty() { return difficulty; }
    public boolean isInGame() { return inGame; }

    public void newGame() {
        inGame = true;
        isFirstClick = true;
        minesLeft = difficulty.mines;
        timeElapsed = 0;
        gameTimer.stop();
        activeEffects.clear();

        isShieldActive = false;
        isScannerActive = false;
        shieldCount = 1;
        scannerCount = 1;

        floodQueue.clear();
        floodScheduled.clear();
        endGameAlpha = 0f;
        endGameFading = false;
        waitingForName = false;
        playerName.setLength(0);

        grid = new Cell[difficulty.rows][difficulty.cols];
        for (int r = 0; r < difficulty.rows; r++) {
            for (int c = 0; c < difficulty.cols; c++) {
                grid[r][c] = new Cell(r, c);
            }
        }

        for (int r = 0; r < difficulty.rows; r++) {
            for (int c = 0; c < difficulty.cols; c++) {
                Cell current = grid[r][c];
                for (int i = r - 1; i <= r + 1; i++) {
                    for (int j = c - 1; j <= c + 1; j++) {
                        if (isValidCell(i, j) && !(i == r && j == c)) {
                            current.addNeighbor(grid[i][j]);
                        }
                    }
                }
            }
        }

        updateStatusDisplay();
        repaint();
    }

    private void generateMines(int startRow, int startCol) {
        var random = new Random();
        int minesPlaced = 0;

        while (minesPlaced < difficulty.mines) {
            int r = random.nextInt(difficulty.rows);
            int c = random.nextInt(difficulty.cols);

            boolean isSafeZone = false;
            for (int i = startRow - 1; i <= startRow + 1; i++) {
                for (int j = startCol - 1; j <= startCol + 1; j++) {
                    if (r == i && c == j) {
                        isSafeZone = true;
                        break;
                    }
                }
            }

            if (!grid[r][c].isMine()) {
                grid[r][c].setMine(true);
                minesPlaced++;
            }
        }

        for (int r = 0; r < difficulty.rows; r++) {
            for (int c = 0; c < difficulty.cols; c++) {
                Cell cell = grid[r][c];
                if (!cell.isMine()) {
                    int count = 0;
                    for (Cell neighbor : cell.getNeighbors()) {
                        if (neighbor.isMine()) count++;
                    }
                    cell.setAdjacentMines(count);
                }
            }
        }
    }

    private boolean isValidCell(int r, int c) {
        return r >= 0 && r < difficulty.rows && c >= 0 && c < difficulty.cols;
    }

    private void revealCell(int r, int c) {
        if (!isValidCell(r, c)) return;
        Cell cell = grid[r][c];
        
        if (cell.isRevealed() || cell.isFlagged()) return;

        if (cell.isMine()) {
            if (mode == GameConfig.Mode.FANTASY && isShieldActive) {
                isShieldActive = false;
                cell.setFlagged(true);
                minesLeft--;
                triggerVFX(c, r, MagicEffect.Type.SHIELD_SHATTER);
                updateStatusDisplay();
                checkVictory();
                return;
            }
            cell.setRevealed(true);
            triggerVFX(c, r, MagicEffect.Type.EXPLOSION);
            endGame(false);
            return;
        }

        cell.setRevealed(true);
        triggerVFX(c, r, MagicEffect.Type.CELL_RIPPLE);

        if (cell.getAdjacentMines() == 0) {
            scheduleFloodFill(cell);
        } else {
            checkVictory();
        }
    }

    private void scheduleFloodFill(Cell origin) {
        for (Cell neighbor : origin.getNeighbors()) {
            if (!neighbor.isRevealed() && !neighbor.isFlagged() && !neighbor.isMine()) {
                long key = (long) neighbor.getRow() * 1000 + neighbor.getCol();
                if (floodScheduled.add(key)) {
                    floodQueue.offer(new FloodNode(neighbor, 2));
                }
            }
        }
    }

    private void applyScanner(int centerRow, int centerCol) {
        isScannerActive = false;
        triggerVFX(centerCol, centerRow, MagicEffect.Type.SCANNER);

        Cell center = grid[centerRow][centerCol];
        List<Cell> targetArea = new ArrayList<>(center.getNeighbors());
        targetArea.add(center);

        for (Cell cell : targetArea) {
            if (!cell.isRevealed()) {
                if (cell.isMine()) {
                    if (!cell.isFlagged()) {
                        cell.setFlagged(true);
                        minesLeft--;
                    }
                } else {
                    revealCell(cell.getRow(), cell.getCol());
                }
            }
        }

        updateStatusDisplay();
        checkVictory();
    }

    public void activateShield() {
        if (mode == GameConfig.Mode.FANTASY && shieldCount > 0 && !isShieldActive && inGame) {
            isShieldActive = true;
            shieldCount--;
            SoundManager.playSound("shield_on.wav"); 
            updateStatusDisplay();
        }
    }

    public void activateScanner() {
        if (mode == GameConfig.Mode.FANTASY && scannerCount > 0 && !isScannerActive && inGame) {
            isScannerActive = true;
            scannerCount--;
            updateStatusDisplay();
        }
    }

    private void triggerVFX(int gridX, int gridY, MagicEffect.Type type) {
        int centerX = offsetX + (gridX * scaledSize) + (scaledSize / 2);
        int centerY = offsetY + (gridY * scaledSize) + (scaledSize / 2);
        activeEffects.add(new MagicEffect(centerX, centerY, scaledSize, type));

        if (type == MagicEffect.Type.EXPLOSION) SoundManager.playSound("explosion.wav");
        else if (type == MagicEffect.Type.SCANNER) SoundManager.playSound("scanner.wav");
        else if (type == MagicEffect.Type.SHIELD_SHATTER) SoundManager.playSound("shield_break.wav");
        else if (type == MagicEffect.Type.FLAG_PLANT) SoundManager.playSound("flag.wav");
        else if (type == MagicEffect.Type.VICTORY) SoundManager.playSound("victory.wav");
        else if (type == MagicEffect.Type.CELL_RIPPLE) SoundManager.playRevealSound();
    }

    private void checkVictory() {
        int unrevealedSafeCells = 0;

        for (int r = 0; r < difficulty.rows; r++) {
            for (int c = 0; c < difficulty.cols; c++) {
                Cell cell = grid[r][c];
                if (!cell.isMine() && !cell.isRevealed()) {
                    unrevealedSafeCells++;
                }
            }
        }

        if (inGame && unrevealedSafeCells == 0) endGame(true);
    }

    private void endGame(boolean won) {
        inGame = false;
        gameTimer.stop();
        SoundManager.stopBGM(); 
        
        isShieldActive = false;
        endGameWon = won;
        endGameFading = true;

        if (won) {
            waitingForName = true;
            triggerVFX(difficulty.cols / 2,     difficulty.rows / 2,     MagicEffect.Type.VICTORY);
            triggerVFX(difficulty.cols / 4,     difficulty.rows / 4,     MagicEffect.Type.VICTORY);
            triggerVFX(difficulty.cols * 3 / 4, difficulty.rows / 4,     MagicEffect.Type.VICTORY);
            triggerVFX(difficulty.cols / 4,     difficulty.rows * 3 / 4, MagicEffect.Type.VICTORY);
            triggerVFX(difficulty.cols * 3 / 4, difficulty.rows * 3 / 4, MagicEffect.Type.VICTORY);
            statusbar.setText("VICTORY! Completion Time: " + timeElapsed + " seconds.");
        } else {
            statusbar.setText("GAME OVER! Survival Time: " + timeElapsed + " seconds.");
        }
    }

    private void updateStatusDisplay() {
        if (mode == GameConfig.Mode.FANTASY) {
            String shieldStatus = isShieldActive ? "[SHIELD ACTIVE] " : (shieldCount > 0 ? "[S: Shield] " : "[Shield Used] ");
            String scannerStatus = isScannerActive ? "[SCANNER READY] " : (scannerCount > 0 ? "[C: Scanner] " : "[Scanner Used] ");

            statusbar.setText(String.format(" Mode: %s | Flag: %d | Time: %ds | %s%s",
                    mode, minesLeft, timeElapsed, shieldStatus, scannerStatus));
        } else {
            statusbar.setText(String.format(" Mode: %s | Flag: %d | Time: %ds",
                    mode, minesLeft, timeElapsed));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (bgImage != null) {
            g2d.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
            g2d.setColor(new Color(15, 18, 22, 180));
            g2d.fillRect(0, 0, getWidth(), getHeight());
        } else {
            g2d.setColor(new Color(15, 18, 22));
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
        
        for (BackgroundParticle p : bgParticles) p.draw(g2d);

        int maxCellWidth = getWidth() / difficulty.cols;
        int maxCellHeight = getHeight() / difficulty.rows;
        scaledSize = Math.min(maxCellWidth, maxCellHeight);
        if (scaledSize < 20) scaledSize = 20;

        int totalBoardWidth = difficulty.cols * scaledSize;
        int totalBoardHeight = difficulty.rows * scaledSize;
        offsetX = (getWidth() - totalBoardWidth) / 2;
        offsetY = (getHeight() - totalBoardHeight) / 2;

        if (isShieldActive) {
            g2d.setColor(new Color(100, 200, 255, (int)(shieldPulseAlpha * 255)));
            g2d.setStroke(new BasicStroke(4));
            g2d.drawRect(offsetX - 5, offsetY - 5, totalBoardWidth + 10, totalBoardHeight + 10);
        }

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        for (int r = 0; r < difficulty.rows; r++) {
            for (int c = 0; c < difficulty.cols; c++) {
                Cell cell = grid[r][c];
                int imageIndex = DRAW_COVER;

                if (inGame) {
                    if (cell.isFlagged()) imageIndex = DRAW_MARK;
                    else if (cell.isRevealed()) imageIndex = cell.getAdjacentMines();
                } else {
                    if (cell.isMine()) imageIndex = cell.isFlagged() ? DRAW_MARK : DRAW_MINE;
                    else {
                        if (cell.isFlagged()) imageIndex = DRAW_WRONG_MARK;
                        else if (cell.isRevealed()) imageIndex = cell.getAdjacentMines();
                        else imageIndex = DRAW_COVER;
                    }
                }
                g2d.drawImage(img[imageIndex], offsetX + (c * scaledSize), offsetY + (r * scaledSize), scaledSize, scaledSize, this);
            }
        }

        drawHoverGlow(g2d);

        for (MagicEffect effect : activeEffects) {
            effect.draw(g2d);
        }

        drawEndGameOverlay(g2d);
    }

    private void drawHoverGlow(Graphics2D g2d) {
        if (!inGame || hoverRow < 0 || hoverCol < 0) return;
        if (!isValidCell(hoverRow, hoverCol)) return;
        if (grid[hoverRow][hoverCol].isRevealed()) return;

        int px = offsetX + (hoverCol * scaledSize);
        int py = offsetY + (hoverRow * scaledSize);

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
        g2d.setColor(new Color(100, 200, 255));
        g2d.fillRect(px, py, scaledSize, scaledSize);

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g2d.setColor(new Color(180, 230, 255));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRect(px, py, scaledSize, scaledSize);

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    private void drawEndGameOverlay(Graphics2D g2d) {
        if (!endGameFading && endGameAlpha <= 0f) return;

        float alpha = Math.min(endGameAlpha, 0.85f);

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.7f));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        if (endGameAlpha > 0.3f) {
            float panelAlpha = Math.min((endGameAlpha - 0.3f) / 0.6f, 1f);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, panelAlpha));

            int panelW = 480;
            int panelH = 260;
            int px = (getWidth() - panelW) / 2;
            int py = (getHeight() - panelH) / 2;

            g2d.setColor(new Color(20, 23, 28, 240));
            g2d.fillRoundRect(px, py, panelW, panelH, 20, 20);

            Color themeColor = endGameWon ? new Color(100, 255, 150) : new Color(255, 80, 80);

            g2d.setStroke(new BasicStroke(2.5f));
            g2d.setColor(themeColor);
            g2d.drawRoundRect(px, py, panelW, panelH, 20, 20);

            g2d.setStroke(new BasicStroke(1f));
            g2d.drawRoundRect(px + 10, py + 10, panelW - 20, panelH - 20, 15, 15);

            String title = endGameWon ? "DUNGEON CLEARED" : "YOU PERISHED";
            String subtitle = endGameWon ? "The arcane seal has been broken." : "The magic mine destroyed you.";
            
            g2d.setFont(new Font("Serif", Font.BOLD, 34));
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(title, px + (panelW - fm.stringWidth(title))/2, py + 60);

            g2d.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            g2d.setColor(Color.LIGHT_GRAY);
            fm = g2d.getFontMetrics();
            g2d.drawString(subtitle, px + (panelW - fm.stringWidth(subtitle))/2, py + 95);

            if (waitingForName) {
                String inputStr = playerName.toString() + (System.currentTimeMillis() % 1000 < 500 ? "_" : "");
                String prompt = "ENTER NAME: " + inputStr;
                g2d.setFont(new Font("Consolas", Font.BOLD, 22));
                g2d.setColor(new Color(100, 200, 255));
                fm = g2d.getFontMetrics();
                g2d.drawString(prompt, px + (panelW - fm.stringWidth(prompt))/2, py + 160);

                String action = "[ Press ENTER to Save Score ]";
                g2d.setFont(new Font("Consolas", Font.PLAIN, 14));
                g2d.setColor(Color.GRAY);
                fm = g2d.getFontMetrics();
                g2d.drawString(action, px + (panelW - fm.stringWidth(action))/2, py + 210);
            } else {
                String action = "[ Click Anywhere to Restart ]";
                g2d.setFont(new Font("Consolas", Font.BOLD, 14));
                g2d.setColor(new Color(224, 184, 114));
                fm = g2d.getFontMetrics();
                g2d.drawString(action, px + (panelW - fm.stringWidth(action))/2, py + 180);
            }
        }

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    private class HoverAdapter extends MouseMotionAdapter {
        @Override
        public void mouseMoved(MouseEvent e) {
            int relativeX = e.getX() - offsetX;
            int relativeY = e.getY() - offsetY;
            int newRow = relativeY / (scaledSize > 0 ? scaledSize : 1);
            int newCol = relativeX / (scaledSize > 0 ? scaledSize : 1);

            if (newRow != hoverRow || newCol != hoverCol) {
                hoverRow = newRow;
                hoverCol = newCol;
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            mouseMoved(e);
        }
    }

    private class MinesAdapter extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (waitingForName) return; 

            if (!inGame) {
                newGame();
                SoundManager.playBGM("game_bgm.wav"); 
                return;
            }

            int relativeX = e.getX() - offsetX;
            int relativeY = e.getY() - offsetY;
            int totalBoardWidth = difficulty.cols * scaledSize;
            int totalBoardHeight = difficulty.rows * scaledSize;

            if (relativeX < 0 || relativeX >= totalBoardWidth || relativeY < 0 || relativeY >= totalBoardHeight) return;

            int c = relativeX / scaledSize;
            int r = relativeY / scaledSize;
            if (!isValidCell(r, c)) return;

            if (!gameTimer.isRunning()) gameTimer.start();

            if (e.getButton() == MouseEvent.BUTTON3) {
                Cell cell = grid[r][c];
                if (!cell.isRevealed()) {
                    if (!cell.isFlagged() && minesLeft > 0) {
                        cell.setFlagged(true);
                        minesLeft--;
                        triggerVFX(c, r, MagicEffect.Type.FLAG_PLANT);
                    } else if (cell.isFlagged()) {
                        cell.setFlagged(false);
                        minesLeft++;
                    }
                    updateStatusDisplay();
                }
            } else if (e.getButton() == MouseEvent.BUTTON1) {
                if (isFirstClick) {
                    generateMines(r, c); // Buat bom SETELAH klik pertama diketahui
                    isFirstClick = false;
                }
                if (mode == GameConfig.Mode.FANTASY && isScannerActive) applyScanner(r, c);
                else revealCell(r, c);
            }
        }
    }

    private class FantasyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            if (waitingForName) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && playerName.length() > 0) {
                    waitingForName = false;
                    ScoreManager.saveScore(mode, difficulty, playerName.toString(), timeElapsed);
                } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && playerName.length() > 0) {
                    playerName.deleteCharAt(playerName.length() - 1);
                }
                repaint();
                return;
            }

            int key = e.getKeyCode();
            if (key == KeyEvent.VK_S) activateShield();
            else if (key == KeyEvent.VK_C) activateScanner();
        }

        @Override
        public void keyTyped(KeyEvent e) {
            if (waitingForName) {
                char c = e.getKeyChar();
                if (c >= 32 && c <= 126 && c != ',' && playerName.length() < 12) { 
                    playerName.append(c);
                }
                e.consume();
            }
        }
    }
}

class FloodNode {
    Cell cell;
    int delay;
    public FloodNode(Cell c, int d) {
        this.cell = c;
        this.delay = d;
    }
}

class Cell {
    private int row, col;
    private boolean isMine, isRevealed, isFlagged;
    private int adjacentMines;
    private List<Cell> neighbors; 

    public Cell(int row, int col) { 
        this.row = row; 
        this.col = col;
        this.isMine = false; 
        this.isRevealed = false; 
        this.isFlagged = false; 
        this.adjacentMines = 0; 
        this.neighbors = new ArrayList<>();
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public boolean isMine() { return isMine; }
    public void setMine(boolean mine) { isMine = mine; }
    public boolean isRevealed() { return isRevealed; }
    public void setRevealed(boolean revealed) { isRevealed = revealed; }
    public boolean isFlagged() { return isFlagged; }
    public void setFlagged(boolean flagged) { isFlagged = flagged; }
    public int getAdjacentMines() { return adjacentMines; }
    public void setAdjacentMines(int count) { this.adjacentMines = count; }
    
    public void addNeighbor(Cell neighbor) { this.neighbors.add(neighbor); }
    public List<Cell> getNeighbors() { return neighbors; }
}

class BackgroundParticle {
    double x, y, speed, size;
    float alpha, pulseSpeed, pulseAngle;
    Color color;

    public BackgroundParticle(double x, double y) {
        Random rand = new Random();
        this.x = x;
        this.y = y;
        this.speed = rand.nextDouble() * 1.5 + 0.5;
        this.size = rand.nextDouble() * 2.5 + 1.5;
        this.pulseAngle = (float) (rand.nextDouble() * Math.PI * 2);
        this.pulseSpeed = (float) (rand.nextDouble() * 0.04 + 0.02);

        if (rand.nextBoolean()) {
            this.color = new Color(224, 184, 114);
        } else {
            this.color = new Color(100, 200, 255);
        }
    }

    public void update(int screenWidth, int screenHeight) {
        y -= speed;
        pulseAngle += pulseSpeed;
        alpha = (float) (0.5 + 0.3 * Math.sin(pulseAngle));
        if (alpha > 1f) alpha = 1f;
        if (alpha < 0f) alpha = 0f;

        if (y < -10) {
            y = screenHeight + 10;
            x = new Random().nextInt(screenWidth);
        }
    }

    public void draw(Graphics2D g2d) {
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(color);
        g2d.fillOval((int) x, (int) y, (int) size, (int) size);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }
}

class VFXParticle {
    double x, y, vx, vy, life, maxLife, size, rot, rotSpeed;
    Color color;
    int shapeType;

    public VFXParticle(double x, double y, double vx, double vy, double life, double size, Color color, int shapeType) {
        this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        this.life = life; this.maxLife = life; this.size = size;
        this.color = color; this.shapeType = shapeType;
        this.rot = new Random().nextDouble() * Math.PI * 2;
        this.rotSpeed = (new Random().nextDouble() - 0.5) * 0.4;
    }

    public void update() {
        x += vx; y += vy;
        life--; rot += rotSpeed;
        if (shapeType == 1) vy += 0.15;
    }

    public void draw(Graphics2D g2d) {
        float alpha = (float) Math.max(0, life / maxLife);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(color);

        AffineTransform old = g2d.getTransform();
        g2d.translate(x, y);
        g2d.rotate(rot);

        if (shapeType == 0) {
            g2d.fillOval((int)-size/2, (int)-size/2, (int)size, (int)size);
        } else if (shapeType == 1) {
            Polygon p = new Polygon(new int[]{0, (int)size, (int)-size}, new int[]{(int)-size, (int)size, (int)size}, 3);
            g2d.fillPolygon(p);
        }
        g2d.setTransform(old);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }
}

class MagicEffect {
    enum Type { EXPLOSION, SCANNER, SHIELD_SHATTER, CELL_RIPPLE, FLAG_PLANT, VICTORY }

    private Type type;
    private int x, y, radius, maxRadius;
    private float ringAlpha = 1.0f;
    private List<VFXParticle> particles;

    public MagicEffect(int x, int y, int cellSize, Type type) {
        this.type = type; this.x = x; this.y = y;
        this.particles = new ArrayList<>();
        Random rand = new Random();

        if (type == Type.EXPLOSION) {
            this.maxRadius = cellSize * 2;
            for (int i = 0; i < 20; i++) {
                double angle = rand.nextDouble() * Math.PI * 2;
                double speed = rand.nextDouble() * 5 + 2;
                Color c = rand.nextBoolean() ? new Color(255, 60, 20) : new Color(255, 140, 0);
                particles.add(new VFXParticle(x, y, Math.cos(angle)*speed, Math.sin(angle)*speed, 40, rand.nextInt(10)+5, c, 1));
            }
        } else if (type == Type.SCANNER) {
            this.maxRadius = cellSize * 4;
            for (int i = 0; i < 30; i++) {
                double px = x + (rand.nextDouble() - 0.5) * maxRadius;
                double py = y + (rand.nextDouble() - 0.5) * maxRadius;
                particles.add(new VFXParticle(px, py, 0, rand.nextDouble() * -3 - 1, 60, 4, new Color(255, 215, 0), 0));
            }
        } else if (type == Type.SHIELD_SHATTER) {
            this.maxRadius = cellSize * 2;
            for (int i = 0; i < 15; i++) {
                double angle = rand.nextDouble() * Math.PI * 2;
                double speed = rand.nextDouble() * 8 + 3;
                particles.add(new VFXParticle(x, y, Math.cos(angle)*speed, Math.sin(angle)*speed, 35, rand.nextInt(8)+4, new Color(100, 200, 255), 1));
            }

        } else if (type == Type.CELL_RIPPLE) {
            this.maxRadius = cellSize;
            this.ringAlpha = 0.7f;

        } else if (type == Type.FLAG_PLANT) {
            this.maxRadius = cellSize;
            for (int i = 0; i < 8; i++) {
                double angle = -Math.PI / 2 + (rand.nextDouble() - 0.5) * Math.PI;
                double speed = rand.nextDouble() * 3 + 1.5;
                Color c = rand.nextBoolean() ? new Color(255, 215, 0) : new Color(255, 160, 50);
                particles.add(new VFXParticle(x, y, Math.cos(angle)*speed, Math.sin(angle)*speed, 25, rand.nextInt(4)+3, c, 0));
            }

        } else if (type == Type.VICTORY) {
            this.maxRadius = cellSize * 3;
            Color[] victoryColors = {
                    new Color(255, 215, 0),
                    new Color(100, 255, 120),
                    new Color(100, 200, 255),
                    new Color(255, 100, 200),
                    new Color(255, 120, 40),
                    new Color(200, 150, 255)
            };
            for (int i = 0; i < 40; i++) {
                double angle = rand.nextDouble() * Math.PI * 2;
                double speed = rand.nextDouble() * 7 + 3;
                Color c = victoryColors[rand.nextInt(victoryColors.length)];
                int shape = rand.nextBoolean() ? 0 : 1;
                particles.add(new VFXParticle(x, y, Math.cos(angle)*speed, Math.sin(angle)*speed,
                        50 + rand.nextInt(30), rand.nextInt(8)+4, c, shape));
            }
        }
    }

    public void update() {
        if (ringAlpha > 0) ringAlpha -= 0.05f;
        if (ringAlpha < 0) ringAlpha = 0;

        if (type == Type.SCANNER) radius += 15;
        else if (type == Type.CELL_RIPPLE) radius += 4;
        else radius += 8;

        Iterator<VFXParticle> it = particles.iterator();
        while (it.hasNext()) {
            VFXParticle p = it.next();
            p.update();
            if (p.life <= 0) it.remove();
        }
    }

    public boolean isDead() {
        return particles.isEmpty() && ringAlpha <= 0;
    }

    public void draw(Graphics2D g2d) {
        if (ringAlpha > 0) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ringAlpha));

            if (type == Type.EXPLOSION) {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(x - radius/2, y - radius/2, radius, radius);
            } else if (type == Type.SCANNER) {
                g2d.setColor(new Color(255, 215, 0));
                g2d.setStroke(new BasicStroke(20));
                g2d.drawOval(x - radius, y - radius, radius * 2, radius * 2);
            } else if (type == Type.SHIELD_SHATTER) {
                g2d.setColor(new Color(100, 200, 255));
                Polygon hex = createHexagon(x, y, radius);
                g2d.setStroke(new BasicStroke(3));
                g2d.drawPolygon(hex);

            } else if (type == Type.CELL_RIPPLE) {
                g2d.setColor(new Color(180, 230, 255));
                g2d.setStroke(new BasicStroke(1.2f));
                g2d.drawOval(x - radius, y - radius, radius * 2, radius * 2);
            } else if (type == Type.FLAG_PLANT) {
                g2d.setColor(new Color(255, 215, 0));
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawOval(x - radius/2, y - radius/2, radius, radius);
            } else if (type == Type.VICTORY) {
                g2d.setColor(new Color(255, 215, 0));
                g2d.setStroke(new BasicStroke(3f));
                g2d.drawOval(x - radius, y - radius, radius * 2, radius * 2);
            }
        }

        for (VFXParticle p : particles) p.draw(g2d);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        g2d.setStroke(new BasicStroke(1f));
    }

    private Polygon createHexagon(int cx, int cy, int r) {
        Polygon hex = new Polygon();
        for (int i = 0; i < 6; i++) {
            hex.addPoint((int) (cx + r * Math.cos(i * Math.PI / 3)), (int) (cy + r * Math.sin(i * Math.PI / 3)));
        }
        return hex;
    }
}

class GameConfig {
    public enum Difficulty {
        EASY(9, 9, 10), MEDIUM(16, 16, 40), HARD(16, 30, 75);
        public final int rows, cols, mines;
        Difficulty(int rows, int cols, int mines) { this.rows = rows; this.cols = cols; this.mines = mines; }
    }
    public enum Mode { CLASSIC, FANTASY }
}