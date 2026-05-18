# 🟡 High Priority Problems

> 16 problems covering sliding window, prefix sums, graph traversal, and system design patterns.

---

## 252. Meeting Rooms

### Problem Statement
Given an array of meeting time intervals `intervals` where `intervals[i] = [starti, endi]`, determine if a person could attend **all meetings** (i.e., no two intervals overlap).

### Java Solution
```java
import java.util.Arrays;

class MeetingRooms {
    public boolean canAttendMeetings(int[][] intervals) {
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        for (int i = 1; i < intervals.length; i++) {
            // If the current meeting starts before the previous one ends → conflict
            if (intervals[i][0] < intervals[i - 1][1]) return false;
        }
        return true;
    }
}
```

### Test Cases
```java
// Test 1: Overlapping → false
int[][] t1 = {{0,30},{5,10},{15,20}};
assert new MeetingRooms().canAttendMeetings(t1) == false;

// Test 2: No overlap → true
int[][] t2 = {{7,10},{2,4}};
assert new MeetingRooms().canAttendMeetings(t2) == true;

// Test 3: Back-to-back meetings (end == start) → true
int[][] t3 = {{1,5},{5,10}};
assert new MeetingRooms().canAttendMeetings(t3) == true;
```

---

## 325. Maximum Size Subarray Sum Equals k

### Problem Statement
Given an integer array `nums` and an integer `k`, return the **maximum length of a subarray** that sums to `k`. If no such subarray exists, return `0`.

### Java Solution
```java
import java.util.HashMap;
import java.util.Map;

class MaxSizeSubarraySumEqualsK {
    public int maxSubArrayLen(int[] nums, int k) {
        Map<Integer, Integer> prefixIndex = new HashMap<>();
        prefixIndex.put(0, -1); // prefix sum of 0 at index -1
        int sum = 0, maxLen = 0;

        for (int i = 0; i < nums.length; i++) {
            sum += nums[i];
            int complement = sum - k;
            if (prefixIndex.containsKey(complement)) {
                maxLen = Math.max(maxLen, i - prefixIndex.get(complement));
            }
            // Only store the first occurrence of each prefix sum
            prefixIndex.putIfAbsent(sum, i);
        }

        return maxLen;
    }
}
```

### Test Cases
```java
// Test 1: Standard case
int[] nums1 = {1,-1,5,-2,3};
assert new MaxSizeSubarraySumEqualsK().maxSubArrayLen(nums1, 3) == 4; // [1,-1,5,-2]

// Test 2: Negative numbers
int[] nums2 = {-2,-1,2,1};
assert new MaxSizeSubarraySumEqualsK().maxSubArrayLen(nums2, 1) == 2; // [-1,2]

// Test 3: No valid subarray
int[] nums3 = {1,2,3};
assert new MaxSizeSubarraySumEqualsK().maxSubArrayLen(nums3, 10) == 0;
```

---

## 340. Longest Substring with At Most K Distinct Characters

### Problem Statement
Given a string `s` and an integer `k`, return the length of the **longest substring** that contains **at most `k` distinct characters**.

### Java Solution
```java
import java.util.HashMap;
import java.util.Map;

class LongestSubstringAtMostKDistinct {
    public int lengthOfLongestSubstringKDistinct(String s, int k) {
        if (k == 0) return 0;
        Map<Character, Integer> freq = new HashMap<>();
        int left = 0, maxLen = 0;

        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            freq.put(c, freq.getOrDefault(c, 0) + 1);

            // Shrink window when distinct count exceeds k
            while (freq.size() > k) {
                char leftChar = s.charAt(left);
                freq.put(leftChar, freq.get(leftChar) - 1);
                if (freq.get(leftChar) == 0) freq.remove(leftChar);
                left++;
            }
            maxLen = Math.max(maxLen, right - left + 1);
        }
        return maxLen;
    }
}
```

### Test Cases
```java
// Test 1: k=2
assert new LongestSubstringAtMostKDistinct().lengthOfLongestSubstringKDistinct("eceba", 2) == 3; // "ece"

// Test 2: k=1
assert new LongestSubstringAtMostKDistinct().lengthOfLongestSubstringKDistinct("aa", 1) == 2;

// Test 3: k >= distinct chars in string
assert new LongestSubstringAtMostKDistinct().lengthOfLongestSubstringKDistinct("abc", 3) == 3;
```

---

## 159. Longest Substring with At Most Two Distinct Characters

