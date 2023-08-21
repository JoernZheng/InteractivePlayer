package course.multimedia;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Stats {
    public static final List<Integer> READY_PLAYER_ONE = Arrays.asList(1, 160,250,419,506,895,1079,1130,1179,
            1351,1921,2331,2459,2583,2716,3148,3244,3263,3284,3303,3546,3620,3729,3770,3809,3848,
            3879,3990,4023,4052,4081,4129,4232,4347,4492,4724,4844,5329,5599,5754,5952,6140,6303,
            6857,6969,7048,7458,7591,7669,7835,8018,8080,8114,8178,8269,8369,8510);
    public static final List<Integer> READY_PLAYER_ONE_PY_SCENE = Arrays.asList(1, 162, 252, 421, 509, 897, 1081, 1132,
        1180, 1352, 2332, 2717, 3148, 3547, 3621, 3771, 3991, 4053, 4082, 4232, 4347, 4492, 4725, 4845,
        5600, 5755, 5952, 6141, 6303, 6857, 7048, 7458, 7592, 7670,7836, 7877, 7941, 7986, 8018, 8081,
        8115, 8186, 8270, 8290, 8312, 8370, 8511);
    public static final List<Integer> GATSBY_SHOTS_PY_SCENE = Arrays.asList(
            1, 1688, 1820, 1894, 2143, 2722, 2838, 3080, 3322,
            3712, 3959, 4033, 4329, 4383, 4437, 4474, 4553, 4607,
            4649, 4805, 4865, 4913, 4959, 5009, 5077, 5108, 5145,
            5214, 5259, 5324, 5373, 5613);
    public static final List<Integer> GATSBY_SHOTS = Arrays.asList(
            1,775,1048,1345,1471,1698,1822,1894,2150,2728,2838,3087,3322,
            3721,3875,3971,4036,4131,4218,4218,4236,4330,4337,4389,4446,
            4480,4565,4615,4653,4721,4750,4812,4837,4865,5023,5069,5105,
            5154,5209,5223,5299,5321,5321,5377,5423);
    public static final List<Integer> LONG_DARK = Arrays.asList(0,183,313,446,620,798,931,1100,
            1236,1321,1411,1498,1586,1767,1943,2026,2115,2208,2297,2386,2473,2650,2826,
            3000,3175,3410,3573,3687,4059,4287,5224,5652);
    public static final List<Integer> LONG_DARK_PY_SCENE = Arrays.asList(1, 31, 61, 121, 184, 314,
            447, 621, 799, 932, 1101, 1237, 1322, 1412, 1499, 1587, 1768, 1944, 2027, 2116,
            2209, 2298, 2387, 2474, 2651, 2827, 3001, 3176, 3411, 3683, 4060, 4286, 5220, 5649);

    public static final int BIAS = 10;

    public static List<Integer> findMissingValues(List<Integer> sortedBase, List<Integer> sortedCompare) {
        List<Integer> missing = new ArrayList<>();
        boolean miss = true;

        for (Integer value : sortedBase) {
            miss = true;
            for (int i = -BIAS; i <= BIAS; i++) {
                if (Collections.binarySearch(sortedCompare, value + i) >= 0) {
                    miss = false;
                    break;
                }
            }
            if (miss) missing.add(value);
        }

        return missing;
    }

    public static void drawLineChart(List<Double> data, String chartTitle) {
        XYSeriesCollection dataset = convertToXYSeries(data);

        JFreeChart chart = ChartFactory.createXYLineChart(
                chartTitle,
                "X Axis Label",
                "Y Axis Label",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        XYPlot plot = (XYPlot) chart.getPlot();
//        plot.setInsets(new RectangleInsets(10, 10, 10, 10));
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        plot.setRenderer(renderer);
        ChartFrame frame = new ChartFrame("Chart", chart);
        frame.pack();
        frame.setVisible(true);

    }

    private static XYSeriesCollection convertToXYSeries(List<Double> values) {
        XYSeries series = new XYSeries("Data");
        for (int i = 0; i < values.size(); i++) {
            series.add(i, values.get(i));
        }
        return new XYSeriesCollection(series);
    }

    public static List<Integer> readPySceneDetectResult(String csvFile) {
        List<Integer> data = new ArrayList<>();
        String line = "";
        String csvDelimiter = ",";
        int startFrameIdx = 1;

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            // Read the head
            br.readLine();
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] values = line.split(csvDelimiter);
                data.add(Integer.valueOf(values[startFrameIdx]));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return data;
    }


}
