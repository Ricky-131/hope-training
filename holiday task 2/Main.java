import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.Timer;

interface Playable {
    File getAudioFile();
    String getDisplayName();
}

abstract class AudioEntity implements Playable {
    protected String id;
    protected String title;
    protected File file;

    public AudioEntity(String id, String title, File file) {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException();
        if (file == null || !file.exists()) throw new IllegalArgumentException();
        this.id = id;
        this.title = title;
        this.file = file;
    }

    public File getAudioFile() { return file; }
    public String getTitle() { return title; }
}

class Song extends AudioEntity {
    public Song(String id, String title, File file) {
        super(id, title, file);
    }
    @Override
    public String getDisplayName() { return title; }
}

class Playlist {
    private String name;
    private List<AudioEntity> tracks = new ArrayList<>();
    public Playlist(String name) { this.name = name; }
    public void addTrack(AudioEntity t) { tracks.add(t); }
    public List<AudioEntity> getTracks() { return tracks; }
}

public class Main {
    private static Clip clip;
    private static boolean isPlaying = false;
    private static boolean isRepeating = false;
    private static boolean isShuffled = false;
    private static boolean isDragging = false;
    private static Playlist mainPlaylist = new Playlist("All Songs");
    private static Stack<Integer> history = new Stack<>();
    private static int currentIndex = -1;
    private static JList<String> songList;
    private static JButton playPauseBtn;
    private static JButton repeatBtn;
    private static JButton shuffleBtn;
    private static JLabel nowPlayingLabel;
    private static JSlider progressBar;
    private static JSlider volumeBar;
    private static JLabel timeElapsedLabel;
    private static JLabel timeTotalLabel;
    private static CoverArtPanel coverArtPanel;
    private static byte[] audioBytes;
    private static AudioFormat audioFormat;
    private static float currentVolume = 0.8f;

    public static void main(String[] args) {
        loadAudioFiles();
        SwingUtilities.invokeLater(Main::createGUI);
    }