### Problem Statement
Given a string `s`, return the length of the **longest substring** that contains **at most two distinct characters**.

### Java Solution
```java
import java.util.HashMap;
import java.util.Map;

class LongestSubstringAtMostTwoDistinct {
    public int lengthOfLongestSubstringTwoDistinct(String s) {
        Map<Character, Integer> freq = new HashMap<>();
        int left = 0, maxLen = 0;

        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            freq.put(c, freq.getOrDefault(c, 0) + 1);

            while (freq.size() > 2) {
                char leftChar = s.charAt(left);
                freq.put(leftChar, freq.get(leftChar) - 1);
                if (freq.get(leftChar) == 0) freq.remove(leftChar);
                left++;
            }
            maxLen = Math.max(maxLen, right - left + 1);
        }
        return maxLen;
    }
}
```

### Test Cases
```java
// Test 1: Classic case
assert new LongestSubstringAtMostTwoDistinct().lengthOfLongestSubstringTwoDistinct("eceba") == 3; // "ece"

// Test 2: All same character
assert new LongestSubstringAtMostTwoDistinct().lengthOfLongestSubstringTwoDistinct("aaaa") == 4;

// Test 3: Alternating two chars
assert new LongestSubstringAtMostTwoDistinct().lengthOfLongestSubstringTwoDistinct("abababab") == 8;
```

---

## 727. Minimum Window Subsequence

### Problem Statement
Given strings `s` and `t`, find the **minimum window substring** of `s` such that every character in `t` (**including duplicates**) appears in the window **as a subsequence** (in order). Return the minimum window, or `""` if no valid window exists.

### Java Solution
```java
class MinimumWindowSubsequence {
    public String minWindow(String s, String t) {
        int si = 0, ti = 0;
        int minLen = Integer.MAX_VALUE;
        String result = "";

        while (si < s.length()) {
            // Forward pass: find end of window
            if (s.charAt(si) == t.charAt(ti)) {
                ti++;
                if (ti == t.length()) {
                    int end = si + 1;
                    ti--;
                    // Backward pass: shrink window from the right
                    while (ti >= 0) {
                        if (s.charAt(si) == t.charAt(ti)) ti--;
                        si--;
                    }
                    si++;
                    if (end - si < minLen) {
                        minLen = end - si;
                        result = s.substring(si, end);
                    }
                    ti = 0;
                }
            }
            si++;
        }
        return result;
    }
}
```

### Test Cases
```java
// Test 1: Standard case
assert new MinimumWindowSubsequence().minWindow("abcdebdde", "bde").equals("bcde");

// Test 2: t is single character
assert new MinimumWindowSubsequence().minWindow("abcde", "d").equals("d");

// Test 3: No valid window
assert new MinimumWindowSubsequence().minWindow("abc", "xyz").equals("");
```

---

## 616. Add Bold Tag in String

### Problem Statement
Given a string `s` and an array of strings `words`, add a `<b>` tag in `s` before and after every substring of `s` that is matched by any word in `words`. If two such substrings overlap or are adjacent, merge them into one bold region. Return the resulting string.

### Java Solution
```java
import java.util.Arrays;

class AddBoldTagInString {
    public String addBoldTag(String s, String[] words) {
        int n = s.length();
        boolean[] bold = new boolean[n];

        // Mark bold positions
        for (String word : words) {
            int start = 0;
            while ((start = s.indexOf(word, start)) != -1) {
                Arrays.fill(bold, start, start + word.length(), true);
                start++;
            }
        }

        // Build result with tags
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (bold[i] && (i == 0 || !bold[i - 1])) sb.append("<b>");
            sb.append(s.charAt(i));
            if (bold[i] && (i == n - 1 || !bold[i + 1])) sb.append("</b>");
        }
        return sb.toString();
    }
}
```

### Test Cases
```java
// Test 1: Two adjacent words → merged tag
assert new AddBoldTagInString().addBoldTag("abcxyz123", new String[]{"abc","123"})
    .equals("<b>abc</b>xyz<b>123</b>");

// Test 2: Overlapping words → merged
assert new AddBoldTagInString().addBoldTag("aaabbcc", new String[]{"aaa","aab","bc"})
    .equals("<b>aaabbc</b>c");

// Test 3: No match → original string unchanged
assert new AddBoldTagInString().addBoldTag("hello", new String[]{"xyz"})
    .equals("hello");
```

---

