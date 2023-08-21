package course.multimedia;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Indexer {
    public static final int WIDTH = 480; // width of the video frames
    public static final int HEIGHT = 270; // height of the video frames
    public static int FPS = 30; // frames per second of the video
    public static int FRAME_SIZE = 4;
    public static int SAMPLING_RATE = 44100;

    public static String pySceneDetectPath = "/opt/homebrew/Caskroom/miniforge/base/envs/scene-detection/bin/scenedetect";

    public static IndexTree index(String videoPath, List<BufferedImage> video, List<byte[]> audio) {
        String videoPathPrefix = videoPath.substring(0, videoPath.lastIndexOf("."));
        String[] segs = videoPathPrefix.split("/");
        String csvFilePath = segs[segs.length - 1] + "-Scenes.csv";
        String mp4FilePath = videoPathPrefix + ".mp4";

        runPySceneDetect(mp4FilePath);

        Indexer indexer = new Indexer();
        List<Integer> shotBoundaries = indexer.shotBoundaryDetection(csvFilePath, video, audio);
        // List<Integer> shotBoundaries = getReadyPlayerOneShots();
        List<Integer> sceneBoundaries = indexer.sceneClustering(video, shotBoundaries);
        Map<Integer, List<Integer>> subShots = indexer.subshotDetection(video, audio, shotBoundaries);

        return indexer.buildIndexTree(video.size(), shotBoundaries, sceneBoundaries, subShots);
    }

    public static void runPySceneDetect(String mp4FilePath) {
        String[] command = {
                pySceneDetectPath,
                "-i",
                mp4FilePath,
                "detect-adaptive",
                "list-scenes"
        };

        try {
            // 使用ProcessBuilder执行命令
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            // 读取命令输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // 等待命令执行完毕
            int exitCode = process.waitFor();
            System.out.println("Exit code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 镜头边界检测。输入：视频帧列表，音频帧列表。输出：边界索引列表，索引为视频中的边界帧。比如有shots[0,1,2,3,4,5]，返回的结果样式是[0,2,4]，因为0,2,4是边界帧。xc
    public List<Integer> shotBoundaryDetection(String csvFilePath, List<BufferedImage> video, List<byte[]> audio) {
        List<Integer> pySceneResult = Stats.readPySceneDetectResult(csvFilePath);
        return ShotsDetection.detectShots(video, audio, pySceneResult);
    }

    // 场景聚类。输出内容是场景边界对应于Shots的索引列表。比如有边界[0,5,20]，场景聚类结果可以是List=[0,2]，表示从[0,5]开始的shots在同一个场景，[20]开始的shot在另一个场景。
    public List<Integer> sceneClustering(List<BufferedImage> video, List<Integer> shotBoundaries) {
        return HierarchicalClustering.agglomerativeClustering(video, shotBoundaries, 0.5);
    }

    // 子镜头检测。输出Map<ShotIndex, List<SubshotFrames>>，比如有边界[0,5,20]，subshot可能为{0:[0, 3], 5:[5, 10, 15],
    // 20:[20]}，其中[]里面的是subshot的帧索引。
    public Map<Integer, List<Integer>> subshotDetection(List<BufferedImage> video, List<byte[]> audio,
                                                        List<Integer> shotBoundaries) {
        Map<Integer, List<Integer>> subshotBoundaries = new HashMap<>();

        //ignore part of the start and end indices from a shot
        int ignoreFrames = 45;

        for (int i = 0; i < shotBoundaries.size(); i++) {
            int startIndex = shotBoundaries.get(i);
            int endIndex = i + 1 < shotBoundaries.size() ? shotBoundaries.get(i + 1) - 1 : video.size() - 1;
            int subshotCount = 0;

            //System.out.println(startIndex + " -> " + endIndex);

            List<Integer> subshotStartAt = new ArrayList<>();

            // set the threshold value for video change dedection
            double videoThreshold = 100;
            int[][] previousFrame = new int[HEIGHT][WIDTH];
            long previousTime = 0;
            double timeatsec = 0.0;
            double previousatsec = 0.0;
            int videoSubshot = 0;

            // set the threshold value for audio change dedection
            double audioThreshold = 0.15;
            double energy = 0.0;
            double previousEnergy = 0.0;
            double durationInSeconds = 0.0;
            double previousDuration = 0.0;

            for (int j = startIndex; j <= endIndex; j++) {
                // looking for subshots through video list
                BufferedImage currentImage = video.get(j);
                int[][] currentFrame = new int[HEIGHT][WIDTH];
                int audioSubshot = 0;

                // get rgb value from the current image
                for (int y = 0; y < HEIGHT; y++) {
                    for (int x = 0; x < WIDTH; x++) {
                        int rgb = currentImage.getRGB(x, y);
                        currentFrame[y][x] = rgb;
                    }
                }

                long currentTime = System.currentTimeMillis();
                if (currentTime - previousTime < 1000 / FPS) {
                    int differenceCount = 0;
                    for (int y = 0; y < HEIGHT; y++) {
                        for (int x = 0; x < WIDTH; x++) {
                            int difference = Math.abs(currentFrame[y][x] - previousFrame[y][x]);
                            if (difference > videoThreshold) {
                                differenceCount++;
                            }
                        }
                    }
                    double motionScore = (double) differenceCount / (WIDTH * HEIGHT);
                    timeatsec = (double) j / FPS;
                    if (motionScore > 0.7 && timeatsec - previousatsec > 3 && j - audioSubshot > 60) {
                        if (j - startIndex > ignoreFrames && endIndex - j > ignoreFrames) {
                            //System.out.println("motion at " + j);
                            subshotStartAt.add(j);
                            subshotCount++;
                            videoSubshot = j;
                            previousatsec = timeatsec;
                        }
                    }
                }
                previousFrame = currentFrame;
                previousTime = currentTime;

                // looking for sub-shots through audio list
                byte[] audioBuffer = audio.get(j);
                int samplesPerBuffer = audioBuffer.length / FRAME_SIZE;

                for (int k = 0; k < audioBuffer.length; k += FRAME_SIZE) {
                    // Convert the bytes to a double value between -1 and 1
                    double sample = (double) ((audioBuffer[k] & 0xFF) | (audioBuffer[k + 1] << 8)) / audioBuffer.length;
                    // Calculate the energy of the sample
                    energy += Math.pow(sample, 2) / samplesPerBuffer;
                }
                durationInSeconds = ((double) j * audioBuffer.length / FRAME_SIZE + 0.0) / SAMPLING_RATE;

                // If the energy has changed above the threshold, a sound change has occurred
                if (Math.abs(energy - previousEnergy) > audioThreshold && durationInSeconds - previousDuration > 3 && j - videoSubshot > 60) {
                    if (!(subshotStartAt.contains(j))) {
                        if (j - startIndex > ignoreFrames && endIndex - j > ignoreFrames) {
                            //System.out.println("Sound at " + j);
                            subshotStartAt.add(j);
                            subshotCount++;
                            audioSubshot = j;
                        }
                    }
                    previousDuration = durationInSeconds;
                }

                previousEnergy = energy;
                energy = 0;
                durationInSeconds = 0;
            }

            if (subshotCount > 0) {
                subshotStartAt.add(0, startIndex);
            }

            subshotBoundaries.put(startIndex, subshotStartAt);

        }

        return subshotBoundaries;
    }

    public IndexTree buildIndexTree(int frameNum, List<Integer> shotBoundaries, List<Integer> sceneBoundaries, Map<Integer, List<Integer>> subShots) {
        IndexTree indexTree = new IndexTree(frameNum);
        indexTree.root.children = new ArrayList<>();

        for (int i = 0; i < sceneBoundaries.size(); i++) {
            int sceneStart = shotBoundaries.get(sceneBoundaries.get(i));
            int sceneEnd = (i < sceneBoundaries.size() - 1) ? shotBoundaries.get(sceneBoundaries.get(i + 1)) - 1 : frameNum - 1;
            IndexTree.Section scene = new IndexTree.Section(sceneStart, sceneEnd);
            scene.children = new ArrayList<>();
            indexTree.root.children.add(scene);

            int shotStartIndex = sceneBoundaries.get(i);
            int shotEndIndex = (i < sceneBoundaries.size() - 1) ? sceneBoundaries.get(i + 1) : shotBoundaries.size();

            for (int j = shotStartIndex; j < shotEndIndex; j++) {
                int shotStart = shotBoundaries.get(j);
                int shotEnd = (j < shotBoundaries.size() - 1) ? shotBoundaries.get(j + 1) - 1 : scene.endIndex;
                IndexTree.Section shot = new IndexTree.Section(shotStart, shotEnd);
                shot.children = new ArrayList<>();
                scene.children.add(shot);

                if (subShots.containsKey(shotStart)) {
                    List<Integer> subshotBoundaries = subShots.get(shotStart);
                    for (int k = 0; k < subshotBoundaries.size(); k++) {
                        int subshotStart = subshotBoundaries.get(k);
                        int subshotEnd = (k < subshotBoundaries.size() - 1) ? subshotBoundaries.get(k + 1) - 1 : shot.endIndex;
                        IndexTree.Section subshot = new IndexTree.Section(subshotStart, subshotEnd);
                        shot.children.add(subshot);
                    }
                }
            }
        }
        return indexTree;
    }
}
