package course.multimedia;

import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HierarchicalClustering {
    public static final int WIDTH = 480; // width of the video frames
    public static final int HEIGHT = 270; // height of the video frames
    public static int BITS_PER_FRAME = WIDTH * HEIGHT * 3;// num of bits per frame
    public static int frameNum = -1;

    public static class Cluster {
        private final List<Integer> members;

        public Cluster() {
            members = new ArrayList<>();
        }

        public void addMember(int index) {
            members.add(index);
        }

        public List<Integer> getMembers() {
            return members;
        }
    }


    // 将BufferedImage转换为Mat
    public static Mat bufferedImageToMat(BufferedImage image) {
        int type = image.getType();
        if (type == BufferedImage.TYPE_3BYTE_BGR || type == BufferedImage.TYPE_BYTE_GRAY) {
            byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            Mat mat = new Mat(image.getHeight(), image.getWidth(), type == BufferedImage.TYPE_3BYTE_BGR ? CvType.CV_8UC3 : CvType.CV_8UC1);
            mat.put(0, 0, pixels);
            return mat;
        } else if (type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_BGR) {
            int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            byte[] bytePixels = new byte[pixels.length * 3];
            int byteIndex = 0;
            for (int pixel : pixels) {
                bytePixels[byteIndex++] = (byte) ((pixel >> 16) & 0xff);
                bytePixels[byteIndex++] = (byte) ((pixel >> 8) & 0xff);
                bytePixels[byteIndex++] = (byte) (pixel & 0xff);
            }
            Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
            mat.put(0, 0, bytePixels);
            return mat;
        } else {
            throw new IllegalArgumentException("Unsupported BufferedImage type: " + type);
        }
    }

    // 提取颜色直方图特征
    public static Mat extractColorHistogram(BufferedImage image) {
        // 将 BufferedImage 转换为 Mat 格式
        Mat matImage = bufferedImageToMat(image);

        // 将图像从 BGR 转换为 HSV 色彩空间
        Mat hsvImage = new Mat();
        Imgproc.cvtColor(matImage, hsvImage, Imgproc.COLOR_BGR2HSV);

        // 设置直方图的参数
        int[] histSize = {16, 16, 16}; // 将每个通道分成 16 个 bin
        float[] histRange = {0, 180, 0, 256, 0, 256}; // H 的范围是 0-180，S 和 V 的范围是 0-256
        MatOfInt channels = new MatOfInt(0, 1, 2);
        MatOfFloat ranges = new MatOfFloat(histRange);

        // 计算直方图
        Mat hist = new Mat();
        List<Mat> images = new ArrayList<>();
        images.add(hsvImage);
        Imgproc.calcHist(images, channels, new Mat(), hist, new MatOfInt(histSize), ranges, false);

        // 归一化直方图
        Core.normalize(hist, hist);

        return hist;
    }

    // 计算镜头间的距离
    public static double calculateDistance(Mat hist1, Mat hist2) {
        // 使用巴氏距离度量方法，计算两个颜色直方图之间的距离
        return Imgproc.compareHist(hist1, hist2, Imgproc.CV_COMP_BHATTACHARYYA);
    }

    // 凝聚层次聚类
    public static List<Integer> agglomerativeClustering(List<BufferedImage> video, List<Integer> shotBoundaries, double threshold) {
        // 提取每个镜头的特征
        List<Mat> shotFeatures = new ArrayList<>();
        for (Integer boundary : shotBoundaries) {
            shotFeatures.add(extractColorHistogram(video.get(boundary)));
        }

        // 计算镜头间的距离
        int n = shotBoundaries.size();
        double[][] distances = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                distances[i][j] = calculateDistance(shotFeatures.get(i), shotFeatures.get(j));
                distances[j][i] = distances[i][j];
            }
        }

        // 初始化簇
        List<Cluster> clusters = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Cluster cluster = new Cluster();
            cluster.addMember(i);
            clusters.add(cluster);
        }

        // 执行凝聚层次聚类算法
        while (true) {
            // 查找距离最近的两个簇
            double minDistance = Double.MAX_VALUE;
            int minI = -1, minJ = -1;
            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    double distance = Double.MAX_VALUE;
                    for (int memberI : clusters.get(i).getMembers()) {
                        for (int memberJ : clusters.get(j).getMembers()) {
                            double currentDistance = distances[memberI][memberJ];
                            if (currentDistance < distance) {
                                distance = currentDistance;
                            }
                        }
                    }
                    if (distance < minDistance) {
                        minDistance = distance;
                        minI = i;
                        minJ = j;
                    }
                }
            }

            // 检查终止条件
            if (minDistance >= threshold) {
                break;
            }

            // 合并最近的两个簇
            Cluster mergedCluster = new Cluster();
            mergedCluster.getMembers().addAll(clusters.get(minI).getMembers());
            mergedCluster.getMembers().addAll(clusters.get(minJ).getMembers());
            clusters.remove(minJ);
            clusters.remove(minI);
            clusters.add(mergedCluster);
        }

        // 转换聚类结果
        List<List<Integer>> result = new ArrayList<>();
        for (Cluster cluster : clusters) {
            List<Integer> clusterMembers = new ArrayList<>();
            for (Integer memberIndex : cluster.getMembers()) {
                clusterMembers.add(shotBoundaries.get(memberIndex));
            }
            result.add(clusterMembers);
        }

        // return result;
        return convertClusterResultToSceneBoundaries(shotBoundaries, result);
    }

    public static List<Integer> convertClusterResultToSceneBoundaries(List<Integer> shotBoundaries, List<List<Integer>> clusterResult) {
        List<Integer> sceneBoundaries = new ArrayList<>();

        Map<Integer, Integer> shotToClusterMap = new TreeMap<>();
        for (int i = 0; i < clusterResult.size(); i++) {
            for (Integer shot : clusterResult.get(i)) {
                shotToClusterMap.put(shot, i);
            }
        }

        for (int i = 0; i < shotBoundaries.size(); i++) {
            if (i == 0) {
                sceneBoundaries.add(i);
            } else {
                if (shotToClusterMap.get(shotBoundaries.get(i)) != shotToClusterMap.get(shotBoundaries.get(i - 1))) {
                    sceneBoundaries.add(i);
                }
            }
        }

        return sceneBoundaries;
    }
}