## 271. Encode and Decode Strings

### Problem Statement
Design an algorithm to encode a **list of strings** into a single string, and decode it back to the original list. The codec must handle any character, including `#` and `/`.

### Java Solution
```java
import java.util.ArrayList;
import java.util.List;

class Codec {
    // Encode: prefix each string with "<length>#<string>"
    public String encode(List<String> strs) {
        StringBuilder sb = new StringBuilder();
        for (String s : strs) {
            sb.append(s.length()).append('#').append(s);
        }
        return sb.toString();
    }

    public List<String> decode(String s) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int j = s.indexOf('#', i);
            int len = Integer.parseInt(s.substring(i, j));
            result.add(s.substring(j + 1, j + 1 + len));
            i = j + 1 + len;
        }
        return result;
    }
}
```

### Test Cases
```java
// Test 1: Standard strings
Codec codec = new Codec();
List<String> t1 = List.of("hello", "world");
assert codec.decode(codec.encode(t1)).equals(t1);

// Test 2: Strings with special characters
List<String> t2 = List.of("we#say:", "yes#!");
assert codec.decode(codec.encode(t2)).equals(t2);

// Test 3: Empty string in list
List<String> t3 = List.of("", "a", "");
assert codec.decode(codec.encode(t3)).equals(t3);
```

---

## 305. Number of Islands II

### Problem Statement
Given an `m x n` grid initially all water (`0`), you are given a list of `positions` where each position turns a water cell into land (`1`). After each operation, return the **number of islands**. Use Union-Find for efficient dynamic connectivity.

### Java Solution
```java
import java.util.*;

class NumberOfIslandsII {
    private int[] parent, rank;
    private int count;
    private int m, n;

    public List<Integer> numIslands2(int m, int n, int[][] positions) {
        this.m = m; this.n = n;
        parent = new int[m * n];
        rank   = new int[m * n];
        Arrays.fill(parent, -1);
        count = 0;

        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        List<Integer> result = new ArrayList<>();

        for (int[] pos : positions) {
            int r = pos[0], c = pos[1];
            int id = r * n + c;
            if (parent[id] == -1) {
                parent[id] = id;
                count++;
                for (int[] dir : dirs) {
                    int nr = r + dir[0], nc = c + dir[1];
                    int nid = nr * n + nc;
                    if (nr >= 0 && nr < m && nc >= 0 && nc < n && parent[nid] != -1) {
                        union(id, nid);
                    }
                }
            }
            result.add(count);
        }
        return result;
    }

    private int find(int x) {
        if (parent[x] != x) parent[x] = find(parent[x]);
        return parent[x];
    }

    private void union(int x, int y) {
        int px = find(x), py = find(y);
        if (px == py) return;
        if (rank[px] < rank[py]) parent[px] = py;
        else if (rank[px] > rank[py]) parent[py] = px;
        else { parent[py] = px; rank[px]++; }
        count--;
    }
}
```

### Test Cases
```java
// Test 1: Standard case
int[][] pos1 = {{0,0},{0,1},{1,2},{2,1}};
assert new NumberOfIslandsII().numIslands2(3, 3, pos1).equals(List.of(1,1,2,3));

// Test 2: All land added in one island
int[][] pos2 = {{0,0},{0,1},{0,2}};
assert new NumberOfIslandsII().numIslands2(1, 3, pos2).equals(List.of(1,1,1));

// Test 3: Duplicate positions
int[][] pos3 = {{0,0},{0,0}};
assert new NumberOfIslandsII().numIslands2(2, 2, pos3).equals(List.of(1,1));
```

---

## 490. The Maze

### Problem Statement
There is a ball in a `maze` with empty spaces (`0`) and walls (`1`). The ball can roll **up, down, left, right** through empty spaces until it hits a wall. Given `start` and `destination`, return `true` if the ball can stop at `destination`.

### Java Solution
```java
import java.util.LinkedList;
import java.util.Queue;

class TheMaze {
    public boolean hasPath(int[][] maze, int[] start, int[] destination) {
        int m = maze.length, n = maze[0].length;
        boolean[][] visited = new boolean[m][n];
        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        Queue<int[]> queue = new LinkedList<>();
        queue.offer(start);
        visited[start[0]][start[1]] = true;

        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            if (curr[0] == destination[0] && curr[1] == destination[1]) return true;

            for (int[] dir : dirs) {
                int r = curr[0], c = curr[1];
                // Roll until hitting a wall
                while (r + dir[0] >= 0 && r + dir[0] < m &&
                       c + dir[1] >= 0 && c + dir[1] < n &&
                       maze[r + dir[0]][c + dir[1]] == 0) {
                    r += dir[0];
                    c += dir[1];
                }
                if (!visited[r][c]) {
                    visited[r][c] = true;
                    queue.offer(new int[]{r, c});
                }
            }
        }
        return false;
    }
}
```

