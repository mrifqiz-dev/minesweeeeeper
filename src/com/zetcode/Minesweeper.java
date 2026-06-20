package com.zetcode;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

public class Minesweeper extends JFrame {

    private CardLayout cardLayout;
    private JPanel mainContainer;
    private Board board;
    private JLabel statusbar;
    private FantasyButton btnContinue;
    private FantasyButton shieldBtn;
    private FantasyButton scannerBtn;

    private GameConfig.Mode pendingMode = GameConfig.Mode.FANTASY;

    public Minesweeper() {
        initUI();
    }

    private void initUI() {
        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        statusbar = new JLabel(" Awaiting configuration...");
        statusbar.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusbar.setBackground(new Color(15, 18, 22));
        statusbar.setForeground(new Color(224, 184, 114));
        statusbar.setOpaque(true);
        statusbar.setPreferredSize(new Dimension(0, 35));
        statusbar.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        board = new Board(statusbar);

        JPanel mainMenuPanel = createMainMenuPanel();
        JPanel difficultyPanel = createDifficultyPanel();
        JPanel gamePanel = createGamePanel();
        JPanel leaderboardPanel = createLeaderboardPanel();

        mainContainer.add(mainMenuPanel, "MENU");
        mainContainer.add(difficultyPanel, "DIFFICULTY");
        mainContainer.add(gamePanel, "GAME");
        mainContainer.add(leaderboardPanel, "LEADERBOARD");

        add(mainContainer);

        setResizable(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setTitle("Minesweeper - High Fantasy Edition");
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        SoundManager.playBGM("menu_bgm.wav");
    }

    private JPanel createMainMenuPanel() {
        MenuParticlePanel panel = new MenuParticlePanel();
        panel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(10, 10, 40, 10);

        JLabel titleLabel = new JLabel("MINESWEEPER");
        titleLabel.setFont(new Font("Serif", Font.BOLD, 55));
        titleLabel.setForeground(new Color(224, 184, 114));

        JLabel subtitleLabel = new JLabel("ARCANE REALM");
        subtitleLabel.setFont(new Font("Serif", Font.ITALIC, 24));
        subtitleLabel.setForeground(Color.LIGHT_GRAY);

        JPanel titlePanel = new JPanel(new GridBagLayout());
        titlePanel.setOpaque(false);
        GridBagConstraints tGbc = new GridBagConstraints();
        tGbc.gridx = 0; tGbc.gridy = 0; titlePanel.add(titleLabel, tGbc);
        tGbc.gridy = 1; titlePanel.add(subtitleLabel, tGbc);

        panel.add(titlePanel, gbc);

        gbc.gridy++; gbc.insets = new Insets(10, 10, 15, 10);
        FantasyButton btnNewGame = new FantasyButton("ENTER DUNGEON");
        btnNewGame.addActionListener(e -> cardLayout.show(mainContainer, "DIFFICULTY"));
        panel.add(btnNewGame, gbc);

        gbc.gridy++;
        btnContinue = new FantasyButton("RESUME JOURNEY");
        btnContinue.setEnabled(false);
        btnContinue.addActionListener(e -> {
            board.resumeGame();

            boolean isFantasy = (board.getGameMode() == GameConfig.Mode.FANTASY);
            shieldBtn.setVisible(isFantasy);
            scannerBtn.setVisible(isFantasy);

            SoundManager.playBGM("game_bgm.wav");
            cardLayout.show(mainContainer, "GAME");
            board.requestFocusInWindow();
        });
        panel.add(btnContinue, gbc);
        
        gbc.gridy++;
        FantasyButton btnLeaderboard = new FantasyButton("HALL OF FAME");
        btnLeaderboard.addActionListener(e -> cardLayout.show(mainContainer, "LEADERBOARD"));
        panel.add(btnLeaderboard, gbc);

        gbc.gridy++;
        FantasyButton btnExit = new FantasyButton("ABANDON QUEST");
        btnExit.addActionListener(e -> System.exit(0));
        panel.add(btnExit, gbc);

        return panel;
    }

    private JPanel createDifficultyPanel() {
        MenuParticlePanel panel = new MenuParticlePanel();
        panel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(10, 10, 20, 10);

        JLabel titleLabel = new JLabel("CHOOSE YOUR FATE");
        titleLabel.setFont(new Font("Serif", Font.BOLD, 36));
        titleLabel.setForeground(new Color(224, 184, 114));
        panel.add(titleLabel, gbc);

        gbc.gridy++; gbc.insets = new Insets(10, 10, 30, 10);
        FantasyButton btnToggleMode = new FantasyButton("MODE: " + pendingMode.name());
        btnToggleMode.setForeground(new Color(100, 200, 255));
        btnToggleMode.addActionListener(e -> {
            pendingMode = (pendingMode == GameConfig.Mode.FANTASY) ? GameConfig.Mode.CLASSIC : GameConfig.Mode.FANTASY;
            btnToggleMode.setText("MODE: " + pendingMode.name());
        });
        panel.add(btnToggleMode, gbc);

        gbc.gridy++; gbc.insets = new Insets(5, 10, 5, 10);
        FantasyButton btnEasy = new FantasyButton("APPRENTICE (9x9)");
        btnEasy.addActionListener(e -> startGame(GameConfig.Difficulty.EASY));
        panel.add(btnEasy, gbc);

        gbc.gridy++;
        FantasyButton btnMedium = new FantasyButton("MAGE (16x16)");
        btnMedium.addActionListener(e -> startGame(GameConfig.Difficulty.MEDIUM));
        panel.add(btnMedium, gbc);

        gbc.gridy++;
        FantasyButton btnHard = new FantasyButton("ARCHMAGE (30x16)");
        btnHard.addActionListener(e -> startGame(GameConfig.Difficulty.HARD));
        panel.add(btnHard, gbc);

        gbc.gridy++; gbc.insets = new Insets(30, 10, 10, 10);
        FantasyButton btnBack = new FantasyButton("RETURN");
        btnBack.addActionListener(e -> cardLayout.show(mainContainer, "MENU"));
        panel.add(btnBack, gbc);

        return panel;
    }

    private JPanel createGamePanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel sideHUD = new JPanel(new GridBagLayout());
        sideHUD.setPreferredSize(new Dimension(280, 0));
        sideHUD.setBackground(new Color(20, 23, 28));
        sideHUD.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(40, 45, 55)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new Insets(15, 15, 25, 15);

        JLabel title = new JLabel("GRIMOIRE");
        title.setFont(new Font("Serif", Font.BOLD, 22));
        title.setForeground(new Color(224, 184, 114));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        sideHUD.add(title, gbc);

        gbc.gridy++; gbc.insets = new Insets(8, 15, 8, 15);
        shieldBtn = new FantasyButton("Aegis Barrier (S)");
        shieldBtn.addActionListener(e -> { board.activateShield(); board.requestFocusInWindow(); });
        sideHUD.add(shieldBtn, gbc);

        gbc.gridy++;
        scannerBtn = new FantasyButton("Divine Revelation (C)");
        scannerBtn.addActionListener(e -> { board.activateScanner(); board.requestFocusInWindow(); });
        sideHUD.add(scannerBtn, gbc);

        gbc.gridy++; gbc.weighty = 1.0;
        JPanel spacer = new JPanel(); spacer.setOpaque(false);
        sideHUD.add(spacer, gbc);

        gbc.gridy++; gbc.weighty = 0; gbc.insets = new Insets(8, 15, 20, 15);
        FantasyButton menuBtn = new FantasyButton("MEDITATE (PAUSE)");
        menuBtn.addActionListener(e -> {
            board.pauseGame();
            if (board.isInGame()) btnContinue.setEnabled(true);
            SoundManager.playBGM("menu_bgm.wav");
            cardLayout.show(mainContainer, "MENU");
        });
        sideHUD.add(menuBtn, gbc);

        panel.add(board, BorderLayout.CENTER);
        panel.add(sideHUD, BorderLayout.EAST);
        panel.add(statusbar, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createLeaderboardPanel() {
        MenuParticlePanel panel = new MenuParticlePanel();
        panel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.insets = new Insets(10, 10, 30, 10);

        JLabel titleLabel = new JLabel("HALL OF FAME");
        titleLabel.setFont(new Font("Serif", Font.BOLD, 36));
        titleLabel.setForeground(new Color(224, 184, 114));
        panel.add(titleLabel, gbc);

        // Filters
        gbc.gridy++; gbc.gridwidth = 1; gbc.insets = new Insets(5, 5, 20, 5);
        
        GameConfig.Mode[] currentMode = { GameConfig.Mode.FANTASY };
        GameConfig.Difficulty[] currentDiff = { GameConfig.Difficulty.EASY };
        
        LeaderboardDisplay displayArea = new LeaderboardDisplay(currentMode[0], currentDiff[0]);

        FantasyButton btnModeFilter = new FantasyButton("MODE: " + currentMode[0].name());
        btnModeFilter.setForeground(new Color(100, 200, 255));
        btnModeFilter.setPreferredSize(new Dimension(200, 40));
        btnModeFilter.addActionListener(e -> {
            currentMode[0] = (currentMode[0] == GameConfig.Mode.FANTASY) ? GameConfig.Mode.CLASSIC : GameConfig.Mode.FANTASY;
            btnModeFilter.setText("MODE: " + currentMode[0].name());
            displayArea.updateFilters(currentMode[0], currentDiff[0]);
        });
        panel.add(btnModeFilter, gbc);

        gbc.gridx = 1;
        FantasyButton btnDiffFilter = new FantasyButton("DIFF: " + currentDiff[0].name());
        btnDiffFilter.setPreferredSize(new Dimension(200, 40));
        btnDiffFilter.addActionListener(e -> {
            if (currentDiff[0] == GameConfig.Difficulty.EASY) currentDiff[0] = GameConfig.Difficulty.MEDIUM;
            else if (currentDiff[0] == GameConfig.Difficulty.MEDIUM) currentDiff[0] = GameConfig.Difficulty.HARD;
            else currentDiff[0] = GameConfig.Difficulty.EASY;
            btnDiffFilter.setText("DIFF: " + currentDiff[0].name());
            displayArea.updateFilters(currentMode[0], currentDiff[0]);
        });
        panel.add(btnDiffFilter, gbc);

        gbc.gridx = 0; gbc.gridy++; gbc.gridwidth = 2;
        panel.add(displayArea, gbc);

        gbc.gridy++; gbc.insets = new Insets(30, 10, 10, 10);
        FantasyButton btnBack = new FantasyButton("RETURN");
        btnBack.addActionListener(e -> cardLayout.show(mainContainer, "MENU"));
        panel.add(btnBack, gbc);

        return panel;
    }

    private void startGame(GameConfig.Difficulty diff) {
        board.updateConfiguration(diff, pendingMode);
        boolean isFantasy = (pendingMode == GameConfig.Mode.FANTASY);
        shieldBtn.setVisible(isFantasy);
        scannerBtn.setVisible(isFantasy);
        btnContinue.setEnabled(true);
        SoundManager.playBGM("game_bgm.wav"); 
        cardLayout.show(mainContainer, "GAME");
        board.requestFocusInWindow();
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            var ex = new Minesweeper();
            ex.setVisible(true);
        });
    }
}

