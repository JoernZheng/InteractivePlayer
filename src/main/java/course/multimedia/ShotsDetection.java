package course.multimedia;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.util.*;

import static course.multimedia.HierarchicalClustering.bufferedImageToMat;

public class ShotsDetection {
    static final int HIST_SIZE = 256; // Number of bins in the histogram
    static final float[] RANGE = {0, 256}; // Range of pixel values to consider
    static final boolean ACCUMULATE = false; // Whether to accumulate the histogram

    static Mat frame = new Mat();
    static Mat prevFrame = new Mat();
    static Mat hist1 = new Mat();
    static Mat hist2 = new Mat();
    static Mat hist3 = new Mat();
    static Mat hist4 = new Mat();
    static List<Double> scores = new ArrayList<>();
    static List<Double> uScores = new ArrayList<>();
    static List<Double> vScores = new ArrayList<>();
    static final double DIFFERENCE_THRESHOLD = 650;
    static final double CORRELATION_THRESHOLD = 0.8;
    static final double CHI_SQUARE_THRESHOLD = Math.pow(10, 6);
    static final int SHOT_MIN_DIS = 10;
    static final double THRESHOLD = 0.6;


    public static List<Integer> detectShotsSimple(List<Mat> mats, List<Integer> pySceneShots, int startIdx, int endIdx, int shotMinDistance, double threshold) {
        List<Integer> result;
        List<Integer> correlationShots = new ArrayList<>();
        List<Integer> chiSquareShots = new ArrayList<>();
        List<Integer> diffShots = new ArrayList<>();

        Mat uChannel = new Mat();
        Mat preUChannel = new Mat();
        Mat vChannel = new Mat();
        Mat preVChannel = new Mat();
        int corrFrameDis = shotMinDistance;
        int chiFrameDis = shotMinDistance;
        int diffDis = shotMinDistance;

        for (int i = startIdx; i < endIdx - shotMinDistance; i++) {
            prevFrame = mats.get(i);
            frame = mats.get(i + 1);

            Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2YUV);

            Core.extractChannel(prevFrame, preUChannel, 1);
            Core.extractChannel(prevFrame, preVChannel, 2);
            Core.extractChannel(frame, uChannel, 1);
            Core.extractChannel(frame, vChannel, 2);

            // U channel histograms
            Imgproc.calcHist(Arrays.asList(preUChannel), new MatOfInt(0), new Mat(), hist1, new MatOfInt(HIST_SIZE), new MatOfFloat(RANGE), ACCUMULATE);
            Core.normalize(hist1, hist1, 0, hist1.rows(), Core.NORM_MINMAX);
            Imgproc.calcHist(Arrays.asList(uChannel), new MatOfInt(0), new Mat(), hist2, new MatOfInt(HIST_SIZE), new MatOfFloat(RANGE), ACCUMULATE);
            Core.normalize(hist2, hist2, 0, hist2.rows(), Core.NORM_MINMAX);
            // V channel histograms
            Imgproc.calcHist(Arrays.asList(preVChannel), new MatOfInt(0), new Mat(), hist3, new MatOfInt(HIST_SIZE), new MatOfFloat(RANGE), ACCUMULATE);
            Core.normalize(hist3, hist3, 0, hist3.rows(), Core.NORM_MINMAX);
            Imgproc.calcHist(Arrays.asList(vChannel), new MatOfInt(0), new Mat(), hist4, new MatOfInt(HIST_SIZE), new MatOfFloat(RANGE), ACCUMULATE);
            Core.normalize(hist4, hist4, 0, hist4.rows(), Core.NORM_MINMAX);

            // Calculate similarity and add to mid-result shots list
            if (corrFrameDis > 0) corrFrameDis--;
            else {
                if (correlationBased(CORRELATION_THRESHOLD, false)) {
                    correlationShots.add(i);
                    corrFrameDis = shotMinDistance;
                }
            }

            if (chiFrameDis > 0) chiFrameDis--;
            else {
                if (chiSquareBased(CHI_SQUARE_THRESHOLD, false)) {
                    chiSquareShots.add(i);
                    chiFrameDis = shotMinDistance;
                }
            }

            if (diffDis > 0) diffDis--;
            else {
                if (differenceBased(DIFFERENCE_THRESHOLD, false)) {
                    diffShots.add(i);
                    diffDis = shotMinDistance;
                }
            }
        }