### Test Cases
```java
// Test 1: Path exists
int[][] maze1 = {
    {0,0,1,0,0},
    {0,0,0,0,0},
    {0,0,0,1,0},
    {1,1,0,1,1},
    {0,0,0,0,0}
};
assert new TheMaze().hasPath(maze1, new int[]{0,4}, new int[]{4,4}) == true;

// Test 2: No path (blocked by walls)
assert new TheMaze().hasPath(maze1, new int[]{0,4}, new int[]{3,2}) == false;

// Test 3: Start == destination
assert new TheMaze().hasPath(maze1, new int[]{0,4}, new int[]{0,4}) == true;
```

---

## 489. Robot Room Cleaner

### Problem Statement
Given a robot in a room with an API that can `move()`, `turnRight()`, `turnLeft()`, and `clean()`, clean the entire room. The robot doesn't know its position or the room layout. Use DFS with **virtual coordinates** and backtracking.

### Java Solution
```java
import java.util.HashSet;
import java.util.Set;

class RobotRoomCleaner {
    interface Robot {
        boolean move();
        void turnLeft();
        void turnRight();
        void clean();
    }

    private static final int[][] DIRS = {{-1,0},{0,1},{1,0},{0,-1}}; // up, right, down, left
    private Set<String> visited = new HashSet<>();
    private Robot robot;

    public void cleanRoom(Robot robot) {
        this.robot = robot;
        dfs(0, 0, 0); // row, col, direction (0=up)
    }

    private void dfs(int row, int col, int dir) {
        robot.clean();
        visited.add(row + "," + col);

        for (int i = 0; i < 4; i++) {
            int newDir = (dir + i) % 4;
            int newRow = row + DIRS[newDir][0];
            int newCol = col + DIRS[newDir][1];

            if (!visited.contains(newRow + "," + newCol) && robot.move()) {
                dfs(newRow, newCol, newDir);
                // Backtrack: turn around, move back, turn to original direction
                robot.turnRight(); robot.turnRight();
                robot.move();
                robot.turnRight(); robot.turnRight();
            }
            robot.turnRight(); // face next direction
        }
    }
}
```

### Test Cases
```java
// Test cases use a mock Robot backed by a 2D grid.

import java.util.*;

class RobotMock implements RobotRoomCleaner.Robot {
    private final int[][] room;
    private int row, col, dir; // dir: 0=up,1=right,2=down,3=left
    public final Set<String> cleaned = new HashSet<>();
    private static final int[][] DIRS = {{-1,0},{0,1},{1,0},{0,-1}};

    RobotMock(int[][] room, int startR, int startC) {
        this.room = room; row = startR; col = startC; dir = 0;
    }

    @Override public void clean()      { cleaned.add(row + "," + col); }
    @Override public void turnLeft()   { dir = (dir + 3) % 4; }
    @Override public void turnRight()  { dir = (dir + 1) % 4; }

    @Override public boolean move() {
        int nr = row + DIRS[dir][0], nc = col + DIRS[dir][1];
        if (nr < 0 || nr >= room.length || nc < 0 || nc >= room[0].length || room[nr][nc] == 0)
            return false;
        row = nr; col = nc;
        return true;
    }
}

// Test 1: Full 2×2 open room — all 4 cells cleaned
int[][] room1 = {{1,1},{1,1}};
RobotMock mock1 = new RobotMock(room1, 0, 0);
new RobotRoomCleaner().cleanRoom(mock1);
assert mock1.cleaned.size() == 4;

// Test 2: Room with one obstacle — only reachable cells cleaned
//  1 1
//  0 1   (0,0 is obstacle)
int[][] room2 = {{1,1},{0,1}};
RobotMock mock2 = new RobotMock(room2, 0, 0);
new RobotRoomCleaner().cleanRoom(mock2);
assert mock2.cleaned.size() == 3; // cells (0,0),(0,1),(1,1)

// Test 3: Single cell — robot cleans only itself
int[][] room3 = {{1}};
RobotMock mock3 = new RobotMock(room3, 0, 0);
new RobotRoomCleaner().cleanRoom(mock3);
assert mock3.cleaned.size() == 1;
```