class LeaderboardDisplay extends JPanel {
    private GameConfig.Mode mode;
    private GameConfig.Difficulty diff;
    
    public LeaderboardDisplay(GameConfig.Mode mode, GameConfig.Difficulty diff) {
         this.mode = mode; this.diff = diff;
         setOpaque(false);
         setPreferredSize(new Dimension(420, 250));
    }
    
    public void updateFilters(GameConfig.Mode mode, GameConfig.Difficulty diff) {
         this.mode = mode; this.diff = diff;
         repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
         super.paintComponent(g);
         Graphics2D g2d = (Graphics2D) g;
         g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         
         g2d.setColor(new Color(20, 23, 28, 200));
         g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
         g2d.setColor(new Color(100, 200, 255));
         g2d.setStroke(new BasicStroke(1.5f));
         g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);

         List<PlayerRecord> records = ScoreManager.getScores(mode, diff);
         
         if (records.isEmpty()) {
             g2d.setFont(new Font("Consolas", Font.ITALIC, 16));
             g2d.setColor(Color.GRAY);
             String msg = "No records found for this category.";
             FontMetrics fm = g2d.getFontMetrics();
             g2d.drawString(msg, (getWidth() - fm.stringWidth(msg))/2, getHeight()/2);
             return;
         }
         
