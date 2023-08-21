package course.multimedia;

import java.util.List;

public class IndexTree {

    public static class Section {
        int startIndex;
        int endIndex;
        List<Section> children;

        /**
         * Creates a new video section using the specified start and end indices.
         * The section can represent a video, scene, shot, or sub-shot.
         * Note that both the start and end indices are inclusive.
         *
         * @param startIndex the starting index of the video section
         * @param endIndex   the ending index of the video section
         */
        public Section(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    Section root;

    public IndexTree(int frameNum) {
        if (frameNum == 0) {
            root = new Section(0, 0);
            System.out.println("This video has no frames. Please check your input.");
        } else {
            root = new Section(0, frameNum - 1);
        }
    }

    /***
     * Build the test case index tree from a json file.
     *                   Root
     *      /             |           \
     *    Scene1       Scene2        Scene3...
     *    /   \        /    \        /    \
     *    Shot1 Shot2 Shot3 Shot4  Shot5 Shot6...
     *    / \   / \   / \   / \    / \   / \
     *    Sub1 Sub2 Sub3 Sub4 Sub5 Sub6 Sub7...
     */
}