---

## 370. Range Addition

### Problem Statement
You have an array of length `n` initialized with all zeros. You are given `k` update operations, each tuple `(startIndex, endIndex, inc)` means incrementing subarray `[startIndex, endIndex]` by `inc`. Return the modified array after all updates.

### Java Solution
```java
class RangeAddition {
    public int[] getModifiedArray(int length, int[][] updates) {
        int[] diff = new int[length];

        // Difference array technique
        for (int[] update : updates) {
            int start = update[0], end = update[1], inc = update[2];
            diff[start] += inc;
            if (end + 1 < length) diff[end + 1] -= inc;
        }

        // Prefix sum to reconstruct
        for (int i = 1; i < length; i++) {
            diff[i] += diff[i - 1];
        }
        return diff;
    }
}
```

### Test Cases
```java
// Test 1: Standard case
int[][] updates1 = {{1,3,2},{2,4,3},{0,2,-2}};
int[] result1 = new RangeAddition().getModifiedArray(5, updates1);
assert Arrays.equals(result1, new int[]{-2,0,3,5,3});

// Test 2: Single update covering full array
int[][] updates2 = {{0,4,1}};
int[] result2 = new RangeAddition().getModifiedArray(5, updates2);
assert Arrays.equals(result2, new int[]{1,1,1,1,1});

// Test 3: No updates
int[] result3 = new RangeAddition().getModifiedArray(3, new int[][]{});
assert Arrays.equals(result3, new int[]{0,0,0});
```

---

## 644. Maximum Average Subarray II

### Problem Statement
Given an integer array `nums` and an integer `k`, find the **contiguous subarray** of length **at least `k`** that has the **maximum average**. Return the maximum average with an error tolerance of `10^-5`.

### Java Solution
```java
class MaximumAverageSubarrayII {
    public double findMaxAverage(int[] nums, int k) {
        double lo = -10000, hi = 10000;

        while (hi - lo > 1e-6) {
            double mid = (lo + hi) / 2;
            if (canAchieve(nums, k, mid)) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    // Check if there exists a subarray of length >= k with average >= target
    private boolean canAchieve(int[] nums, int k, double target) {
        // Subtract target from each element; check if any subarray sum >= 0
        double sum = 0, prevSum = 0, minPrev = 0;
        for (int i = 0; i < k; i++) sum += nums[i] - target;
        if (sum >= 0) return true;

        for (int i = k; i < nums.length; i++) {
            sum += nums[i] - target;
            prevSum += nums[i - k] - target;
            minPrev = Math.min(minPrev, prevSum);
            if (sum - minPrev >= 0) return true;
        }
        return false;
    }
}
```

### Test Cases
```java
// Test 1: Subarray longer than k gives better average
int[] nums1 = {1,12,-5,-6,50,3};
assert Math.abs(new MaximumAverageSubarrayII().findMaxAverage(nums1, 4) - 12.75) < 1e-5;

// Test 2: Entire array is the best subarray
int[] nums2 = {5,5,5,5};
assert Math.abs(new MaximumAverageSubarrayII().findMaxAverage(nums2, 1) - 5.0) < 1e-5;

// Test 3: k equals array length
int[] nums3 = {3,1,4,1,5};
double expected = (3+1+4+1+5) / 5.0;
assert Math.abs(new MaximumAverageSubarrayII().findMaxAverage(nums3, 5) - expected) < 1e-5;
```

---

## 588. Design In-Memory File System

### Problem Statement
Design an in-memory file system. Implement the `FileSystem` class:
- `List<String> ls(String path)` — list files/directories at `path` (sorted).
- `void mkdir(String path)` — creates directory (and parents).
- `void addContentToFile(String filePath, String content)` — appends content to file.
- `String readContentFromFile(String filePath)` — reads file content.