         g2d.setFont(new Font("Consolas", Font.BOLD, 20));
         int y = 45;
         for (int i = 0; i < Math.min(5, records.size()); i++) {
             PlayerRecord r = records.get(i);
             g2d.setColor(i == 0 ? new Color(255, 215, 0) : new Color(224, 184, 114));
             g2d.drawString(String.format("%d.", i + 1), 40, y);
             g2d.setColor(Color.LIGHT_GRAY);
             g2d.drawString(String.format("%-14s", r.name), 90, y);
             g2d.setColor(new Color(100, 255, 150));
             g2d.drawString(String.format("%d s", r.time), 300, y);
             y += 40;
         }
    }
}

class MenuParticlePanel extends JPanel {

    private final List<MenuParticle> particles = new ArrayList<>();
    private final List<MenuOrb> orbs = new ArrayList<>();
    private final Timer animTimer;
    private float titleGlowAngle = 0f;
    private Image bgImage;

    public MenuParticlePanel() {
        setBackground(new Color(15, 18, 22));
        setOpaque(true);

        URL bgUrl = getClass().getResource("/resources/bg_fantasy.png");
        if (bgUrl != null) {
            bgImage = new ImageIcon(bgUrl).getImage();
        }

        Random rand = new Random();

        for (int i = 0; i < 100; i++) {
            particles.add(new MenuParticle(rand.nextInt(2000), rand.nextInt(1200), rand));
        }

        for (int i = 0; i < 5; i++) {
            orbs.add(new MenuOrb(rand.nextInt(1600) + 100, rand.nextInt(900) + 100, rand));
        }

        animTimer = new Timer(16, e -> {
            updateAll();
            repaint();
        });
        animTimer.start();
    }

