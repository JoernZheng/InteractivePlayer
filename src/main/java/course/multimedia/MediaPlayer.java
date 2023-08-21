package course.multimedia;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MediaPlayer extends JFrame implements ActionListener {
    public static double frameRate = 30.0; // frames per second of the video

    // Components
    private Clip audioClip;
    private JLabel imageLabel;
    JLabel currentFrameLabel = new JLabel("0/0");
    private JButton playButton, pauseButton, stopButton;
    private JPanel sidebarPanel;

    // Variables
    private final List<BufferedImage> frames;
    private int frameIndex;
    private ScheduledExecutorService executor;
    private JPanel previousSelectedButtonBox = null;
    private final List<IndexRangeButton> indexRangeButtons = new ArrayList<>();
    private IndexRangeButton previousHighlightedButton = null;
    private final List<IndexRangeButtonBox> indexRangeButtonBoxes = new ArrayList<>();
    private IndexRangeButtonBox previousHighlightedButtonBox = null;

    // ==================== Dev Tools ====================
    private JTextArea frameInput;
    private JButton generateButton;
    private JPanel devToolPanel;
    private JScrollPane scrollPane;
    // ==================== Dev Tools ====================

    /***
     * Entrance to play video and audio synchronously
     * @param frames: List of frames
     * @param audioFile: Audio file
     * @param indexTree: Root->Scene->Shot->Sub-shot
     */
    public static void play(List<BufferedImage> frames, File audioFile, IndexTree indexTree) {
        // 创建并显示界面
        EventQueue.invokeLater(() -> {
            MediaPlayer player = new MediaPlayer(frames, audioFile, indexTree);
            player.setVisible(true);
        });
    }

    private static class IndexRangeButton extends JButton {
        int startIndex;
        int endIndex;
        int level;

        public IndexRangeButton(String text, int startIndex, int endIndex, int level) {
            super(text);
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.level = level;
        }

        public boolean isFrameInRange(int frameIndex) {
            return frameIndex >= startIndex && frameIndex <= endIndex;
        }
    }

    public class IndexRangeButtonBox extends JPanel {
        private JButton button;
        private int startIndex;
        private int endIndex;
        private int level;

        public IndexRangeButtonBox(JButton button, int startIndex, int endIndex, int level) {
            this.button = button;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.level = level;
            setLayout(new BorderLayout());
            add(button, BorderLayout.CENTER);
        }

        public JButton getButton() {
            return button;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }

        public int getLevel() {
            return level;
        }
    }


    public MediaPlayer(List<BufferedImage> frames, File audioFile, IndexTree indexTree) {
        this.frames = frames;
        initUI();
        initAudio(audioFile);
        generateSidebarButtons(indexTree.root, 1);
    }

    // 初始化界面
    private void initUI() {
        // 设置窗口属性
        setTitle("Video Player");
        setSize(1000, 600); // 增加窗口宽度，以容纳侧边栏
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // 初始化组件
        Container container = getContentPane();
        container.setLayout(new BorderLayout());

        // 创建侧边栏
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBackground(Color.WHITE);
        sidebarPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JScrollPane sidebarScrollPane = new JScrollPane(sidebarPanel);
        sidebarScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        sidebarScrollPane.setMinimumSize(new Dimension(200, 100));
        sidebarScrollPane.setMaximumSize(new Dimension(500, Integer.MAX_VALUE));

        // 创建右侧面板
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BorderLayout());
        rightPanel.setBackground(Color.WHITE);
        rightPanel.setMinimumSize(new Dimension(600, 0)); // 设置右侧面板的最小宽度

        // 创建分隔面板并添加侧边栏和右侧面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarScrollPane, rightPanel);
        splitPane.setOneTouchExpandable(false); // 隐藏面板分割线上的小箭头
        splitPane.setBackground(new Color(238, 238, 238));
        splitPane.setBorder(BorderFactory.createEmptyBorder(-1, -1, -1, -1));
        splitPane.setDividerSize(5);
        container.add(splitPane, BorderLayout.CENTER);

        // 创建视频播放区域
        imageLabel = new JLabel();
        if (!frames.isEmpty()) {
            imageLabel.setIcon(new ImageIcon(frames.get(0)));
        }
        imageLabel.setHorizontalAlignment(JLabel.CENTER);
        rightPanel.add(imageLabel, BorderLayout.CENTER);

        // 创建控制面板
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.CENTER)); // 修改布局为居中对齐的FlowLayout
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        playButton = new JButton("Play");
        playButton.setPreferredSize(new Dimension(playButton.getPreferredSize().width * 2, playButton.getPreferredSize().height * 2)); // 增大按钮大小
        playButton.addActionListener(this);
        controlPanel.add(playButton);

        pauseButton = new JButton("Pause");
        pauseButton.setPreferredSize(new Dimension(pauseButton.getPreferredSize().width * 2, pauseButton.getPreferredSize().height * 2)); // 增大按钮大小
        pauseButton.addActionListener(this);
        controlPanel.add(pauseButton);

        stopButton = new JButton("Stop");
        stopButton.setPreferredSize(new Dimension(stopButton.getPreferredSize().width * 2, stopButton.getPreferredSize().height * 2)); // 增大按钮大小
        stopButton.addActionListener(this);
        controlPanel.add(stopButton);

        rightPanel.add(controlPanel, BorderLayout.SOUTH);

        // 创建帧控制面板
        JPanel frameControlPanel = new JPanel();
        frameControlPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        frameControlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // 添加Frame Control标签
        JLabel frameControlLabel = new JLabel("Frame Control");
        frameControlPanel.add(frameControlLabel);

        // 添加后退5帧按钮
        JButton backward5Button = new JButton("<<5");
        backward5Button.addActionListener(e -> {
            pauseButton.doClick();
            setFrameAndSyncAudio(frameIndex - 5, false);
            updateFrameDisplay();
        });
        frameControlPanel.add(backward5Button);

        // 添加后退1帧按钮
        JButton backward1Button = new JButton("<<1");
        backward1Button.addActionListener(e -> {
            pauseButton.doClick();
            setFrameAndSyncAudio(frameIndex - 1, false);
            updateFrameDisplay();
        });
        frameControlPanel.add(backward1Button);

        frameControlPanel.add(currentFrameLabel);

        // 添加前进1帧按钮
        JButton forward1Button = new JButton(">>1");
        forward1Button.addActionListener(e -> {
            pauseButton.doClick();
            setFrameAndSyncAudio(frameIndex + 1, false);
            updateFrameDisplay();
        });
        frameControlPanel.add(forward1Button);

        // 添加前进5帧按钮
        JButton forward5Button = new JButton(">>5");
        forward5Button.addActionListener(e -> {
            pauseButton.doClick();
            setFrameAndSyncAudio(frameIndex + 5, false);
            updateFrameDisplay();
        });
        frameControlPanel.add(forward5Button);

        // 将帧控制面板添加到右侧面板
        rightPanel.add(frameControlPanel, BorderLayout.NORTH);

        updateFrameDisplay();

        // ==================== Dev Panel ====================
        // 创建Dev Tool面板
        devToolPanel = new JPanel();
        devToolPanel.setLayout(new BoxLayout(devToolPanel, BoxLayout.Y_AXIS));
        devToolPanel.setBackground(Color.WHITE);
        devToolPanel.setMinimumSize(new Dimension(200, 0));
        devToolPanel.setMaximumSize(new Dimension(500, Integer.MAX_VALUE));
        devToolPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 0));

        // 创建多行文本输入框
        frameInput = new JTextArea();
        frameInput.setLineWrap(true);
        frameInput.setWrapStyleWord(true);
        JScrollPane frameInputScrollPane = new JScrollPane(frameInput);
        frameInputScrollPane.setBorder(BorderFactory.createTitledBorder("Frames Input"));
        frameInputScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        devToolPanel.add(frameInputScrollPane);

        // 创建"生成"按钮
        generateButton = new JButton("Generate");
        generateButton.addActionListener(e -> {
            String inputText = frameInput.getText().trim();
            List<Integer> frameIndices = Arrays.stream(inputText.split("[^\\d]+"))
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
            generateDevToolButtons(frameIndices);
        });

        generateButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        devToolPanel.add(generateButton);

        scrollPane = new JScrollPane(devToolPanel);
        scrollPane.setPreferredSize(new Dimension(200, 0));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // 将Dev Tool面板添加到右侧面板
        // rightPanel.add(scrollPane, BorderLayout.EAST);
        // ==================== Dev Panel ====================
    }


    // 初始化音频
    private void initAudio(File audioFile) {
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            audioClip = AudioSystem.getClip();
            audioClip.open(audioStream);
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    // 处理按钮点击事件
    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == playButton) {
            play();
        } else if (source == pauseButton) {
            if (executor != null) {
                executor.shutdownNow();
            }
            audioClip.stop();
        } else if (source == stopButton) {
            stop();
        }
    }

    // 设置当前帧并同步音频
    private void setFrameAndSyncAudio(int targetFrame, boolean playAfterJump) {
        if (targetFrame >= 0 && targetFrame < frames.size()) {
            frameIndex = targetFrame;
            imageLabel.setIcon(new ImageIcon(frames.get(frameIndex)));

            // 计算目标帧对应的音频播放位置
            long targetMicrosecondPosition = (long) (targetFrame * (1000000.0 / frameRate));

            // 设置音频剪辑的播放位置
            audioClip.setMicrosecondPosition(targetMicrosecondPosition);

            if (playAfterJump) {
                play();
            } else {
                if (executor != null) {
                    executor.shutdownNow();
                }
                audioClip.stop();
            }
        }
    }

    // 根据IndexTree生成侧边栏按钮
    private void generateSidebarButtons(IndexTree.Section section, int level) {
        if (section == null) {
            return;
        }

        String buttonText;
        if (level == 1) {
            buttonText = "Scene";
        } else if (level == 2) {
            buttonText = "Shot";
        } else if (level == 3) {
            buttonText = "Subshot";
        } else {
            return;
        }

        int buttonIndex = 1;
        if (section.children == null || section.children.isEmpty()) {
            return;
        }

        for (IndexTree.Section childSection : section.children) {
            // IndexRangeButton button = new IndexRangeButton(buttonText + buttonIndex, childSection.startIndex, childSection.endIndex, level);
            JButton button = new JButton(buttonText + buttonIndex);
            // 设置按钮的样式
            button.setOpaque(true);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setFocusPainted(false);

            // 设置按钮字体大小
            button.setFont(new Font("Lucida Grande", Font.PLAIN, 14));
            button.setHorizontalAlignment(SwingConstants.LEFT);

            // 设置按钮宽度与左侧边栏宽度相同
            button.setMaximumSize(new Dimension(500, button.getPreferredSize().height));
            button.setMinimumSize(new Dimension(200, button.getPreferredSize().height));

            // 根据层次设置缩进
            int indent = (level - 1) * 30;
            button.setBorder(BorderFactory.createEmptyBorder(0, indent, 0, 0));

            // 创建一个新的面板来包裹按钮
            // JPanel buttonBox = new JPanel();
            IndexRangeButtonBox buttonBox = new IndexRangeButtonBox(button, childSection.startIndex, childSection.endIndex, level);
            buttonBox.setLayout(new BoxLayout(buttonBox, BoxLayout.X_AXIS));
            buttonBox.setBackground(Color.WHITE);
            buttonBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            buttonBox.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));
            buttonBox.add(button);
            indexRangeButtonBoxes.add(buttonBox);


            button.addActionListener(e -> {
                // 恢复上一个被点击的按钮的背景颜色
                // if (previousSelectedButtonBox != null) {
                //     // previousSelectedButtonBox.setContentAreaFilled(false);
                //     previousSelectedButtonBox.setBackground(Color.WHITE);
                // }

                setFrameAndSyncAudio(childSection.startIndex, true);
                updateFrameDisplay();

                // 设置被点击的按钮背景颜色
                // buttonBox.setBackground(new Color(238, 238, 238));
                // button.setContentAreaFilled(true);

                // 将当前按钮设置为上一个被点击的按钮
                // previousSelectedButtonBox = buttonBox;
            });

            // 添加一个鼠标适配器来处理按钮的背景颜色
            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    // buttonBox.setContentAreaFilled(true);
                    buttonBox.setBackground(new Color(238, 238, 238));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    // 当鼠标离开按钮时，如果按钮没有被点击，则恢复原来的背景颜色
                    if (previousSelectedButtonBox != buttonBox && buttonBox.getBackground() != Color.WHITE) {
                        // button.setContentAreaFilled(false);
                        buttonBox.setBackground(Color.WHITE);
                    }
                }
            });

            // indexRangeButtons.add(button);
            sidebarPanel.add(buttonBox);

            // 添加按钮之间的间距
            sidebarPanel.add(Box.createRigidArea(new Dimension(0, 8)));

            // 递归处理子节点
            generateSidebarButtons(childSection, level + 1);
            buttonIndex++;
        }
    }


    private void play() {
        if (executor != null) {
            executor.shutdownNow();
        }

        executor = Executors.newSingleThreadScheduledExecutor();

        long frameInterval = (long) (1000.0 / frameRate);

        executor.scheduleAtFixedRate(this::updateFrame, 0, frameInterval, TimeUnit.MILLISECONDS);

        audioClip.start();
    }

    private void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }

        audioClip.stop();
        setFrameAndSyncAudio(0, false);
        updateFrameDisplay(); // 更新帧显示
    }


    private void updateFrame() {
        int targetFrameIndex = getTargetFrameIndex(audioClip.getMicrosecondPosition());

        if (frameIndex < targetFrameIndex) {
            // 如果当前视频帧落后于音频时间戳，跳帧
            frameIndex = targetFrameIndex;
        } else if (frameIndex > targetFrameIndex) {
            // 如果当前视频帧超前于音频时间戳，等待（不进行渲染）
            return;
        }

        if (frameIndex < frames.size()) {
            imageLabel.setIcon(new ImageIcon(frames.get(frameIndex)));
            updateFrameDisplay(); // 更新帧显示
        } else {
            // 当视频播放完毕时，停止音频播放并关闭定时器
            audioClip.stop();
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }

    private int getTargetFrameIndex(long audioTimestamp) {
        return (int) (audioTimestamp * frameRate / 1000000.0);
    }

    // 更新帧显示方法
    private void updateFrameDisplay() {
        currentFrameLabel.setText(frameIndex + 1 + "/" + frames.size());
        highlightButtonBoxForCurrentFrame(frameIndex);
    }

    // ==================== Dev Tools Functions ====================
    // 根据输入的帧索引生成Dev Tool面板上的按钮
    private void generateDevToolButtons(List<Integer> frameIndices) {
        // 清除先前的按钮
        if (devToolPanel.getComponentCount() > 2) {
            devToolPanel.remove(2);
        }

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (int frameIndex : frameIndices) {
            JButton button = new JButton("Frame " + frameIndex);
            styleSidebarButton(button);
            button.addActionListener(e -> setFrameAndSyncAudio(frameIndex - 1, true));
            buttonPanel.add(button);
        }

        devToolPanel.add(buttonPanel);
        devToolPanel.revalidate();
        devToolPanel.repaint();
    }

    private void styleSidebarButton(JButton button) {
        button.setOpaque(true);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);

        // 设置按钮字体大小
        button.setFont(new Font("Lucida Grande", Font.PLAIN, 14));

        // 设置按钮边距
        button.setBorder(new EmptyBorder(5, 5, 5, 5));

        // 添加鼠标悬停事件
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(Color.BLUE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(Color.BLACK);
            }
        });
    }

    private void highlightButtonForCurrentFrame() {
        IndexRangeButton buttonToHighlight = null;

        for (IndexRangeButton button : indexRangeButtons) {
            if (button.isFrameInRange(frameIndex)) {
                if (buttonToHighlight == null || button.level > buttonToHighlight.level) {
                    buttonToHighlight = button;
                } else if (button.level == buttonToHighlight.level && button.startIndex > buttonToHighlight.startIndex) {
                    buttonToHighlight = button;
                }
            }
        }

        if (buttonToHighlight != null && previousHighlightedButton != buttonToHighlight) {
            if (previousHighlightedButton != null) {
                previousHighlightedButton.setBackground(Color.WHITE);
            }
            buttonToHighlight.setBackground(new Color(238, 238, 238));
            previousHighlightedButton = buttonToHighlight;
        }
    }

    private void highlightButtonBoxForCurrentFrame(int currentFrame) {
        // 获取当前帧所在的最低级别
        int highestLevel = Integer.MIN_VALUE;
        for (IndexRangeButtonBox buttonBox : indexRangeButtonBoxes) {
            if (buttonBox.getStartIndex() <= currentFrame && buttonBox.getEndIndex() >= currentFrame) {
                highestLevel = Math.max(highestLevel, buttonBox.getLevel());
            }
        }

        // 高亮最低级别的按钮
        for (IndexRangeButtonBox buttonBox : indexRangeButtonBoxes) {
            if (buttonBox.getStartIndex() <= currentFrame && buttonBox.getEndIndex() >= currentFrame) {
                if (buttonBox.getLevel() == highestLevel) {
                    buttonBox.setBackground(new Color(238, 238, 238));
                }
            } else {
                buttonBox.setBackground(Color.WHITE);
            }
        }
    }




    // public static void main(String[] args) {
    //     File audioFile = new File("/Users/zhengyaowen/Downloads/Demo-576/Ready_Player_One_rgb/InputAudio.wav");
    //     // Create an all black frame
    //     BufferedImage blackFrame = new BufferedImage(480, 270, BufferedImage.TYPE_INT_RGB);
    //     Graphics2D graphics = blackFrame.createGraphics();
    //     graphics.setColor(Color.BLACK);
    //     graphics.fillRect(0, 0, blackFrame.getWidth(), blackFrame.getHeight());
    //
    //     // 创建并显示界面
    //     EventQueue.invokeLater(() -> {
    //         // 创建一个空的帧列表和一个空的索引树
    //         List<BufferedImage> emptyFrames = new ArrayList<>();
    //         emptyFrames.add(blackFrame);
    //         IndexTree indexTree = IndexTree.getMockIndexTree();
    //
    //         MediaPlayer player = new MediaPlayer(emptyFrames, audioFile, indexTree);
    //         player.setVisible(true);
    //     });
    // }
}