### Java Solution
```java
import java.util.*;

class FileSystem {
    private Map<String, String> files = new HashMap<>();       // path → content
    private Map<String, List<String>> dirs = new HashMap<>();  // dir → children

    public FileSystem() {
        dirs.put("/", new ArrayList<>());
    }

    public List<String> ls(String path) {
        if (files.containsKey(path)) {
            // It's a file → return just its name
            String name = path.substring(path.lastIndexOf('/') + 1);
            return List.of(name);
        }
        List<String> list = new ArrayList<>(dirs.getOrDefault(path, new ArrayList<>()));
        Collections.sort(list);
        return list;
    }

    public void mkdir(String path) {
        String[] parts = path.split("/");
        StringBuilder curr = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) { curr.append("/"); continue; }
            String parent = curr.toString();
            curr.append(part);
            dirs.computeIfAbsent(parent, k -> new ArrayList<>());
            if (!dirs.get(parent).contains(part)) dirs.get(parent).add(part);
            dirs.computeIfAbsent(curr.toString(), k -> new ArrayList<>());
            curr.append("/");
        }
    }

    public void addContentToFile(String filePath, String content) {
        if (!files.containsKey(filePath)) {
            // Register file in its parent directory
            int slash = filePath.lastIndexOf('/');
            String dir  = slash == 0 ? "/" : filePath.substring(0, slash);
            String name = filePath.substring(slash + 1);
            mkdir(dir);
            if (!dirs.get(dir).contains(name)) dirs.get(dir).add(name);
            files.put(filePath, "");
        }
        files.put(filePath, files.get(filePath) + content);
    }

    public String readContentFromFile(String filePath) {
        return files.getOrDefault(filePath, "");
    }
}
```

### Test Cases
```java
// Test 1: Basic mkdir and ls
FileSystem fs1 = new FileSystem();
fs1.mkdir("/a/b/c");
assert fs1.ls("/").equals(List.of("a"));
assert fs1.ls("/a/b").equals(List.of("c"));

// Test 2: Add file and read content
FileSystem fs2 = new FileSystem();
fs2.addContentToFile("/hello.txt", "Hello");
fs2.addContentToFile("/hello.txt", " World");
assert fs2.readContentFromFile("/hello.txt").equals("Hello World");
assert fs2.ls("/hello.txt").equals(List.of("hello.txt"));

// Test 3: ls returns sorted entries
FileSystem fs3 = new FileSystem();
fs3.mkdir("/d"); fs3.mkdir("/b"); fs3.mkdir("/a");
assert fs3.ls("/").equals(List.of("a","b","d"));
```

---

## 635. Design Log Storage System

### Problem Statement
You are given several logs, each with a `logId` and a `timestamp` in the format `"Year:Month:Day:Hour:Minute:Second"`. Implement:
- `void put(int id, String timestamp)` — stores a log.
- `List<Integer> retrieve(String start, String end, String granularity)` — returns all log IDs with timestamps within `[start, end]` at the given granularity level.

### Java Solution
```java
import java.util.*;

class LogSystem {
    // Store each log as (id -> full timestamp string)
    private Map<Integer, String> store = new HashMap<>();

    // Granularity → how many characters of "YYYY:MM:DD:HH:MM:SS" (len 19) to compare
    private static final String[] GRAN_NAMES = {"Year","Month","Day","Hour","Minute","Second"};
    private static final int[]    GRAN_CUTOFF = {  4,     7,    10,    13,     16,      19  };

    public LogSystem() {}

    public void put(int id, String timestamp) {
        store.put(id, timestamp);
    }

    public List<Integer> retrieve(String start, String end, String granularity) {
        // Find how many characters to compare based on granularity
        int cut = GRAN_CUTOFF[Arrays.asList(GRAN_NAMES).indexOf(granularity)];
        String lo = start.substring(0, cut);
        String hi = end.substring(0, cut);

        List<Integer> result = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : store.entrySet()) {
            String t = entry.getValue().substring(0, cut);
            if (t.compareTo(lo) >= 0 && t.compareTo(hi) <= 0) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
}
```

### Test Cases
```java
// Test 1: Retrieve by Year — all 3 logs fall within 2016–2017
LogSystem ls = new LogSystem();
ls.put(1, "2016:01:01:00:00:00");
ls.put(2, "2017:01:01:00:00:00");
ls.put(3, "2016:01:01:23:00:00");
List<Integer> r1 = ls.retrieve("2016:01:01:00:00:00", "2017:01:01:23:00:00", "Year");
assert r1.containsAll(List.of(1, 2, 3));

// Test 2: Retrieve by Hour — log 3 (23:xx) is outside 00–22 range
List<Integer> r2 = ls.retrieve("2016:01:01:00:00:00", "2016:01:01:22:00:00", "Hour");
assert r2.contains(1) && !r2.contains(3);

// Test 3: No logs in future year range
List<Integer> r3 = ls.retrieve("2020:01:01:00:00:00", "2021:01:01:00:00:00", "Year");
assert r3.isEmpty();
```