    private void updateAll() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        for (MenuParticle p : particles) p.update(w, h);
        for (MenuOrb o : orbs) o.update(w, h);
        titleGlowAngle += 0.03f;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (bgImage != null) {
            g2d.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
            g2d.setColor(new Color(15, 18, 22, 160));
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }

        for (MenuOrb o : orbs) o.draw(g2d);
        drawDecorLines(g2d);
        for (MenuParticle p : particles) p.draw(g2d);
    }

    private void drawDecorLines(Graphics2D g2d) {
        int cx = getWidth() / 2;
        int cy = getHeight() / 2 - 120;

        float alpha = (float)(0.25 + 0.15 * Math.sin(titleGlowAngle));
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(new Color(224, 184, 114));

        int lineW = 180;
        int gap = 20;
        g2d.fillRect(cx - lineW - gap, cy, lineW, 1);
        g2d.fillRect(cx + gap, cy, lineW, 1);

        g2d.fillOval(cx - lineW - gap - 3, cy - 2, 5, 5);
        g2d.fillOval(cx + gap + lineW - 2, cy - 2, 5, 5);

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }
}

class MenuParticle {
    double x, y, speed, size;
    float alpha, pulseAngle, pulseSpeed;
    Color color;

    public MenuParticle(double x, double y, Random rand) {
        this.x = x;
        this.y = y;
        this.speed = rand.nextDouble() * 1.2 + 0.4;
        this.size = rand.nextDouble() * 3.0 + 1.0;
        this.pulseAngle = (float)(rand.nextDouble() * Math.PI * 2);
        this.pulseSpeed = (float)(rand.nextDouble() * 0.03 + 0.015);

        int pick = rand.nextInt(3);
        if (pick == 0)      this.color = new Color(224, 184, 114);
        else if (pick == 1) this.color = new Color(100, 200, 255);
        else                this.color = new Color(180, 130, 255);
    }

