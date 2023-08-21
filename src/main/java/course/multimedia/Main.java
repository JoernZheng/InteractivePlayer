package course.multimedia;

import nu.pattern.OpenCV;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.AudioFormat;


public class Main {
    public static final int WIDTH = 480; // width of the video frames
    public static final int HEIGHT = 270; // height of the video frames
    public static int FPS = 30; // frames per second of the video
    public static int BITS_PER_FRAME = WIDTH * HEIGHT * 3;// num of bits per frame
    public static int frameNum = -1;

    public static void main(String[] args) {
        OpenCV.loadLocally();
        System.out.println("OpenCV loaded successfully");

        String inputVideoPath = args[0];
        String inputAudioPath = args[1];

        // print jvm max memory
        System.out.println("Max memory: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + "M");

        File videoFile = new File(inputVideoPath);
        File audioFile = new File(inputAudioPath);

        List<BufferedImage> video = videoFrameExtractor(videoFile);
        List<byte[]> audio = audioFrameExtractor(audioFile);

        // read runtime memory status
        // System.out.println("Total memory: " + Runtime.getRuntime().totalMemory() / 1024 / 1024 + "M");
        // System.out.println("Free memory: " + Runtime.getRuntime().freeMemory() / 1024 / 1024 + "M");
        // System.out.println("Used memory: " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024 + "M");

        IndexTree indexTree = Indexer.index(inputVideoPath, video, audio);

        // IndexTree indexTree = IndexTree.getMockIndexTree();
        MediaPlayer.play(video, audioFile, indexTree);
    }

    public static List<BufferedImage> videoFrameExtractor(File file) {
        List<BufferedImage> list = new ArrayList<>();
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            FileChannel channel = raf.getChannel();
            // System.out.println("Channel size = " + channel.size());
            ByteBuffer buffer = ByteBuffer.allocate(WIDTH * HEIGHT * 3);
            frameNum = (int) (channel.size() / BITS_PER_FRAME);
            // System.out.println("numFrames = " + numFrames);
            for (int i = 0; i < frameNum; i++) {
                buffer.clear();
                channel.read(buffer);
                buffer.rewind();
                BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < HEIGHT; y++) {
                    for (int x = 0; x < WIDTH; x++) {
                        int r = buffer.get() & 0xff;
                        int g = buffer.get() & 0xff;
                        int b = buffer.get() & 0xff;
                        int rgb = (r << 16) | (g << 8) | b;
                        image.setRGB(x, y, rgb);
                    }
                }
                list.add(image);
            }
            channel.close();
            raf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return list;
    }

    // TODO：读取音频的字节流
    public static List<byte[]> audioFrameExtractor(File file) {
        List<byte[]> frames = new ArrayList<>();

        // Open audio file
        File audioFile = new File(file.getAbsolutePath());
        AudioInputStream audioInputStream = null;
        AudioFormat audioFormat = null;
        try {
            audioInputStream = AudioSystem.getAudioInputStream(audioFile);
            audioFormat = audioInputStream.getFormat();
        } catch (UnsupportedAudioFileException e) {
            System.out.println("Unsupported audio file: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Error reading audio file: " + e.getMessage());
        }

        if (audioFormat == null) {
            System.out.println("AudioInputStream or AudioFormat is null");
            return frames;
        }

        // 计算每段音频的大小
        long totalFrames = audioInputStream.getFrameLength();
        int frameSize = audioFormat.getFrameSize();
        float sampleRate = audioFormat.getSampleRate();

        // System.out.println("totalFrames = " + totalFrames);
        // System.out.println("frameSize = " + frameSize);
        // System.out.println("sampleRate = " + sampleRate);

        // 计算每帧视频对应的音频样本数
        int samplesPerVideoFrame = Math.round(sampleRate / FPS);

        // 计算每段音频的字节数
        int bytesPerSegment = samplesPerVideoFrame * frameSize;

        byte[] buffer = new byte[bytesPerSegment];
        int bytesRead;
        int frameReadCount = 0;

        // 读取音频数据并切分
        try {
            for (int i = 0; i < frameNum; i++) {
                bytesRead = audioInputStream.read(buffer, 0, bytesPerSegment);
                if (bytesRead > 0) {
                    byte[] segment = new byte[bytesPerSegment];
                    System.arraycopy(buffer, 0, segment, 0, bytesRead);
                    // 如果音频样本不足，则用静音填充
                    if (bytesRead < bytesPerSegment) {
                        ByteBuffer byteBuffer = ByteBuffer.wrap(segment);
                        byteBuffer.position(bytesRead);
                        for (int j = bytesRead; j < bytesPerSegment; j += frameSize) {
                            for (int k = 0; k < frameSize; k++) {
                                byteBuffer.put((byte) 0);
                            }
                        }
                    }
                    frames.add(segment);
                    frameReadCount += bytesRead;
                } else {
                    break;
                }
            }

            System.out.println("frameReadCount = " + frameReadCount / frameSize);

            audioInputStream.close();
        } catch (IOException e) {
            System.out.println("Error reading audio file: " + e.getMessage());
        }

        return frames;
    }
}