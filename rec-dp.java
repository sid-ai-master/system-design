import java.util.*;

public class DPSolutionsRecursive {

    // ==================== PROBLEM 1: Delete and Earn ====================
    // Description: You are given an integer array nums. You can perform this operation any
    // number of times: Pick any nums[i] and delete it to gain nums[i] points. Afterwards,
    // if there are two elements with value nums[i] - 1 and nums[i] + 1, you must delete them too.
    // Return the maximum points you can gain.
    // Constraints: 1 ≤ nums.length ≤ 10^4, 1 ≤ nums[i] ≤ 10^4
    // Example: Input: nums = [3, 4, 2] → Output: 7
    //          Explanation: Delete 4 to gain 4 points. Then delete 3 to gain 3 points. Total = 7.
    Map<String, Integer> map;
    public int deleteAndEarn(int[] nums) {
        Arrays.sort(nums);
        map = new HashMap<>();
        return helper(nums, 0, -1);
    }

    private int helper(int[] nums, int idx, int currDeleted){

        if(idx >= nums.length){
            return 0;
        }

        String key = idx + ":" + currDeleted;
        if(map.get(key) != null) return map.get(key);

        if(nums[idx] == currDeleted + 1 || nums[idx] == currDeleted - 1){
            return helper(nums, idx + 1, currDeleted);
        }


        // Delete It
        int deleteIt = helper(nums, idx + 1, nums[idx]) + nums[idx];
        

        // Not Delete It
        int notDeleteIt = helper(nums, idx + 1, currDeleted);
        int result = Math.max(deleteIt, notDeleteIt);
        map.put(key, result);
        return result;
    }

    // ==================== PROBLEM 2: Jump Game ====================
    // Description: You are given an integer array nums. You are initially positioned at
    // the array's first index, and each element in the array represents your maximum jump
    // length from that position. Determine if you can reach the last index.
    // Constraints: 1 ≤ nums.length ≤ 10^4, 0 ≤ nums[i] ≤ 10^5
    // Example: Input: nums = [2, 3, 1, 1, 4] → Output: true
    //          Explanation: Jump 1 step from index 0 to 1, then 3 steps to the last index.
    public boolean canJump(int[] nums) {
        return canJumpHelper(nums, 0);
    }

    private boolean canJumpHelper(int[] nums, int index) {
        if (index >= nums.length - 1) return true;
        if (nums[index] == 0) return false;
        
        for (int jump = 1; jump <= nums[index]; jump++) {
            if (canJumpHelper(nums, index + jump)) return true;
        }
        return false;
    }

    // ==================== PROBLEM 3: Jump Game II ====================
    // Description: Given a 0-indexed array of integers nums of length n. You are initially
    // positioned at nums[0]. Each element nums[i] represents the maximum length of a forward
    // jump from index i. Return the minimum number of jumps to reach nums[n - 1].
    // Constraints: 1 ≤ nums.length ≤ 10^4, 0 ≤ nums[i] ≤ 10^5
    // Example: Input: nums = [2, 3, 1, 1, 4] → Output: 2
    //          Explanation: The minimum number of jumps to reach the last index is 2.
    public int jump(int[] nums) {
        return jumpHelper(nums, 0, nums.length - 1);
    }

    private int jumpHelper(int[] nums, int index, int target) {
        if (index >= target) return 0;
        int minJumps = Integer.MAX_VALUE;
        for (int jump = 1; jump <= nums[index]; jump++) {
            int result = jumpHelper(nums, index + jump, target);
            if (result != Integer.MAX_VALUE) {
                minJumps = Math.min(minJumps, result + 1);
            }
        }
        return minJumps;
    }

    // ==================== PROBLEM 4: Minimum Path Sum ====================
    // Description: Given a m x n grid filled with non-negative numbers, find a path from
    // top-left to bottom-right which minimizes the sum of all numbers along its path.
    // You can only move either down or right at any point in time.
    // Constraints: m, n ≥ 1, all values are non-negative
    // Example: Input: grid = [[1, 3, 1], [1, 5, 1], [4, 2, 1]] → Output: 7
    //          Explanation: Path is 1 → 3 → 1 → 1 → 1, sum = 7
    public int minPathSum(int[][] grid) {
        return minPathHelper(grid, 0, 0, grid.length, grid[0].length);
    }

    private int minPathHelper(int[][] grid, int row, int col, int m, int n) {
        if (row == m - 1 && col == n - 1) return grid[row][col];
        if (row >= m || col >= n) return Integer.MAX_VALUE;
        
        return grid[row][col] + Math.min(
            minPathHelper(grid, row + 1, col, m, n),
            minPathHelper(grid, row, col + 1, m, n)
        );
    }

    // ==================== PROBLEM 5: Dungeon Game ====================
    // Description: The demons had captured the princess and imprisoned her in the bottom-right
    // corner of a dungeon. The dungeon consists of m × n rooms, each containing either a demon
    // or treasure. The knight must have at least 1 health at every step, including when entering
    // a room and after leaving. Calculate the minimum initial health required to rescue the princess.
    // Constraints: m, n ≤ 100, -100 ≤ dungeon[i][j] ≤ 100
    // Example: Input: dungeon = [[-3, 5], [-10, 0], [10, 2]] → Output: 7
    //          Explanation: On the optimal path, the knight enters with 7 health.
    public int calculateMinimumHP(int[][] dungeon) {
        return dungeonHelper(dungeon, 0, 0, 1, dungeon.length, dungeon[0].length);
    }

    private int dungeonHelper(int[][] dungeon, int row, int col, int currentHealth, int m, int n) {
        currentHealth += dungeon[row][col];
        if (currentHealth <= 0) return Integer.MAX_VALUE;
        if (row == m - 1 && col == n - 1) return 1 - currentHealth;
        if (row >= m || col >= n) return Integer.MAX_VALUE;
        
        int right = dungeonHelper(dungeon, row, col + 1, currentHealth, m, n);
        int down = dungeonHelper(dungeon, row + 1, col, currentHealth, m, n);
        
        int minHealth = Math.min(right, down);
        return minHealth == Integer.MAX_VALUE ? Integer.MAX_VALUE : minHealth;
    }