    public void update(int w, int h) {
        y -= speed;
        pulseAngle += pulseSpeed;
        alpha = (float)(0.45 + 0.35 * Math.sin(pulseAngle));
        if (alpha > 1f) alpha = 1f;
        if (alpha < 0f) alpha = 0f;
        if (y < -10) {
            y = h + 10;
            x = new Random().nextInt(w);
        }
    }

    public void draw(Graphics2D g2d) {
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(color);
        g2d.fillOval((int)x, (int)y, (int)size, (int)size);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }
}

class MenuOrb {
    double x, y, vx, vy, radius;
    float pulseAngle, pulseSpeed;
    Color color;

    public MenuOrb(double x, double y, Random rand) {
        this.x = x;
        this.y = y;
        this.radius = rand.nextDouble() * 80 + 60;
        this.vx = (rand.nextDouble() - 0.5) * 0.4;
        this.vy = (rand.nextDouble() - 0.5) * 0.3;
        this.pulseAngle = (float)(rand.nextDouble() * Math.PI * 2);
        this.pulseSpeed = (float)(rand.nextDouble() * 0.015 + 0.008);

        int pick = rand.nextInt(3);
        if (pick == 0)      this.color = new Color(224, 184, 114);
        else if (pick == 1) this.color = new Color(40, 80, 160);
        else                this.color = new Color(60, 30, 100);
    }

    public void update(int w, int h) {
        x += vx;
        y += vy;
        pulseAngle += pulseSpeed;

        if (x - radius < 0 || x + radius > w) vx = -vx;
        if (y - radius < 0 || y + radius > h) vy = -vy;
    }

    public void draw(Graphics2D g2d) {
        float baseAlpha = (float)(0.04 + 0.03 * Math.sin(pulseAngle));
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, baseAlpha));
        g2d.setColor(color);
        g2d.fillOval((int)(x - radius), (int)(y - radius), (int)(radius * 2), (int)(radius * 2));
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }
}

class FantasyButton extends JButton {
    private final Color idleColor = new Color(30, 34, 40);
    private final Color hoverColor = new Color(45, 52, 64);
    private final Color clickColor = new Color(20, 23, 28);
    private final Color textColor = new Color(224, 184, 114);

    public FantasyButton(String text) {
        super(text);
        setFont(new Font("Segoe UI", Font.BOLD, 15));
        setForeground(textColor);
        setBackground(idleColor);
        setFocusPainted(false);
        setContentAreaFilled(false);
        setOpaque(true);
        setPreferredSize(new Dimension(300, 50));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(textColor, 1),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    setBackground(hoverColor);
                    setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color.WHITE, 1),
                            BorderFactory.createEmptyBorder(5, 15, 5, 15)
                    ));
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (isEnabled()) {
                    setBackground(idleColor);
                    setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(textColor, 1),
                            BorderFactory.createEmptyBorder(5, 15, 5, 15)
                    ));
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    setBackground(clickColor);
                    SoundManager.playSound("click.wav");
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (isEnabled()) setBackground(hoverColor);
            }
        });
    }

    @Override
    public void setEnabled(boolean b) {
        super.setEnabled(b);
        if (!b) {
            setForeground(Color.DARK_GRAY);
            setBackground(new Color(15, 18, 22));
            setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
        } else {
            setForeground(textColor);
            setBackground(idleColor);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(textColor, 1),
                    BorderFactory.createEmptyBorder(5, 15, 5, 15)
            ));
        }
    }
}