---

## 348. Design Tic-Tac-Toe

### Problem Statement
Design a Tic-Tac-Toe game on an `n x n` board. Each player places their mark at `(row, col)`. Return `0` if no winner yet, `1` if player 1 wins, or `2` if player 2 wins after each move. Achieve **O(1) per move** using row/column/diagonal counters.

### Java Solution
```java
class TicTacToe {
    private int[] rows, cols;
    private int diagonal, antiDiagonal;
    private int n;

    public TicTacToe(int n) {
        this.n = n;
        rows = new int[n];
        cols = new int[n];
    }

    public int move(int row, int col, int player) {
        int delta = (player == 1) ? 1 : -1;
        rows[row] += delta;
        cols[col] += delta;
        if (row == col)           diagonal    += delta;
        if (row + col == n - 1)   antiDiagonal += delta;

        if (Math.abs(rows[row]) == n || Math.abs(cols[col]) == n ||
            Math.abs(diagonal) == n  || Math.abs(antiDiagonal) == n) {
            return player;
        }
        return 0;
    }
}
```

### Test Cases
```java
// Test 1: Player 1 wins on a row
TicTacToe ttt1 = new TicTacToe(3);
ttt1.move(0,0,1); ttt1.move(0,1,2);
ttt1.move(1,0,1); ttt1.move(0,2,2);
assert ttt1.move(2,0,1) == 1; // Player 1 fills column 0

// Test 2: Player 2 wins on anti-diagonal: (0,2) → (1,1) → (2,0)
TicTacToe ttt2 = new TicTacToe(3);
ttt2.move(0,0,1); // P1
ttt2.move(0,2,2); // P2 → anti-diag start
ttt2.move(1,0,1); // P1
ttt2.move(1,1,2); // P2 → anti-diag middle
ttt2.move(2,2,1); // P1
assert ttt2.move(2,0,2) == 2; // P2 completes anti-diagonal (0,2),(1,1),(2,0)

// Test 3: No winner yet
TicTacToe ttt3 = new TicTacToe(3);
assert ttt3.move(0,0,1) == 0;
assert ttt3.move(1,1,2) == 0;
```

---

## 716. Max Stack

### Problem Statement
Design a **Max Stack** that supports:
- `push(int x)` — push element onto stack.
- `pop()` — remove and return the top element.
- `top()` — get the top element without removing it.
- `peekMax()` — retrieve the maximum element without removing it.
- `popMax()` — remove and return the maximum element (if multiple, remove the top-most one).

### Java Solution
```java
import java.util.Stack;

class MaxStack {
    private Stack<Integer> stack;
    private Stack<Integer> maxStack; // tracks max at each level

    public MaxStack() {
        stack    = new Stack<>();
        maxStack = new Stack<>();
    }

    public void push(int x) {
        stack.push(x);
        maxStack.push(maxStack.isEmpty() ? x : Math.max(x, maxStack.peek()));
    }

    public int pop() {
        maxStack.pop();
        return stack.pop();
    }

    public int top() {
        return stack.peek();
    }

    public int peekMax() {
        return maxStack.peek();
    }

    public int popMax() {
        int max = peekMax();
        Stack<Integer> temp = new Stack<>();
        // Pop until we find the max
        while (top() != max) temp.push(pop());
        pop(); // remove max
        while (!temp.isEmpty()) push(temp.pop()); // restore
        return max;
    }
}
```

### Test Cases
```java
// Test 1: Basic push/peekMax/popMax
MaxStack ms1 = new MaxStack();
ms1.push(5); ms1.push(1); ms1.push(5);
assert ms1.top()     == 5;
assert ms1.peekMax() == 5;
assert ms1.popMax()  == 5; // removes top-most 5
assert ms1.top()     == 1;
assert ms1.peekMax() == 5; // original 5 at bottom

// Test 2: Pop order
MaxStack ms2 = new MaxStack();
ms2.push(1); ms2.push(2);
assert ms2.pop() == 2;
assert ms2.top() == 1;

// Test 3: All same values
MaxStack ms3 = new MaxStack();
ms3.push(3); ms3.push(3); ms3.push(3);
assert ms3.peekMax() == 3;
assert ms3.popMax()  == 3; // removes topmost 3
assert ms3.top()     == 3; // another 3 remains
```

