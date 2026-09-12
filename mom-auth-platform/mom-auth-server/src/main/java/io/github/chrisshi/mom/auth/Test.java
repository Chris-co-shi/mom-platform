package io.github.chrisshi.mom.auth;

/**
 * @author 史偕成
 * @date 2026/09/10 14:18
 **/
public class Test {

    private final static int[] heights = {3, 5, 3, 4, 5};

    public static Integer countBuildings(int[] heights) {
        if (heights == null || heights.length == 0) {
            return null;
        }
        int count = 0;
        int stantVal = heights[0];
        for (int i = 1; i < heights.length; i++) {
            if (heights[i] > stantVal) {
                count++;
                stantVal = heights[i];
            }
        }
        System.out.println("count build : " + count);


        return null;
    }

    static void main() {
        countBuildings(heights);
    }
}