class ScoreManager {
    private static final String FILE_PATH = "leaderboard.txt";

    public static void saveScore(GameConfig.Mode mode, GameConfig.Difficulty diff, String name, int time) {
        try (FileWriter fw = new FileWriter(FILE_PATH, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(mode.name() + "," + diff.name() + "," + name + "," + time);
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to save score: " + e.getMessage());
        }
    }

    public static List<PlayerRecord> getScores(GameConfig.Mode mode, GameConfig.Difficulty diff) {
        List<PlayerRecord> records = new ArrayList<>();
        File file = new File(FILE_PATH);
        if (!file.exists()) return records;

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split(",");
                if (parts.length == 4) {
                    if (parts[0].equals(mode.name()) && parts[1].equals(diff.name())) {
                        records.add(new PlayerRecord(parts[2], Integer.parseInt(parts[3])));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to read scores: " + e.getMessage());
        }
        records.sort((a, b) -> Integer.compare(a.time, b.time));
        return records;
    }
}

class PlayerRecord {
    String name;
    int time;
    public PlayerRecord(String name, int time) { 
        this.name = name; 
        this.time = time; 
    }
}

class SoundManager {
    private static Clip bgmClip;
    private static long lastRevealTime = 0;

    public static void playSound(String fileName) {
        playSound(fileName, 0f);
    }

    public static void playSound(String fileName, float volumeOffset) {
        try {
            java.net.URL url = SoundManager.class.getResource("/resources/" + fileName);

            if (url == null) {
                java.io.File audioFile = new java.io.File("src/resources/" + fileName);
                if (!audioFile.exists()) {
                    System.err.println("[AUDIO MISSING] System could not find file: " + audioFile.getAbsolutePath());
                    return;
                }
                url = audioFile.toURI().toURL();
            }

            AudioInputStream audioIn = AudioSystem.getAudioInputStream(url);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float max = gainControl.getMaximum();
                float min = gainControl.getMinimum();
                if (volumeOffset > max) volumeOffset = max;
                if (volumeOffset < min) volumeOffset = min;
                gainControl.setValue(volumeOffset);
            }

            clip.start();
        } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
            System.err.println("[FORMAT ERROR] File '" + fileName + "' rejected by JVM. Required format: 16-bit PCM WAV.");
        } catch (Exception e) {
            System.err.println("[SYSTEM ERROR] Failed to play '" + fileName + "': " + e.getMessage());
        }
    }

    public static void playRevealSound() {
        long now = System.currentTimeMillis();
        if (now - lastRevealTime > 60) {
            playSound("reveal.wav", 6.0f);
            lastRevealTime = now;
        }
    }

    public static void playBGM(String fileName) {
        try {
            if (bgmClip != null && bgmClip.isRunning()) bgmClip.stop();

            java.net.URL url = SoundManager.class.getResource("/resources/" + fileName);

            if (url == null) {
                java.io.File audioFile = new java.io.File("src/resources/" + fileName);
                if (!audioFile.exists()) {
                    System.err.println("[BGM MISSING] System could not find BGM: " + audioFile.getAbsolutePath());
                    return;
                }
                url = audioFile.toURI().toURL();
            }

            AudioInputStream audioIn = AudioSystem.getAudioInputStream(url);
            bgmClip = AudioSystem.getClip();
            bgmClip.open(audioIn);
            bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
            System.err.println("[BGM FORMAT ERROR] File '" + fileName + "' rejected by JVM. Required format: 16-bit PCM WAV.");
        } catch (Exception e) {
            System.err.println("[BGM SYSTEM ERROR] Failed to play BGM '" + fileName + "': " + e.getMessage());
        }
    }

    public static void stopBGM() {
        if (bgmClip != null && bgmClip.isRunning()) {
            bgmClip.stop();
        }
    }
}