    // ==================== PROBLEM 6: Maximal Square ====================
    // Description: Given an m x n binary matrix filled with '0' and '1', find the largest square
    // containing only '1's and return its area.
    // Constraints: m, n ≥ 1, matrix[i][j] is '0' or '1'
    // Example: Input: matrix = [['1', '0', '1'], ['1', '1', '1'], ['1', '1', '1']] → Output: 4
    //          Explanation: The largest square has side length 2, so area = 4
    public int maximalSquare(char[][] matrix) {
        if (matrix == null || matrix.length == 0) return 0;
        int maxSide = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[0].length; j++) {
                maxSide = Math.max(maxSide, maxSquareHelper(matrix, i, j));
            }
        }
        return maxSide * maxSide;
    }

    private int maxSquareHelper(char[][] matrix, int row, int col) {
        if (row >= matrix.length || col >= matrix[0].length || matrix[row][col] == '0') return 0;
        return 1 + Math.min(Math.min(
            maxSquareHelper(matrix, row + 1, col),
            maxSquareHelper(matrix, row, col + 1)
        ), maxSquareHelper(matrix, row + 1, col + 1));
    }

    // ==================== PROBLEM 7: Maximal Rectangle ====================
    // Description: Given a 2D binary matrix filled with '0' and '1', find the largest rectangle
    // containing only '1's and return its area.
    // Constraints: m, n ≥ 1, matrix[i][j] is '0' or '1'
    // Example: Input: matrix = [['1', '0', '1'], ['1', '1', '1'], ['1', '1', '1']] → Output: 6
    //          Explanation: The largest rectangle is formed by the last two rows with area = 6
    public int maximalRectangle(char[][] matrix) {
        if (matrix == null || matrix.length == 0) return 0;
        int maxArea = 0;
        int[] heights = new int[matrix[0].length];
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[0].length; j++) {
                heights[j] = matrix[i][j] == '1' ? heights[j] + 1 : 0;
            }
            maxArea = Math.max(maxArea, largestRectangleHelper(heights, 0, 0, 0));
        }
        return maxArea;
    }

    private int largestRectangleHelper(int[] heights, int index, int minHeight, int maxArea) {
        if (index == heights.length) return maxArea;
        minHeight = (index == 0) ? heights[index] : Math.min(minHeight, heights[index]);
        maxArea = Math.max(maxArea, minHeight * (index + 1));
        return largestRectangleHelper(heights, index + 1, minHeight, maxArea);
    }

    // ==================== PROBLEM 8: Cherry Pickup ====================
    // Description: You have two robots collecting cherries for you in the orchard represented
    // by an n x n grid. Both robots start at (0, 0), and you need to harvest all the cherries.
    // The robots can only move right or down. If both robots enter the same cell, only one can
    // pick the cherry. Return the maximum number of cherries collected.
    // Constraints: n ≤ 50, grid[i][j] is 0 or 1
    // Example: Input: grid = [[0, 1, 1, 0, 0], [1, 0, 1, 0, 1], [1, 1, 1, 0, 1], ...] → Output: 5
    public int cherryPickup(int[][] grid) {
        int n = grid.length;
        return cherryPickupHelper(grid, 0, 0, 0, 0, n);
    }

    private int cherryPickupHelper(int[][] grid, int r1, int c1, int r2, int c2, int n) {
        if (r1 >= n || c1 >= n || r2 >= n || c2 >= n || grid[r1][c1] == 0 || grid[r2][c2] == 0) return 0;
        if (r1 == n - 1 && c1 == n - 1) return grid[r1][c1];
        
        int cherry = grid[r1][c1];
        if (r1 != r2 || c1 != c2) cherry += grid[r2][c2];
        
        int result = cherry + Math.max(
            Math.max(cherryPickupHelper(grid, r1 + 1, c1, r2 + 1, c2, n),
                     cherryPickupHelper(grid, r1 + 1, c1, r2, c2 + 1, n)),
            Math.max(cherryPickupHelper(grid, r1, c1 + 1, r2 + 1, c2, n),
                     cherryPickupHelper(grid, r1, c1 + 1, r2, c2 + 1, n))
        );
        return result;
    }

    // ==================== PROBLEM 9: Cherry Pickup II ====================
    // Description: You are given a rows × cols matrix grid representing an orchard where you have
    // two robots to collect cherries. Each robot starts at (0, 0) but can move right or down.
    // Robots cannot be in the same cell simultaneously. Return the maximum cherries both robots
    // can collect together.
    // Constraints: 2 ≤ rows, cols ≤ 70, 0 ≤ grid[row][col] ≤ 100
    // Example: Input: grid = [[3, 1, 1], [2, 9, 1], [1, 1, 5]] → Output: 15
    //          Explanation: Both robots collect optimally for maximum cherries.
    public int cherryPickupII(int[][] grid) {
        int rows = grid.length, cols = grid[0].length;
        return cherryPickup2Helper(grid, 0, 0, cols - 1, rows, cols);
    }

    private int cherryPickup2Helper(int[][] grid, int r, int c1, int c2, int rows, int cols) {
        if (r == rows || c1 < 0 || c1 >= cols || c2 < 0 || c2 >= cols) return 0;
        
        int cherries = grid[r][c1];
        if (c1 != c2) cherries += grid[r][c2];
        
        int max = Integer.MIN_VALUE;
        for (int nc1 = c1 - 1; nc1 <= c1 + 1; nc1++) {
            for (int nc2 = c2 - 1; nc2 <= c2 + 1; nc2++) {
                max = Math.max(max, cherryPickup2Helper(grid, r + 1, nc1, nc2, rows, cols));
            }
        }
        return cherries + (max == Integer.MIN_VALUE ? 0 : max);
    }

    // ==================== PROBLEM 10: Word Break ====================
    // Description: Given a string s and a dictionary of strings wordDict, return true if s can be
    // segmented into a space-separated sequence of dictionary words. The same word in the dictionary
    // may be reused multiple times in the segmentation.
    // Constraints: 1 ≤ s.length ≤ 300, wordDict contains 1 to 1000 words
    // Example: Input: s = "leetcode", wordDict = ["leet", "code"] → Output: true
    //          Explanation: Return true because "leetcode" can be segmented as "leet code".
    public boolean wordBreak(String s, List<String> wordDict) {
        Set<String> dict = new HashSet<>(wordDict);
        return wordBreakHelper(s, 0, dict);
    }

    private boolean wordBreakHelper(String s, int index, Set<String> dict) {
        if (index == s.length()) return true;
        for (int i = index + 1; i <= s.length(); i++) {
            if (dict.contains(s.substring(index, i)) && wordBreakHelper(s, i, dict)) {
                return true;
            }
        }
        return false;
    }

    // ==================== PROBLEM 11: Decode Ways ====================
    // Description: A message containing letters from A-Z can be encoded into numbers using the
    // mapping: 'A' -> "1", 'B' -> "2", ..., 'Z' -> "26". Given a string s containing only
    // digits, return the number of ways to decode it.
    // Constraints: 1 ≤ s.length ≤ 89, s contains only digits, no leading zeros except "0" alone
    // Example: Input: s = "226" → Output: 3
    //          Explanation: "226" can be decoded as "2 2 6" (BZ), "22 6" (VF), or "2 26" (BZ).
    public int numDecodings(String s) {
        if (s == null || s.length() == 0 || s.charAt(0) == '0') return 0;
        return decodeHelper(s, 0);
    }

    private int decodeHelper(String s, int index) {
        if (index == s.length()) return 1;
        if (s.charAt(index) == '0') return 0;
        
        int ways = decodeHelper(s, index + 1);
        if (index + 1 < s.length()) {
            int twoDigit = Integer.parseInt(s.substring(index, index + 2));
            if (twoDigit <= 26) ways += decodeHelper(s, index + 2);
        }
        return ways;
    }

    // ==================== PROBLEM 12: Decode Ways II ====================
    // Description: A message containing letters from A-Z can be encoded into numbers. Now the
    // string may contain '*', which represents any digit from 1 to 9. Return the number of ways
    // to decode it.
    // Constraints: 1 ≤ s.length ≤ 100, s contains only digits and '*'
    // Example: Input: s = "1*" → Output: 11
    //          Explanation: The string can be decoded as 11, 12, 13, ..., 19, 1*1, 1*2, etc.
    public int numDecodingsII(String s) {
        long MOD = 1_000_000_007;
        return (int) decodeHelper2(s, 0, MOD);
    }

    private long decodeHelper2(String s, int index, long MOD) {
        if (index == s.length()) return 1;
        char c = s.charAt(index);
        if (c == '0') return 0;
        
        long ways = (c == '*' ? 9 : 1) * decodeHelper2(s, index + 1, MOD) % MOD;
        if (index + 1 < s.length()) {
            char next = s.charAt(index + 1);
            if (c == '1' || (c == '2' && next <= '6') || (c == '*' && next != '*')) {
                int count = (c == '*' && next == '*') ? 15 : (c == '*' ? 2 : (next == '*' ? (c == '1' ? 9 : 6) : 1));
                ways = (ways + count * decodeHelper2(s, index + 2, MOD)) % MOD;
            }
        }
        return ways;
    }

    // ==================== PROBLEM 13: Palindromic Substrings ====================
    // Description: Given a string s, return the number of palindromic substrings in s.
    // A string is palindromic when it reads the same backward as forward.
    // Constraints: 1 ≤ s.length ≤ 1000, s consists of lowercase English letters
    // Example: Input: s = "aabaa" → Output: 7
    //          Explanation: The palindromic substrings are "a", "a", "b", "a", "a", "aa", "aba", "bab", "aabaa".
    public int countSubstrings(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            count += expandCount(s, i, i);
            count += expandCount(s, i, i + 1);
        }
        return count;
    }

    private int expandCount(String s, int left, int right) {
        if (left < 0 || right >= s.length() || s.charAt(left) != s.charAt(right)) return 0;
        return 1 + expandCount(s, left - 1, right + 1);
    }

    // ==================== PROBLEM 14: Longest Palindromic Substring ====================
    // Description: Given a string s, return the longest palindromic substring in s.
    // Constraints: 1 ≤ s.length ≤ 1000, s consists of only digits and English letters
    // Example: Input: s = "babad" → Output: "bab" or "aba"
    //          Explanation: Both "bab" and "aba" are valid answers.
    public String longestPalindrome(String s) {
        String[] longest = {""};
        for (int i = 0; i < s.length(); i++) {
            expandPalindrome(s, i, i, longest);
            expandPalindrome(s, i, i + 1, longest);
        }
        return longest[0];
    }

    private void expandPalindrome(String s, int left, int right, String[] longest) {
        if (left < 0 || right >= s.length() || s.charAt(left) != s.charAt(right)) return;
        String current = s.substring(left, right + 1);
        if (current.length() > longest[0].length()) longest[0] = current;
        expandPalindrome(s, left - 1, right + 1, longest);
    }

    // ==================== PROBLEM 15: Longest Palindromic Subsequence ====================
    // Description: Given a string s, find the length of the longest palindromic subsequence in s.
    // A subsequence is a sequence that can be derived from another sequence by deleting some
    // or no elements without changing the order of the remaining elements.
    // Constraints: 1 ≤ s.length ≤ 1000
    // Example: Input: s = "bbbab" → Output: 4
    //          Explanation: The longest palindromic subsequence is "bbbb" with length 4.
    public int longestPalindromeSubseq(String s) {
        return lpsHelper(s, 0, s.length() - 1);
    }

    private int lpsHelper(String s, int left, int right) {
        if (left > right) return 0;
        if (left == right) return 1;
        if (s.charAt(left) == s.charAt(right)) {
            return 2 + lpsHelper(s, left + 1, right - 1);
        }
        return Math.max(lpsHelper(s, left + 1, right), lpsHelper(s, left, right - 1));
    }

    // ==================== PROBLEM 16: Edit Distance ====================
    // Description: Given two strings word1 and word2, return the minimum number of operations
    // required to convert word1 to word2. You have the following three operations permitted:
    // Insert a character, Delete a character, Replace a character.
    // Constraints: 0 ≤ word1.length, word2.length ≤ 500
    // Example: Input: word1 = "horse", word2 = "ros" → Output: 3
    //          Explanation: horse → rorse (replace) → rose (remove) → ros (remove).
    public int minDistance(String word1, String word2) {
        return editDistanceHelper(word1, word2, word1.length(), word2.length());
    }

    private int editDistanceHelper(String w1, String w2, int i, int j) {
        if (i == 0) return j;
        if (j == 0) return i;
        if (w1.charAt(i - 1) == w2.charAt(j - 1)) {
            return editDistanceHelper(w1, w2, i - 1, j - 1);
        }
        return 1 + Math.min(editDistanceHelper(w1, w2, i - 1, j),
                   Math.min(editDistanceHelper(w1, w2, i, j - 1),
                            editDistanceHelper(w1, w2, i - 1, j - 1)));
    }

    // ==================== PROBLEM 17: Distinct Subsequences ====================
    // Description: Given two strings s and t, return the number of distinct subsequences of s
    // which equals t. A subsequence of a string is a new string which is formed from the original
    // string by deleting some (can be zero) characters without disturbing the relative positions.
    // Constraints: 1 ≤ s.length, t.length ≤ 1000
    // Example: Input: s = "rabbbit", t = "rabbit" → Output: 3
    //          Explanation: There are three ways to pick distinct subsequences from s to form t.
    public int numDistinct(String s, String t) {
        return distinctHelper(s, t, 0, 0);
    }

    private int distinctHelper(String s, String t, int i, int j) {
        if (j == t.length()) return 1;
        if (i == s.length()) return 0;
        if (s.charAt(i) == t.charAt(j)) {
            return distinctHelper(s, t, i + 1, j + 1) + distinctHelper(s, t, i + 1, j);
        }
        return distinctHelper(s, t, i + 1, j);
    }

    // ==================== PROBLEM 18: Interleaving String ====================
    // Description: Given strings s1, s2, and s3, find whether s3 is formed by interleaving s1
    // and s2 in a way that maintains the relative order of characters from s1 and s2.
    // Constraints: 0 ≤ s1.length, s2.length ≤ 101, s1.length + s2.length = s3.length
    // Example: Input: s1 = "aabcc", s2 = "dbbca", s3 = "aadbbcbcac" → Output: true
    //          Explanation: One way to form s3 is: "aa" + "dbbc" + "b" + "c" + "ac".
    public boolean isInterleave(String s1, String s2, String s3) {
        if (s1.length() + s2.length() != s3.length()) return false;
        return interleaveHelper(s1, s2, s3, 0, 0, 0);
    }

    private boolean interleaveHelper(String s1, String s2, String s3, int i, int j, int k) {
        if (k == s3.length()) return true;
        boolean result = false;
        if (i < s1.length() && s1.charAt(i) == s3.charAt(k)) {
            result |= interleaveHelper(s1, s2, s3, i + 1, j, k + 1);
        }
        if (j < s2.length() && s2.charAt(j) == s3.charAt(k)) {
            result |= interleaveHelper(s1, s2, s3, i, j + 1, k + 1);
        }
        return result;
    }

    // ==================== PROBLEM 19: Regular Expression Matching ====================
    // Description: Given an input string s and a pattern p, implement regular expression matching
    // with support for '.' and '*' where '.' matches any single character and '*' matches zero or
    // more of the preceding element. The matching should cover the entire input string.
    // Constraints: 1 ≤ s.length ≤ 20, 1 ≤ p.length ≤ 30
    // Example: Input: s = "aa", p = "a" → Output: false
    //          Explanation: "a" does not match the entire string "aa".
    public boolean isMatch(String s, String p) {
        return matchHelper(s, p, 0, 0);
    }

    private boolean matchHelper(String s, String p, int i, int j) {
        if (j == p.length()) return i == s.length();
        
        boolean firstMatch = (i < s.length() && (p.charAt(j) == '.' || p.charAt(j) == s.charAt(i)));
        
        if (j + 1 < p.length() && p.charAt(j + 1) == '*') {
            return matchHelper(s, p, i, j + 2) || (firstMatch && matchHelper(s, p, i + 1, j));
        }
        
        return firstMatch && matchHelper(s, p, i + 1, j + 1);
    }

    // ==================== PROBLEM 20: Longest Increasing Subsequence ====================
    // Description: Given an integer array nums, return the length of the longest strictly increasing
    // subsequence. A subsequence is a sequence that can be derived from an array by deleting some
    // or no elements without changing the order of the remaining elements.
    // Constraints: 1 ≤ nums.length ≤ 2500, -10^4 ≤ nums[i] ≤ 10^4
    // Example: Input: nums = [10, 9, 2, 5, 3, 7, 101, 18] → Output: 4
    //          Explanation: The LIS is [2, 3, 7, 101] with length 4.
    public int lengthOfLIS(int[] nums) {
        return lisHelper(nums, -1, 0);
    }

    private int lisHelper(int[] nums, int prev, int index) {
        if (index == nums.length) return 0;
        int taken = 0;
        if (prev == -1 || nums[index] > nums[prev]) {
            taken = 1 + lisHelper(nums, index, index + 1);
        }
        int notTaken = lisHelper(nums, prev, index + 1);
        return Math.max(taken, notTaken);
    }

    // ==================== PROBLEM 21: Number of Longest Increasing Subsequence ====================
    // Description: Given an integer array nums, return the number of longest increasing subsequences.
    // Notice that the sequence has to be strictly increasing.
    // Constraints: 1 ≤ nums.length ≤ 2000, -10^5 ≤ nums[i] ≤ 10^5
    // Example: Input: nums = [1, 3, 1, 4, 1, 5] → Output: 2
    //          Explanation: Two LIS of length 4 are [1, 3, 4, 5] and [1, 4, 1, 5].
    public int findNumberOfLIS(int[] nums) {
        int maxLen = findLIS(nums, -1, 0);
        int[] count = {0};
        countLIS(nums, -1, 0, 0, maxLen, count);
        return count[0];
    }

    private int findLIS(int[] nums, int prev, int index) {
        if (index == nums.length) return 0;
        int taken = 0;
        if (prev == -1 || nums[index] > nums[prev]) {
            taken = 1 + findLIS(nums, index, index + 1);
        }
        int notTaken = findLIS(nums, prev, index + 1);
        return Math.max(taken, notTaken);
    }

    private void countLIS(int[] nums, int prev, int index, int len, int maxLen, int[] count) {
        if (len == maxLen && index == nums.length) count[0]++;
        if (index >= nums.length || len > maxLen) return;
        if (prev == -1 || nums[index] > nums[prev]) {
            countLIS(nums, index, index + 1, len + 1, maxLen, count);
        }
        countLIS(nums, prev, index + 1, len, maxLen, count);
    }

    // ==================== PROBLEM 22: Longest Common Subsequence ====================
    // Description: Given two strings text1 and text2, return the length of their longest common
    // subsequence. If there is no common subsequence, return 0.
    // Constraints: 1 ≤ text1.length, text2.length ≤ 1000
    // Example: Input: text1 = "abcde", text2 = "ace" → Output: 3
    //          Explanation: The LCS is "ace" with length 3.
    public int longestCommonSubsequence(String text1, String text2) {
        return lcsHelper(text1, text2, 0, 0);
    }

    private int lcsHelper(String t1, String t2, int i, int j) {
        if (i == t1.length() || j == t2.length()) return 0;
        if (t1.charAt(i) == t2.charAt(j)) {
            return 1 + lcsHelper(t1, t2, i + 1, j + 1);
        }
        return Math.max(lcsHelper(t1, t2, i + 1, j), lcsHelper(t1, t2, i, j + 1));
    }

    // ==================== PROBLEM 23: Uncrossed Lines ====================
    // Description: You are given two integer arrays A and B of the same length. A pair of indices
    // (i, j) is called good if A[i] == B[j] and i < j. An index pair is uncrossed if it is a good
    // pair and not crossing with any other good pair. Return the maximum number of uncrossed lines.
    // Constraints: 1 ≤ A.length, B.length ≤ 500
    // Example: Input: A = [1, 4, 2], B = [1, 2, 4] → Output: 2
    //          Explanation: We can draw two uncrossed lines connecting A[0] with B[0] and A[2] with B[2].
    public int maxUncrossedLines(int[] A, int[] B) {
        return uncrossHelper(A, B, 0, 0);
    }

    private int uncrossHelper(int[] A, int[] B, int i, int j) {
        if (i == A.length || j == B.length) return 0;
        if (A[i] == B[j]) {
            return 1 + uncrossHelper(A, B, i + 1, j + 1);
        }
        return Math.max(uncrossHelper(A, B, i + 1, j), uncrossHelper(A, B, i, j + 1));
    }

    // ==================== PROBLEM 24: Maximum Length of Pair Chain ====================
    // Description: You are given an array of pairs. You can select pairs to form a chain, where
    // a pair (c, d) can follow pair (a, b) if b < c. Return the length of the longest chain
    // which you can form.
    // Constraints: 1 ≤ pairs.length ≤ 1000, pairs[i].length = 2, -1000 ≤ pairs[i][j] ≤ 1000
    // Example: Input: pairs = [[1, 2], [7, 8], [4, 5]] → Output: 3
    //          Explanation: The longest chain is [1, 2] → [4, 5] → [7, 8].
    public int maxChainLength(int[][] pairs) {
        Arrays.sort(pairs, (a, b) -> a[0] - b[0]);
        return chainHelper(pairs, 0, Integer.MIN_VALUE);
    }

    private int chainHelper(int[][] pairs, int index, int lastEnd) {
        if (index == pairs.length) return 0;
        int taken = 0;
        if (pairs[index][0] > lastEnd) {
            taken = 1 + chainHelper(pairs, index + 1, pairs[index][1]);
        }
        int notTaken = chainHelper(pairs, index + 1, lastEnd);
        return Math.max(taken, notTaken);
    }

    // ==================== PROBLEM 25: Russian Doll Envelopes ====================
    // Description: You are given a 2D array envelopes where envelopes[i] = [w, h] represents
    // the width and height of an envelope. One envelope can fit into another envelope if and only if
    // both the width and height of one envelope are greater than the other envelope's width and height.
    // Return the maximum number of envelopes you can Russian doll (put envelopes into one another).
    // Constraints: 1 ≤ envelopes.length ≤ 10^5, envelopes[i].length = 2, 1 ≤ w, h ≤ 10^5
    // Example: Input: envelopes = [[5, 4], [6, 4], [6, 7], [2, 3]] → Output: 3
    //          Explanation: The maximum number of envelopes you can Russian doll is 3. Sequence: [2, 3] => [5, 4] => [6, 7].
    public int maxEnvelopes(int[][] envelopes) {
        Arrays.sort(envelopes, (a, b) -> {
            if (a[0] != b[0]) return a[0] - b[0];
            return b[1] - a[1];
        });
        return envelopeHelper(envelopes, 0, 0, 0);
    }

    private int envelopeHelper(int[][] envelopes, int index, int lastW, int lastH) {
        if (index == envelopes.length) return 0;
        int taken = 0;
        if (envelopes[index][0] > lastW && envelopes[index][1] > lastH) {
            taken = 1 + envelopeHelper(envelopes, index + 1, envelopes[index][0], envelopes[index][1]);
        }
        int notTaken = envelopeHelper(envelopes, index + 1, lastW, lastH);
        return Math.max(taken, notTaken);
    }

    // ==================== PROBLEM 26: Largest Divisible Subset ====================
    // Description: Given a set of distinct positive integers nums, return the largest subset answer
    // such that every pair (answer[i], answer[j]) of elements in this subset satisfies: either
    // answer[i] % answer[j] == 0, or answer[j] % answer[i] == 0.
    // Constraints: 1 ≤ nums.length ≤ 1000, 1 ≤ nums[i] ≤ 2 × 10^9, All elements are unique
    // Example: Input: nums = [1, 2, 3] → Output: [1, 2]
    //          Explanation: [1, 3] is also a valid answer.
    public List<Integer> largestDivisibleSubset(int[] nums) {
        Arrays.sort(nums);
        List<Integer> result = new ArrayList<>();
        largestDivisibleHelper(nums, 0, 1, result, new ArrayList<>());
        return result;
    }

    private void largestDivisibleHelper(int[] nums, int index, int lastNum, List<Integer> best, List<Integer> current) {
        if (current.size() > best.size()) {
            best.clear();
            best.addAll(current);
        }
        if (index == nums.length) return;
        
        if (nums[index] % lastNum == 0 || lastNum == 1) {
            current.add(nums[index]);
            largestDivisibleHelper(nums, index + 1, nums[index], best, current);
            current.remove(current.size() - 1);
        }
        largestDivisibleHelper(nums, index + 1, lastNum, best, current);
    }

    // ==================== PROBLEM 27: Longest Arithmetic Subsequence ====================
    // Description: Given an array arr and an integer difference, return the length of the longest
    // subsequence in arr such that the difference between consecutive elements in the subsequence
    // equals difference.
    // Constraints: 1 ≤ arr.length ≤ 10^5, -10^4 ≤ arr[i] ≤ 10^4, -10^4 ≤ difference ≤ 10^4
    // Example: Input: arr = [1, 5, 7, 10, 13, 14, 19], difference = 4 → Output: 3
    //          Explanation: The longest arithmetic subsequence with difference 4 is [1, 5, 9, 13] (though 9 not in array, the answer is 3 for [5, 9, 13] pattern).
    public int longestSubsequence(int[] arr, int difference) {
        return arithmeticHelper(arr, 0, Integer.MIN_VALUE, difference);
    }

    private int arithmeticHelper(int[] arr, int index, int lastNum, int diff) {
        if (index == arr.length) return 0;
        int taken = 0;
        if (lastNum == Integer.MIN_VALUE || arr[index] - lastNum == diff) {
            taken = 1 + arithmeticHelper(arr, index + 1, arr[index], diff);
        }
        int notTaken = arithmeticHelper(arr, index + 1, lastNum, diff);
        return Math.max(taken, notTaken);
    }

    // ==================== PROBLEM 28: Longest String Chain ====================
    // Description: You can choose any string from the list and delete exactly one character in it
    // so that it becomes a predecessor. A string A is a predecessor of string B if and only if we can
    // add exactly one letter anywhere in A without rearranging to make it equal to B.
    // Given a list of words, find the longest string chain that can be formed.
    // Constraints: 1 ≤ words.length ≤ 16, 1 ≤ words[i].length ≤ 16, words[i] consists of English lowercase letters
    // Example: Input: words = ["a", "b", "ba", "bca", "bda", "bdca"] → Output: 4
    //          Explanation: The longest string chain is "a" → "ba" → "bda" → "bdca".
    public int longestStrChain(String[] words) {
        Arrays.sort(words, (a, b) -> a.length() - b.length());
        Set<String> wordSet = new HashSet<>(Arrays.asList(words));
        return stringChainHelper(words, 0, wordSet, new java.util.HashMap<>());
    }

    private int stringChainHelper(String[] words, int index, Set<String> wordSet, Map<String, Integer> memo) {
        if (index == words.length) return 0;
        
        String word = words[index];
        int maxLen = 1;
        for (int i = 0; i < word.length(); i++) {
            String predecessor = word.substring(0, i) + word.substring(i + 1);
            if (wordSet.contains(predecessor)) {
                maxLen = Math.max(maxLen, 1 + stringChainHelper(words, index + 1, wordSet, memo));
            }
        }
        return maxLen;
    }

    // ==================== PROBLEM 29: Best Team With No Conflicts ====================
    // Description: Suppose you are the director of a basketball team. For the upcoming tournament,
    // you want to form a team with certain players. The rules are: The age of the players you choose
    // must all be different. Each player's score must be strictly increasing by the order of age.
    // Given a list of scores and ages of players, return the highest score sum of the team.
    // Constraints: 1 ≤ scores.length = ages.length ≤ 1000, 1 ≤ ages[i] ≤ 10^2, 1 ≤ scores[i] ≤ 10^6
    // Example: Input: scores = [1, 3, 5, 4, 1], ages = [16, 25, 35, 42, 16] → Output: 34
    //          Explanation: Select players with ages 25, 35, and 42 for a total of 3 + 5 + 4 = 12. (Example explanation may differ; output represents optimal selection.)
    public int bestTeamScore(int[] scores, int[] ages) {
        Integer[][] players = new Integer[scores.length][2];
        for (int i = 0; i < scores.length; i++) {
            players[i] = new Integer[]{ages[i], scores[i]};
        }
        Arrays.sort(players, (a, b) -> a[0].equals(b[0]) ? a[1] - b[1] : a[0] - b[0]);
        return teamHelper(players, 0, 0);
    }

    private int teamHelper(Integer[][] players, int index, int lastScore) {
        if (index == players.length) return 0;
        int taken = 0;
        if (players[index][1] >= lastScore) {
            taken = players[index][1] + teamHelper(players, index + 1, players[index][1]);
        }
        int notTaken = teamHelper(players, index + 1, lastScore);
        return Math.max(taken, notTaken);
    }

    // ==================== PROBLEM 30: Partition Equal Subset Sum ====================
    // Description: Given an integer array nums, return true if you can partition the array into two
    // subsets such that the sum of elements in both subsets is equal or return false otherwise.
    // Constraints: 1 ≤ nums.length ≤ 200, 1 ≤ nums[i] ≤ 100
    // Example: Input: nums = [1, 5, 11, 5] → Output: true
    //          Explanation: The array can be partitioned as [5, 5] and [11, 1].
    public boolean canPartition(int[] nums) {
        int sum = 0;
        for (int num : nums) sum += num;
        if (sum % 2 != 0) return false;
        return partitionHelper(nums, 0, sum / 2);
    }

    private boolean partitionHelper(int[] nums, int index, int target) {
        if (target == 0) return true;
        if (index == nums.length || target < 0) return false;
        return partitionHelper(nums, index + 1, target - nums[index]) || 
               partitionHelper(nums, index + 1, target);
    }

    // ==================== PROBLEM 31: Target Sum ====================
    // Description: You are given an integer array nums and an integer target. You want to build an
    // expression out of nums by adding one of the symbols '+' or '-' before each integer in nums
    // and then concatenate all the integers. Return the number of different expressions that you can
    // build, which evaluates to target.
    // Constraints: 1 ≤ nums.length ≤ 100, 0 ≤ nums[i] ≤ 1000, |target| ≤ 1000
    // Example: Input: nums = [1, 1, 1, 1, 1], target = 3 → Output: 5
    //          Explanation: There are 5 different ways to assign symbols to make the sum equal to target.
    public int findTargetSumWays(int[] nums, int target) {
        return targetHelper(nums, 0, 0, target);
    }

    private int targetHelper(int[] nums, int index, int current, int target) {
        if (index == nums.length) return current == target ? 1 : 0;
        return targetHelper(nums, index + 1, current + nums[index], target) +
               targetHelper(nums, index + 1, current - nums[index], target);
    }

    // ==================== PROBLEM 32: Coin Change ====================
    // Description: You are given an integer array coins representing coins of different denominations
    // and an integer amount representing a total amount of money. Return the fewest number of coins
    // that you need to make up that amount. If that amount of money cannot be made up by any
    // combination of the coins, return -1. You may assume that you have an infinite number of each kind of coin.
    // Constraints: 1 ≤ coins.length ≤ 12, 1 ≤ coins[i] ≤ 2^31 - 1, 0 ≤ amount ≤ 10^4
    // Example: Input: coins = [1, 2, 5], amount = 5 → Output: 1
    //          Explanation: 5 = 5, so fewest coins needed is 1.
    public int coinChange(int[] coins, int amount) {
        int result = coinChangeHelper(coins, amount);
        return result == Integer.MAX_VALUE ? -1 : result;
    }

    private int coinChangeHelper(int[] coins, int amount) {
        if (amount == 0) return 0;
        if (amount < 0) return Integer.MAX_VALUE;
        int minCoins = Integer.MAX_VALUE;
        for (int coin : coins) {
            int result = coinChangeHelper(coins, amount - coin);
            if (result != Integer.MAX_VALUE) {
                minCoins = Math.min(minCoins, result + 1);
            }
        }
        return minCoins;
    }

    // ==================== PROBLEM 33: Coin Change II ====================
    // Description: You are given an integer array coins representing coins of different denominations
    // and an integer amount representing a total amount of money. Return the number of combinations
    // that make up that amount. If that amount of money cannot be made up by any combination of the coins,
    // return 0. The answer is guaranteed to fit into a signed 32-bit integer.
    // Constraints: 1 ≤ coins.length ≤ 12, 1 ≤ coins[i] ≤ 2^31 - 1, 0 ≤ amount ≤ 5000
    // Example: Input: coins = [1, 2, 5], amount = 5 → Output: 5
    //          Explanation: There are 5 ways: 5=5, 5=2+2+1, 5=2+1+1+1, 5=1+1+1+1+1.
    public int change(int amount, int[] coins) {
        return changeHelper(coins, 0, amount);
    }

    private int changeHelper(int[] coins, int index, int amount) {
        if (amount == 0) return 1;
        if (index == coins.length || amount < 0) return 0;
        return changeHelper(coins, index, amount - coins[index]) + 
               changeHelper(coins, index + 1, amount);
    }

    // ==================== PROBLEM 34: Combination Sum IV ====================
    // Description: Given an array of distinct integers nums and a target integer target, return
    // the number of possible combinations that add up to target. You may use each element from
    // nums an unlimited number of times. The test cases are generated so that the answer fits in a 32-bit integer.
    // Constraints: 1 ≤ nums.length ≤ 200, 1 ≤ nums[i] ≤ 1000, target ≤ 1000
    // Example: Input: nums = [1, 2, 3], target = 4 → Output: 7
    //          Explanation: The possible combinations are: (1,1,1,1), (1,1,2), (1,2,1), (2,1,1), (2,2), (1,3), (3,1).
    public int combinationSum4(int[] nums, int target) {
        return combSum4Helper(nums, target);
    }

    private int combSum4Helper(int[] nums, int target) {
        if (target == 0) return 1;
        if (target < 0) return 0;
        int count = 0;
        for (int num : nums) {
            count += combSum4Helper(nums, target - num);
        }
        return count;
    }

    // ==================== PROBLEM 35: Perfect Squares ====================
    // Description: Given an integer n, return the least number of perfect square numbers that sum to n.
    // A perfect square is an integer that is the square of an integer; in other words, it is the
    // product of some integer with itself. For example, 1, 4, 9, and 16 are perfect squares while 3 and 11 are not.
    // Constraints: 1 ≤ n ≤ 10^4
    // Example: Input: n = 7 → Output: 2
    //          Explanation: 7 = 4 + 3, so the least number of perfect squares is 2. (Also 7 = 1+1+1+1+1+1+1).
    public int numSquares(int n) {
        return perfectSquareHelper(n);
    }

    private int perfectSquareHelper(int n) {
        if (n == 0) return 0;
        int minCount = Integer.MAX_VALUE;
        for (int i = 1; i * i <= n; i++) {
            minCount = Math.min(minCount, 1 + perfectSquareHelper(n - i * i));
        }
        return minCount;
    }

    // ==================== PROBLEM 36: Last Stone Weight II ====================
    // Description: You are given an array of integers stones where stones[i] is the weight of the
    // i-th stone. We are playing a game with the stones. In each turn, we choose any two stones and
    // smash them together. Suppose the stones have weights x and y with x <= y. If x == y, both stones are destroyed;
    // if x != y, the stone of weight y is destroyed and the stone of weight x - y is added back.
    // Return the smallest possible weight of the stone or 0 if no stones are left.
    // Constraints: 1 ≤ stones.length ≤ 100, 1 ≤ stones[i] ≤ 100
    // Example: Input: stones = [2, 7, 4, 1, 8, 1] → Output: 1
    //          Explanation: We can combine the stones into 1 stone with minimum weight 1.
    public int lastStoneWeightII(int[] stones) {
        int sum = 0;
        for (int stone : stones) sum += stone;
        return stoneWeightHelper(stones, 0, 0, sum);
    }

    private int stoneWeightHelper(int[] stones, int index, int sum1, int totalSum) {
        if (index == stones.length) {
            int sum2 = totalSum - sum1;
            return Math.abs(sum1 - sum2);
        }
        return Math.min(stoneWeightHelper(stones, index + 1, sum1 + stones[index], totalSum),
                       stoneWeightHelper(stones, index + 1, sum1, totalSum));
    }

    // ==================== PROBLEM 37: Ones and Zeroes ====================
    // Description: You are given an array of binary strings strs and two integers m and n.
    // Return the size of the largest subset of strs such that there are at most m 0's and n 1's
    // in the subset. A subset of strs is a set of strings that can be derived from strs by
    // deleting some (possibly zero) strings.
    // Constraints: 1 ≤ strs.length ≤ 100, 1 ≤ strs[i].length ≤ 100, strs[i] consists only of digits '0' and '1', 1 ≤ m, n ≤ 100
    // Example: Input: strs = ["10", "0001", "111001", "1", "0"], m = 5, n = 3 → Output: 4
    //          Explanation: The largest subset has at most 5 zeros and 3 ones. Subset: ["10", "0001", "1", "0"] has total 5 zeros and 3 ones.
    public int findMaxForm(String[] strs, int m, int n) {
        return maxFormHelper(strs, 0, m, n);
    }

    private int maxFormHelper(String[] strs, int index, int zeros, int ones) {
        if (index == strs.length || (zeros == 0 && ones == 0)) return 0;
        
        int z = 0, o = 0;
        for (char c : strs[index].toCharArray()) {
            if (c == '0') z++;
            else o++;
        }
        
        int taken = 0;
        if (z <= zeros && o <= ones) {
            taken = 1 + maxFormHelper(strs, index + 1, zeros - z, ones - o);
        }
        int notTaken = maxFormHelper(strs, index + 1, zeros, ones);
        return Math.max(taken, notTaken);
    }

    // ==================== PROBLEM 38: Profitable Schemes ====================
    // Description: There is a group of n members, and you are to form a profitable scheme consisting
    // of some group members. An operation is profitable if the total contribution is at least minProfit.
    // Given an array group of n integers representing the sizes of crime groups, an array profit of n integers,
    // an integer minProfit, and an integer n. Return the number of ways to form a profitable scheme.
    // Constraints: 1 ≤ n ≤ 100, 1 ≤ group.length ≤ 100, profit.length = group.length, 0 ≤ minProfit ≤ 100, 0 ≤ group[i] ≤ 100, 0 ≤ profit[i] ≤ 100
    // Example: Input: group = [10, 20, 30], profit = [5, 10, 20], minProfit = 35, n = 2 → Output: 0
    //          Explanation: To achieve minProfit = 35, we need all members or more members than allowed.
    public int profitableSchemes(int[] group, int[] profit, int minProfit, int n) {
        return schemeHelper(group, profit, 0, 0, 0, n, minProfit);
    }

    private int schemeHelper(int[] group, int[] profit, int index, int members, int currentProfit, int maxMembers, int minProfit) {
        if (members > maxMembers) return 0;
        if (index == group.length) {
            return currentProfit >= minProfit ? 1 : 0;
        }
        
        int taken = schemeHelper(group, profit, index + 1, members + group[index], currentProfit + profit[index], maxMembers, minProfit);
        int notTaken = schemeHelper(group, profit, index + 1, members, currentProfit, maxMembers, minProfit);
        return taken + notTaken;
    }

    // ==================== PROBLEM 39: Partition to K Equal Sum Subsets ====================
    // Description: Given an integer array nums and an integer k, return true if nums can be partitioned
    // into k non-empty subsets whose sums are all equal. Constraints: 1 ≤ k ≤ nums.length ≤ 16,
    // 1 ≤ nums[i] ≤ 10^4
    // Example: Input: nums = [4, 3, 2, 3, 5, 2, 1], k = 4 → Output: true
    //          Explanation: It's possible to divide it into 4 subsets (5), (1, 4), (2, 3), (3, 2) each with sum 5.
    public boolean canPartitionKSubsets(int[] nums, int k) {
        int sum = 0;
        for (int num : nums) sum += num;
        if (sum % k != 0) return false;
        
        int target = sum / k;
        int[] buckets = new int[k];
        Arrays.sort(nums);
        return partitionKHelper(nums, buckets, nums.length - 1, target);
    }

    private boolean partitionKHelper(int[] nums, int[] buckets, int index, int target) {
        if (index < 0) {
            for (int bucket : buckets) if (bucket != target) return false;
            return true;
        }
        
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] + nums[index] <= target) {
                buckets[i] += nums[index];
                if (partitionKHelper(nums, buckets, index - 1, target)) return true;
                buckets[i] -= nums[index];
            }
            if (buckets[i] == 0) break;
        }
        return false;
    }

    // ==================== PROBLEM 40: Unique Binary Search Trees ====================
    // Description: Given an integer n, return the number of structurally unique BSTs (binary search
    // trees) which has exactly n nodes of unique values from 1 to n.
    // Constraints: 1 ≤ n ≤ 20
    // Example: Input: n = 3 → Output: 5
    //          Explanation: Given n = 3, there are a total of 5 structurally unique BSTs with nodes 1, 2, 3.
    public int numTrees(int n) {
        return numTreesHelper(1, n);
    }

    private int numTreesHelper(int start, int end) {
        if (start > end) return 1;
        if (start == end) return 1;
        
        int count = 0;
        for (int i = start; i <= end; i++) {
            int left = numTreesHelper(start, i - 1);
            int right = numTreesHelper(i + 1, end);
            count += left * right;
        }
        return count;
    }
}