        result = vote(correlationShots, chiSquareShots, diffShots, pySceneShots, threshold);
        // A shot last at least Stats.BIAS(10) frames
        for (int i = 0; i < result.size() - 1; i++) {
            while (i + 1 < result.size() &&
                    result.get(i + 1) - result.get(i) < Stats.BIAS) result.remove(i + 1);
        }
        return result;
    }

    public static List<Integer> detectShots(List<BufferedImage> video, List<byte[]> audio, List<Integer> pySceneDetectResult) {
        List<Integer> result = new ArrayList<>(pySceneDetectResult);
        List<Mat> mats = convertToMat(video);
        int startFrameIdx;
        int endFrameIdx;
        List<List<Integer>> betweenFramesShots = new ArrayList<>();

        for (int i = 0; i < pySceneDetectResult.size() - 1; i++) {
            startFrameIdx = pySceneDetectResult.get(i);
            endFrameIdx = pySceneDetectResult.get(i + 1);

            List<Integer> currRes = ShotsDetection.detectShotsSimple(mats, pySceneDetectResult, startFrameIdx, endFrameIdx, SHOT_MIN_DIS, THRESHOLD);
            betweenFramesShots.add(currRes);
        }

        for (List<Integer> list : betweenFramesShots) {
            result.addAll(list);
        }

        Collections.sort(result);
        result.set(0, 0);
        return result;
    }

    private static List<Mat> convertToMat(List<BufferedImage> video) {
        List<Mat> matList = new ArrayList<>();

        // Convert each BufferedImage to Mat and add it to the list
        for (BufferedImage bufferedImage : video) {
            matList.add(bufferedImageToMat(bufferedImage));
        }

        return matList;
    }

    private static boolean differenceBased(double threshold, boolean isDrawChart) {

        double difference = 0;
        for (int i = 0; i < hist1.rows(); i++) {
            double h1Value = hist1.get(i, 0)[0];
            double h2Value = hist2.get(i, 0)[0];
            difference += Math.abs(h2Value - h1Value);
        }

        if (isDrawChart) {
            scores.add(difference);
        }

        // If correlation is less than threshold, then a shot boundary has occurred
        return difference > threshold;
    }

    private static boolean correlationBased(double threshold, boolean isDrawChart) {
        double uCorrelation = Imgproc.compareHist(hist1, hist2, Imgproc.HISTCMP_CORREL);
        double vCorrelation = Imgproc.compareHist(hist3, hist4, Imgproc.HISTCMP_CORREL);
        double score = uCorrelation * vCorrelation;

        if (isDrawChart) {
            scores.add(score);
            uScores.add(uCorrelation);
            vScores.add(vCorrelation);
        }

        // If correlation is less than threshold, then a shot boundary has occurred
        return score < threshold;
    }

    private static boolean chiSquareBased(double threshold, boolean isDrawChart) {
        double uCorrelation = Imgproc.compareHist(hist1, hist2, Imgproc.HISTCMP_CHISQR);
        double vCorrelation = Imgproc.compareHist(hist3, hist4, Imgproc.HISTCMP_CHISQR);
        double score = uCorrelation * vCorrelation;

        if (isDrawChart) {
            scores.add(score);
            uScores.add(uCorrelation);
            vScores.add(vCorrelation);
        }

        // If correlation is less than threshold, then a shot boundary has occurred
        return score > threshold;
    }

    private static List<Integer> vote(List<Integer> correlationShots, List<Integer> chiSquareShots,
                                      List<Integer> differenceShots, List<Integer> pySceneShots, double threshold) {
        List<Integer> result = new ArrayList<>();
        Set<Integer> midRes = new HashSet<>();
        Set<Integer> pyShots = new HashSet<>(pySceneShots);
        Set<Integer> corrShots = new HashSet<>(correlationShots);
        Set<Integer> chiShots = new HashSet<>(chiSquareShots);
        Set<Integer> diffShots = new HashSet<>(differenceShots);
        double pySceneWeight = 0.0;
        double correlationWeight = 0.33;
        double chiSquareWeight = 0.33;
        double diffWeight = 0.33;
        double currScore = 0;
        boolean inPyScene = false;
        boolean inCorrelation = false;
        boolean inChiSquare = false;
        boolean inDiff = false;


        midRes.addAll(pyShots);
        midRes.addAll(corrShots);
        midRes.addAll(chiShots);
        midRes.addAll(diffShots);

        for (int value : midRes) {
            currScore = 0;
            inPyScene = false;
            inCorrelation = false;
            inChiSquare = false;
            inDiff = false;

            for (int i = -Stats.BIAS; i <= Stats.BIAS; i++) {
                if (pyShots.contains(value + i)) inPyScene = true;
                if (corrShots.contains(value + i)) inCorrelation = true;
                if (chiShots.contains(value + i)) inChiSquare = true;
                if (diffShots.contains(value + i)) inDiff = true;
            }

            if (inPyScene) currScore += pySceneWeight;
            if (inCorrelation) currScore += correlationWeight;
            if (inChiSquare) currScore += chiSquareWeight;
            if (inDiff) currScore += diffWeight;

            if (currScore >= threshold) result.add(value);
        }

        Collections.sort(result);
        return result;
    }
}