    private static void loadAudioFiles() {
        File audioDir = new File("audio");
        if (!audioDir.exists()) audioDir.mkdir();
        File[] files = audioDir.listFiles();
        if (files != null) {
            int id = 1;
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".wav")) {
                    String name = f.getName();
                    name = name.substring(0, name.length() - 4);
                    mainPlaylist.addTrack(new Song(String.valueOf(id++), name, f));
                }
            }
        }
    }

    private static void createGUI() {
        JFrame frame = new JFrame("Music Player Pro");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 700);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(new Color(15, 15, 18));

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(280, 0));
        leftPanel.setBackground(new Color(25, 25, 30));

        coverArtPanel = new CoverArtPanel();
        leftPanel.add(coverArtPanel, BorderLayout.NORTH);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (AudioEntity t : mainPlaylist.getTracks()) listModel.addElement(t.getDisplayName());

        songList = new JList<>(listModel);
        songList.setBackground(new Color(25, 25, 30));
        songList.setForeground(new Color(200, 200, 200));
        songList.setSelectionBackground(new Color(29, 185, 84));
        songList.setSelectionForeground(Color.WHITE);
        songList.setFont(new Font("SansSerif", Font.BOLD, 13));
        songList.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        songList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && songList.getSelectedIndex() != -1 && songList.getSelectedIndex() != currentIndex) {
                if (currentIndex != -1) history.push(currentIndex);
                currentIndex = songList.getSelectedIndex();
                playAudio(mainPlaylist.getTracks().get(currentIndex));
            }
        });

        JScrollPane scrollPane = new JScrollPane(songList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        leftPanel.add(scrollPane, BorderLayout.CENTER);
        frame.add(leftPanel, BorderLayout.WEST);

        frame.add(new VisualizerPanel(), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(20, 0));
        bottomPanel.setBackground(new Color(20, 20, 24));
        bottomPanel.setPreferredSize(new Dimension(0, 110));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(15, 30, 15, 30));

        nowPlayingLabel = new JLabel("Select a song");
        nowPlayingLabel.setForeground(Color.WHITE);
        nowPlayingLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        nowPlayingLabel.setPreferredSize(new Dimension(250, 30));
        bottomPanel.add(nowPlayingLabel, BorderLayout.WEST);

        JPanel centerPlayerPanel = new JPanel(new BorderLayout(0, 10));
        centerPlayerPanel.setOpaque(false);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        controls.setOpaque(false);

        Font iconFont = new Font("SansSerif", Font.PLAIN, 20);
        repeatBtn = createBtn("\u21BB", new Color(100, 100, 100), iconFont, 40);
        JButton prevBtn = createBtn("\u23EE", new Color(100, 100, 100), iconFont, 40);
        playPauseBtn = createBtn("\u25B6", new Color(29, 185, 84), new Font("SansSerif", Font.PLAIN, 22), 45);
        JButton skipBtn = createBtn("\u23ED", new Color(100, 100, 100), iconFont, 40);
        shuffleBtn = createBtn("\u21C4", new Color(100, 100, 100), iconFont, 40);

        playPauseBtn.addActionListener(e -> {
            if (clip != null && clip.isRunning()) {
                clip.stop();
                playPauseBtn.setText("\u25B6");
                isPlaying = false;
            } else if (clip != null && clip.getMicrosecondPosition() < clip.getMicrosecondLength()) {
                clip.start();
                playPauseBtn.setText("\u23F8");
                isPlaying = true;
            } else if (songList.getSelectedIndex() != -1) {
                if (currentIndex != -1) history.push(currentIndex);
                currentIndex = songList.getSelectedIndex();
                playAudio(mainPlaylist.getTracks().get(currentIndex));
            }
        });

        prevBtn.addActionListener(e -> {
            if (clip != null) {
                if (clip.getMicrosecondPosition() > 5000000L || history.isEmpty()) {
                    clip.setMicrosecondPosition(0);
                    if (!isPlaying) {
                        clip.start();
                        isPlaying = true;
                        playPauseBtn.setText("\u23F8");
                    }
                } else {
                    currentIndex = history.pop();
                    playAudio(mainPlaylist.getTracks().get(currentIndex));
                }
            }
        });

        skipBtn.addActionListener(e -> {
            if (currentIndex != -1) history.push(currentIndex);
            playNext();
        });

        repeatBtn.addActionListener(e -> {
            isRepeating = !isRepeating;
            repeatBtn.setForeground(isRepeating ? new Color(29, 185, 84) : Color.WHITE);
        });

        shuffleBtn.addActionListener(e -> {
            isShuffled = !isShuffled;
            shuffleBtn.setForeground(isShuffled ? new Color(29, 185, 84) : Color.WHITE);
        });

        controls.add(repeatBtn);
        controls.add(prevBtn);
        controls.add(playPauseBtn);
        controls.add(skipBtn);
        controls.add(shuffleBtn);
        centerPlayerPanel.add(controls, BorderLayout.NORTH);

        JPanel progressPanel = new JPanel(new BorderLayout(10, 0));
        progressPanel.setOpaque(false);
        
        progressBar = createCustomSlider(0, 1000, 0);
        progressBar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { isDragging = true; }
            public void mouseReleased(MouseEvent e) {
                isDragging = false;
                if (clip != null && clip.isOpen()) {
                    long newPos = (long) (clip.getMicrosecondLength() * (progressBar.getValue() / 1000.0));
                    clip.setMicrosecondPosition(newPos);
                }
            }
        });

        timeElapsedLabel = new JLabel("0:00");
        timeElapsedLabel.setForeground(new Color(170, 170, 170));
        timeElapsedLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        timeElapsedLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        timeElapsedLabel.setPreferredSize(new Dimension(40, 20));

        timeTotalLabel = new JLabel("0:00");
        timeTotalLabel.setForeground(new Color(170, 170, 170));
        timeTotalLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        timeTotalLabel.setHorizontalAlignment(SwingConstants.LEFT);
        timeTotalLabel.setPreferredSize(new Dimension(40, 20));
        
        progressPanel.add(timeElapsedLabel, BorderLayout.WEST);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(timeTotalLabel, BorderLayout.EAST);
        centerPlayerPanel.add(progressPanel, BorderLayout.SOUTH);
        bottomPanel.add(centerPlayerPanel, BorderLayout.CENTER);

        JPanel volPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 15));
        volPanel.setOpaque(false);
        volPanel.setPreferredSize(new Dimension(250, 40));
        JLabel volIcon = new JLabel("Vol");
        volIcon.setForeground(Color.WHITE);
        volIcon.setFont(new Font("SansSerif", Font.BOLD, 12));
        volumeBar = createCustomSlider(0, 100, 80);
        volumeBar.setPreferredSize(new Dimension(100, 20));
        volumeBar.addChangeListener(e -> {
            currentVolume = volumeBar.getValue() / 100f;
            updateVolume();
        });
        
        volPanel.add(volIcon);
        volPanel.add(volumeBar);
        bottomPanel.add(volPanel, BorderLayout.EAST);
        
        frame.add(bottomPanel, BorderLayout.SOUTH);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JButton createBtn(String text, Color bg, Font font, int size) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(font);
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setPreferredSize(new Dimension(size, size));
        return btn;
    }

    private static JSlider createCustomSlider(int min, int max, int val) {
        JSlider slider = new JSlider(min, max, val);
        slider.setOpaque(false);
        slider.setUI(new BasicSliderUI(slider) {
            @Override
            public void paintThumb(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(Color.WHITE);
                int size = 12;
                g2d.fillOval(thumbRect.x, thumbRect.y + (thumbRect.height / 2) - (size / 2), size, size);
            }
            @Override
            public void paintTrack(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(60, 60, 60));
                g2d.fillRoundRect(trackRect.x, trackRect.y + (trackRect.height / 2) - 2, trackRect.width, 4, 4, 4);
                g2d.setColor(new Color(29, 185, 84));
                g2d.fillRoundRect(trackRect.x, trackRect.y + (trackRect.height / 2) - 2, thumbRect.x - trackRect.x + 6, 4, 4, 4);
            }
        });
        return slider;
    }

    private static void updateVolume() {
        if (clip != null && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float min = gainControl.getMinimum();
            if (currentVolume == 0) {
                gainControl.setValue(min);
            } else {
                float dB = (float) (Math.log10(currentVolume) * 20.0);
                gainControl.setValue(Math.max(min, Math.min(gainControl.getMaximum(), dB)));
            }
        }
    }

    private static void playAudio(AudioEntity track) {
        if (clip != null) {
            clip.stop();
            clip.close();
        }
        try {
            AudioInputStream in = AudioSystem.getAudioInputStream(track.getAudioFile());
            audioFormat = in.getFormat();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            audioBytes = out.toByteArray();
            in.close();

            AudioInputStream stream = AudioSystem.getAudioInputStream(track.getAudioFile());
            clip = AudioSystem.getClip();
            clip.open(stream);
            updateVolume();
            clip.start();
            isPlaying = true;
            playPauseBtn.setText("\u23F8");
            
            songList.removeListSelectionListener(songList.getListSelectionListeners()[0]);
            songList.setSelectedIndex(currentIndex);
            songList.ensureIndexIsVisible(currentIndex);
            songList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && songList.getSelectedIndex() != -1 && songList.getSelectedIndex() != currentIndex) {
                    if (currentIndex != -1) history.push(currentIndex);
                    currentIndex = songList.getSelectedIndex();
                    playAudio(mainPlaylist.getTracks().get(currentIndex));
                }
            });

            nowPlayingLabel.setText(track.getDisplayName());
            coverArtPanel.setSong(track.getDisplayName());
            
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    if (clip.getMicrosecondPosition() >= clip.getMicrosecondLength()) {
                        if (isRepeating) {
                            clip.setMicrosecondPosition(0);
                            clip.start();
                        } else {
                            if (currentIndex != -1) history.push(currentIndex);
                            playNext();
                        }
                    }
                }
            });
        } catch (Exception e) {}
    }

    private static void playNext() {
        if (isRepeating) {
            isRepeating = false;
            repeatBtn.setForeground(Color.WHITE);
        }
        if (!mainPlaylist.getTracks().isEmpty()) {
            if (isShuffled && mainPlaylist.getTracks().size() > 1) {
                int next = currentIndex;
                while (next == currentIndex) {
                    next = new Random().nextInt(mainPlaylist.getTracks().size());
                }
                currentIndex = next;
            } else {
                currentIndex = (currentIndex + 1) % mainPlaylist.getTracks().size();
            }
            playAudio(mainPlaylist.getTracks().get(currentIndex));
        }
    }

    private static String formatTime(long microSeconds) {
        long totalSeconds = microSeconds / 1000000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    static class CoverArtPanel extends JPanel {
        private String songName = "";
        public CoverArtPanel() { setPreferredSize(new Dimension(280, 280)); }
        public void setSong(String name) { this.songName = name; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            if (songName.isEmpty()) {
                g2d.setColor(new Color(30, 30, 35));
                g2d.fillRect(0, 0, getWidth(), getHeight());
                return;
            }

            int hash = songName.hashCode();
            float h1 = Math.abs((hash % 360) / 360f);
            float h2 = Math.abs(((hash / 13) % 360) / 360f);
            
            GradientPaint gp = new GradientPaint(0, 0, Color.getHSBColor(h1, 0.8f, 0.8f), getWidth(), getHeight(), Color.getHSBColor(h2, 0.9f, 0.6f));
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, getWidth(), getHeight());

            g2d.setColor(new Color(255, 255, 255, 60));
            g2d.setStroke(new BasicStroke(15));
            g2d.drawOval(getWidth()/2 - 50, getHeight()/2 - 50, 100, 100);
            
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 50));
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString("\u266B", getWidth()/2 - fm.stringWidth("\u266B")/2, getHeight()/2 + fm.getAscent()/3);
        }
    }

    static class VisualizerPanel extends JPanel {
        private float[] values = new float[180];
        private float[] targets = new float[180];
        private List<Particle> particles = new ArrayList<>();
        private List<Ring> rings = new ArrayList<>();
        private Random rand = new Random();
        private float hueOffset = 0f;
        private float lastBass = 0f;

        class Particle {
            float x, y, vx, vy, life;
            Color color;
            Particle(float x, float y, float vx, float vy, Color color) {
                this.x = x; this.y = y; this.vx = vx; this.vy = vy;
                this.life = 1.0f; this.color = color;
            }
            void update() { x += vx; y += vy; life -= 0.02f; }
        }

        class Ring {
            float radius, maxRadius, life;
            Color color;
            Ring(float startR, float maxR, Color color) {
                this.radius = startR; this.maxRadius = maxR; this.color = color; this.life = 1.0f;
            }
            void update() { radius += (maxRadius - radius) * 0.1f; life -= 0.03f; }
        }

        public VisualizerPanel() {
            setBackground(new Color(12, 12, 15));
            new Timer(30, e -> {
                float currentBass = 0;
                float currentTreble = 0;
                hueOffset += 0.002f;
                if (hueOffset > 1f) hueOffset -= 1f;

                if (isPlaying && clip != null && audioBytes != null && audioFormat != null) {
                    int frame = clip.getFramePosition();
                    int fSize = audioFormat.getFrameSize();
                    int startByte = frame * fSize;
                    int step = (int)(audioFormat.getSampleRate() * 0.05) / values.length;
                    if (step < 1) step = 1;

                    int lastVal = 0;

                    for (int i = 0; i < values.length; i++) {
                        int maxAmp = 0;
                        int trebleAcc = 0;
                        for (int j = 0; j < step; j++) {
                            int idx = startByte + (i * step + j) * fSize;
                            if (idx >= 0 && idx + 1 < audioBytes.length) {
                                int val = (audioBytes[idx + 1] << 8) | (audioBytes[idx] & 0xFF);
                                short sample = (short) val;
                                maxAmp = Math.max(maxAmp, Math.abs(sample));
                                trebleAcc += Math.abs(sample - lastVal);
                                lastVal = sample;
                            }
                        }
                        targets[i] = maxAmp / 32768f;
                        values[i] += (targets[i] - values[i]) * 0.4f;
                        currentBass += values[i];
                        currentTreble += (trebleAcc / (float)step) / 65536f;
                    }
                    currentBass /= values.length;
                    currentTreble /= values.length;
                } else {
                    for (int i = 0; i < values.length; i++) values[i] += (0 - values[i]) * 0.2f;
                }

                int cx = getWidth() / 2;
                int cy = getHeight() / 2;

                if (currentBass > 0.22f && lastBass <= 0.20f) {
                    int r = Math.min(cx, cy) / 4;
                    rings.add(new Ring(r, r * 3, Color.getHSBColor(hueOffset, 0.7f, 1.0f)));
                }
                lastBass = currentBass;

                if (currentTreble > 0.05f || currentBass > 0.15f) {
                    int numSpawns = (int)(currentTreble * 150);
                    for(int i = 0; i < numSpawns; i++) {
                        float angle = rand.nextFloat() * 2 * (float)Math.PI;
                        float speed = 2f + currentTreble * 40f * rand.nextFloat();
                        float vx = (float)Math.cos(angle) * speed;
                        float vy = (float)Math.sin(angle) * speed;
                        float hue = (hueOffset + (currentTreble / (currentBass + 0.01f)) * 0.5f) % 1.0f;
                        Color c = Color.getHSBColor(hue, 0.8f, 1.0f);
                        particles.add(new Particle(cx, cy, vx, vy, c));
                    }
                }

                for (int i = particles.size() - 1; i >= 0; i--) {
                    Particle p = particles.get(i);
                    p.update();
                    if (p.life <= 0) particles.remove(i);
                }
                
                for (int i = rings.size() - 1; i >= 0; i--) {
                    Ring r = rings.get(i);
                    r.update();
                    if (r.life <= 0) rings.remove(i);
                }

                if (clip != null && clip.isOpen() && !isDragging) {
                    long pos = clip.getMicrosecondPosition();
                    long len = clip.getMicrosecondLength();
                    if (len > 0) {
                        progressBar.setValue((int) ((pos * 1000) / len));
                        timeElapsedLabel.setText(formatTime(pos));
                        timeTotalLabel.setText(formatTime(len));
                    }
                }
                repaint();
            }).start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            for (Ring r : rings) {
                g2d.setColor(new Color(r.color.getRed(), r.color.getGreen(), r.color.getBlue(), (int)(Math.max(0, r.life) * 100)));
                g2d.setStroke(new BasicStroke(2 + r.life * 5));
                g2d.drawOval(cx - (int)r.radius, cy - (int)r.radius, (int)r.radius * 2, (int)r.radius * 2);
            }

            for (Particle p : particles) {
                g2d.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int)(Math.max(0, p.life) * 200)));
                int size = (int)(2 + p.life * 5);
                g2d.fillOval((int)p.x - size / 2, (int)p.y - size / 2, size, size);
            }

            int baseRadius = (int)(Math.min(cx, cy) * 0.45);
            int n = values.length;
            int[] px = new int[n];
            int[] py = new int[n];
            float avg = 0;

            for (int i = 0; i < n; i++) {
                avg += values[i];
                double angle = 2 * Math.PI * i / n;
                double r = baseRadius + (values[i] * Math.min(cx, cy) * 0.25);
                px[i] = cx + (int) (Math.cos(angle) * r);
                py[i] = cy + (int) (Math.sin(angle) * r);
            }
            avg /= n;

            GeneralPath path = new GeneralPath();
            int startX = (px[n - 1] + px[0]) / 2;
            int startY = (py[n - 1] + py[0]) / 2;
            path.moveTo(startX, startY);

            for (int i = 0; i < n; i++) {
                int next = (i + 1) % n;
                int midX = (px[i] + px[next]) / 2;
                int midY = (py[i] + py[next]) / 2;
                path.quadTo(px[i], py[i], midX, midY);
            }
            path.closePath();

            Color waveColor = Color.getHSBColor(hueOffset, 0.8f, 0.9f);
            g2d.setColor(new Color(waveColor.getRed(), waveColor.getGreen(), waveColor.getBlue(), 80));
            g2d.fill(path);
            
            g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(waveColor);
            g2d.draw(path);

            int innerR = (int)(baseRadius * 0.90f + avg * baseRadius * 0.40f);
            g2d.setColor(new Color(15, 15, 18));
            g2d.fillOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
            g2d.setColor(Color.getHSBColor((hueOffset + 0.1f) % 1.0f, 0.4f, 1.0f));
            g2d.setStroke(new BasicStroke(2));
            g2d.drawOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
            
            g2d.setFont(new Font("SansSerif", Font.BOLD, innerR));
            FontMetrics fm = g2d.getFontMetrics();
            g2d.setColor(Color.WHITE);
            g2d.drawString("\u266B", cx - fm.stringWidth("\u266B") / 2, cy + fm.getAscent() / 3);
        }
    }
}