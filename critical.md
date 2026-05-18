# 🔴 Critical Priority Problems

> 12 problems that cover the widest ground in FAANG interview loops.

---

## 253. Meeting Rooms II

### Problem Statement
Given an array of meeting time intervals `intervals` where `intervals[i] = [starti, endi]`, return the **minimum number of conference rooms** required to schedule all meetings without conflicts.

### Java Solution
```java
import java.util.Arrays;
import java.util.PriorityQueue;

class MeetingRoomsII {
    public int minMeetingRooms(int[][] intervals) {
        if (intervals == null || intervals.length == 0) return 0;

        // Sort by start time
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);

        // Min-heap tracks earliest ending meeting
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();

        for (int[] interval : intervals) {
            // Free up room if earliest-ending meeting has ended
            if (!minHeap.isEmpty() && minHeap.peek() <= interval[0]) {
                minHeap.poll();
            }
            minHeap.offer(interval[1]);
        }

        return minHeap.size();
    }
}
```

### Test Cases
```java
// Test 1: Two overlapping meetings → need 2 rooms
int[][] t1 = {{0,30},{5,10},{15,20}};
assert new MeetingRoomsII().minMeetingRooms(t1) == 2;

// Test 2: No overlap → 1 room suffices
int[][] t2 = {{7,10},{2,4}};
assert new MeetingRoomsII().minMeetingRooms(t2) == 1;

// Test 3: All meetings at the same time → 3 rooms needed
int[][] t3 = {{1,5},{1,5},{1,5}};
assert new MeetingRoomsII().minMeetingRooms(t3) == 3;
```

---

## 269. Alien Dictionary

### Problem Statement
There is a new alien language that uses the English alphabet, but the order among letters is unknown. You are given a list of `words` sorted **lexicographically by the alien language's rules**. Derive the order of characters and return any valid ordering. Return `""` if no valid ordering exists (cycle) or if the input is contradictory.

### Java Solution
```java
import java.util.*;

class AlienDictionary {
    public String alienOrder(String[] words) {
        Map<Character, Set<Character>> adj = new HashMap<>();
        Map<Character, Integer> inDegree = new HashMap<>();

        // Initialize all characters
        for (String word : words) {
            for (char c : word.toCharArray()) {
                adj.putIfAbsent(c, new HashSet<>());
                inDegree.putIfAbsent(c, 0);
            }
        }

        // Build graph from adjacent word pairs
        for (int i = 0; i < words.length - 1; i++) {
            String w1 = words[i], w2 = words[i + 1];
            int minLen = Math.min(w1.length(), w2.length());
            // Invalid: longer word is prefix of shorter next word
            if (w1.length() > w2.length() && w1.startsWith(w2)) return "";
            for (int j = 0; j < minLen; j++) {
                if (w1.charAt(j) != w2.charAt(j)) {
                    char from = w1.charAt(j), to = w2.charAt(j);
                    if (!adj.get(from).contains(to)) {
                        adj.get(from).add(to);
                        inDegree.put(to, inDegree.get(to) + 1);
                    }
                    break;
                }
            }
        }

        // Topological sort (Kahn's BFS)
        Queue<Character> queue = new LinkedList<>();
        for (char c : inDegree.keySet()) {
            if (inDegree.get(c) == 0) queue.offer(c);
        }

        StringBuilder sb = new StringBuilder();
        while (!queue.isEmpty()) {
            char c = queue.poll();
            sb.append(c);
            for (char neighbor : adj.get(c)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) queue.offer(neighbor);
            }
        }

        return sb.length() == inDegree.size() ? sb.toString() : "";
    }
}
```

### Test Cases
```java
// Test 1: Constraints w<e<r<t<f form a unique total order → only one valid output
String[] t1 = {"wrt","wrf","er","ett","rftt"};
assert new AlienDictionary().alienOrder(t1).equals("wertf");

// Test 2: Cycle detected (z→x→z) → return ""
String[] t2 = {"z","x","z"};
assert new AlienDictionary().alienOrder(t2).equals("");

// Test 3: Two words, single ordering constraint z < x
String[] t3 = {"z","x"};
assert new AlienDictionary().alienOrder(t3).equals("zx");
```

---

## 286. Walls and Gates

### Problem Statement
You are given an `m x n` grid `rooms` with three possible values:
- `-1` — a wall or obstacle
- `0` — a gate
- `INF` (2147483647) — an empty room

Fill each empty room with the **distance to its nearest gate**. If it is impossible to reach a gate, leave it as `INF`.

### Java Solution
```java
import java.util.LinkedList;
import java.util.Queue;

class WallsAndGates {
    private static final int INF = Integer.MAX_VALUE;
    private static final int[][] DIRS = {{0,1},{0,-1},{1,0},{-1,0}};

    public void wallsAndGates(int[][] rooms) {
        if (rooms == null || rooms.length == 0) return;
        int m = rooms.length, n = rooms[0].length;
        Queue<int[]> queue = new LinkedList<>();

        // Enqueue all gates first (multi-source BFS)
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                if (rooms[i][j] == 0) queue.offer(new int[]{i, j});

        // BFS outward from all gates simultaneously
        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            for (int[] dir : DIRS) {
                int r = curr[0] + dir[0], c = curr[1] + dir[1];
                if (r < 0 || r >= m || c < 0 || c >= n || rooms[r][c] != INF) continue;
                rooms[r][c] = rooms[curr[0]][curr[1]] + 1;
                queue.offer(new int[]{r, c});
            }
        }
    }
}
```

### Test Cases
```java
// Test 1: Standard grid — distances filled from nearest gate
int INF = Integer.MAX_VALUE;
int[][] grid1 = {
    {INF, -1,  0, INF},
    {INF, INF, INF, -1},
    {INF, -1,  INF, -1},
    {  0, -1,  INF, INF}
};
new WallsAndGates().wallsAndGates(grid1);
assert grid1[0][0] == 3; // 3 steps from gate at (0,2)
assert grid1[1][2] == 1; // 1 step from gate at (0,2)
assert grid1[2][2] == 2; // 2 steps from gate at (0,2)
assert grid1[3][2] == 1; // 1 step from gate at (3,0)

// Test 2: No gates → all INF rooms stay INF
int[][] grid2 = {{INF, -1},{INF, INF}};
new WallsAndGates().wallsAndGates(grid2);
assert grid2[0][0] == INF;
assert grid2[1][0] == INF;
assert grid2[1][1] == INF;

// Test 3: Single gate at center — adjacent = 1, corners = 2
int[][] grid3 = {{INF,INF,INF},{INF,0,INF},{INF,INF,INF}};
new WallsAndGates().wallsAndGates(grid3);
assert grid3[0][1] == 1; // top edge
assert grid3[1][0] == 1; // left edge
assert grid3[0][0] == 2; // top-left corner
assert grid3[2][2] == 2; // bottom-right corner
```

---

## 323. Number of Connected Components in an Undirected Graph

### Problem Statement
Given `n` nodes labeled `0` to `n-1` and a list of undirected `edges`, return the **number of connected components** in the graph.

### Java Solution
```java
class NumberOfConnectedComponents {
    private int[] parent, rank;

    public int countComponents(int n, int[][] edges) {
        parent = new int[n];
        rank = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        int components = n;
        for (int[] edge : edges) {
            if (union(edge[0], edge[1])) components--;
        }
        return components;
    }

    private int find(int x) {
        if (parent[x] != x) parent[x] = find(parent[x]); // path compression
        return parent[x];
    }

    private boolean union(int x, int y) {
        int px = find(x), py = find(y);
        if (px == py) return false;
        if (rank[px] < rank[py]) parent[px] = py;
        else if (rank[px] > rank[py]) parent[py] = px;
        else { parent[py] = px; rank[px]++; }
        return true;
    }
}
```

### Test Cases
```java
// Test 1: Two components
int[][] edges1 = {{0,1},{1,2},{3,4}};
assert new NumberOfConnectedComponents().countComponents(5, edges1) == 2;

// Test 2: All connected
int[][] edges2 = {{0,1},{1,2},{2,3},{3,4}};
assert new NumberOfConnectedComponents().countComponents(5, edges2) == 1;

// Test 3: No edges → each node is its own component
int[][] edges3 = {};
assert new NumberOfConnectedComponents().countComponents(4, edges3) == 4;
```

---

## 261. Graph Valid Tree

### Problem Statement
Given `n` nodes labeled `0` to `n-1` and a list of undirected `edges`, return **true** if these edges make up a valid tree. A valid tree must:
1. Have exactly `n - 1` edges.
2. Be fully connected (no cycles).

### Java Solution
```java
class GraphValidTree {
    private int[] parent, rank;

    public boolean validTree(int n, int[][] edges) {
        if (edges.length != n - 1) return false; // A tree must have exactly n-1 edges

        parent = new int[n];
        rank = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int[] edge : edges) {
            if (!union(edge[0], edge[1])) return false; // Cycle detected
        }
        return true;
    }

    private int find(int x) {
        if (parent[x] != x) parent[x] = find(parent[x]);
        return parent[x];
    }

    private boolean union(int x, int y) {
        int px = find(x), py = find(y);
        if (px == py) return false;
        if (rank[px] < rank[py]) parent[px] = py;
        else if (rank[px] > rank[py]) parent[py] = px;
        else { parent[py] = px; rank[px]++; }
        return true;
    }
}
```

### Test Cases
```java
// Test 1: Valid tree
int[][] edges1 = {{0,1},{0,2},{0,3},{1,4}};
assert new GraphValidTree().validTree(5, edges1) == true;

// Test 2: Contains a cycle
int[][] edges2 = {{0,1},{1,2},{2,3},{1,3},{1,4}};
assert new GraphValidTree().validTree(5, edges2) == false;

// Test 3: Disconnected (too few edges)
int[][] edges3 = {{0,1},{2,3}};
assert new GraphValidTree().validTree(5, edges3) == false;
```

---

## 317. Shortest Distance from All Buildings

### Problem Statement
You are given an `m x n` grid where:
- `0` = empty land
- `1` = building
- `2` = obstacle

You want to build a house on an empty land that **minimizes the total travel distance** to all buildings. Return this minimum distance, or `-1` if it is not possible.

### Java Solution
```java
import java.util.LinkedList;
import java.util.Queue;

class ShortestDistanceFromAllBuildings {
    private static final int[][] DIRS = {{0,1},{0,-1},{1,0},{-1,0}};

    public int shortestDistance(int[][] grid) {
        int m = grid.length, n = grid[0].length;
        int[][] dist = new int[m][n];
        int[][] reach = new int[m][n];
        int buildingCount = 0;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1) {
                    buildingCount++;
                    bfs(grid, dist, reach, i, j, m, n);
                }
            }
        }

        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                if (grid[i][j] == 0 && reach[i][j] == buildingCount)
                    minDist = Math.min(minDist, dist[i][j]);

        return minDist == Integer.MAX_VALUE ? -1 : minDist;
    }

    private void bfs(int[][] grid, int[][] dist, int[][] reach, int r, int c, int m, int n) {
        boolean[][] visited = new boolean[m][n];
        Queue<int[]> queue = new LinkedList<>();
        queue.offer(new int[]{r, c, 0});
        visited[r][c] = true;
        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            for (int[] dir : DIRS) {
                int nr = curr[0] + dir[0], nc = curr[1] + dir[1];
                if (nr >= 0 && nr < m && nc >= 0 && nc < n && !visited[nr][nc] && grid[nr][nc] == 0) {
                    visited[nr][nc] = true;
                    dist[nr][nc] += curr[2] + 1;
                    reach[nr][nc]++;
                    queue.offer(new int[]{nr, nc, curr[2] + 1});
                }
            }
        }
    }
}
```

### Test Cases
```java
// Test 1: Standard case
int[][] grid1 = {{1,0,2,0,1},{0,0,0,0,0},{0,0,1,0,0}};
assert new ShortestDistanceFromAllBuildings().shortestDistance(grid1) == 7;

// Test 2: Single building, single empty land
int[][] grid2 = {{1,0}};
assert new ShortestDistanceFromAllBuildings().shortestDistance(grid2) == 1;

// Test 3: No reachable empty land (obstacles block all paths)
int[][] grid3 = {{1,2,1},{2,0,2},{1,2,1}};
assert new ShortestDistanceFromAllBuildings().shortestDistance(grid3) == -1;
```

---

## 285. Inorder Successor in BST

### Problem Statement
Given the `root` of a BST and a node `p`, return the **in-order successor** of that node in the BST. The in-order successor of node `p` is the node with the smallest key **greater than** `p.val`. Return `null` if no such node exists.

### Java Solution
```java
class InorderSuccessorBST {
    public static class TreeNode {
        int val;
        TreeNode left, right;
        TreeNode(int val) { this.val = val; }
    }

    public TreeNode inorderSuccessor(TreeNode root, TreeNode p) {
        TreeNode successor = null;
        while (root != null) {
            if (p.val < root.val) {
                successor = root;   // root could be the answer
                root = root.left;   // try to find a closer (smaller) successor
            } else {
                root = root.right;  // p.val >= root.val, move right
            }
        }
        return successor;
    }
}
```

### Test Cases
```java
// Test 1: Successor exists to the right
//       5
//      / \
//     3   6
//    / \
//   2   4
// p=3 → successor=4
TreeNode root1 = new TreeNode(5);
root1.left = new TreeNode(3); root1.right = new TreeNode(6);
root1.left.left = new TreeNode(2); root1.left.right = new TreeNode(4);
TreeNode p1 = root1.left; // node 3
assert new InorderSuccessorBST().inorderSuccessor(root1, p1).val == 4;

// Test 2: p is the max → no successor
TreeNode root2 = new TreeNode(5);
root2.left = new TreeNode(3); root2.right = new TreeNode(6);
TreeNode p2 = root2.right; // node 6
assert new InorderSuccessorBST().inorderSuccessor(root2, p2) == null;

// Test 3: Successor is an ancestor
// p=4, successor=5 (the root)
TreeNode root3 = new TreeNode(5);
root3.left = new TreeNode(3); root3.right = new TreeNode(6);
root3.left.right = new TreeNode(4);
TreeNode p3 = root3.left.right; // node 4
assert new InorderSuccessorBST().inorderSuccessor(root3, p3).val == 5;
```

---

## 314. Binary Tree Vertical Order Traversal

### Problem Statement
Given the `root` of a binary tree, return the **vertical order traversal** of its nodes' values. For each node at position `(row, col)`, its left child is at `(row+1, col-1)` and right child is at `(row+1, col+1)`. The root is at column `0`. Return a list of columns from left to right, with nodes within each column ordered from top to bottom.

### Java Solution
```java
import java.util.*;

class BinaryTreeVerticalOrderTraversal {
    public static class TreeNode {
        int val;
        TreeNode left, right;
        TreeNode(int val) { this.val = val; }
    }

    public List<List<Integer>> verticalOrder(TreeNode root) {
        List<List<Integer>> result = new ArrayList<>();
        if (root == null) return result;

        Map<Integer, List<Integer>> colMap = new TreeMap<>();
        Queue<int[]> queue = new LinkedList<>();    // [node index via map]
        Queue<TreeNode> nodeQueue = new LinkedList<>();

        nodeQueue.offer(root);
        queue.offer(new int[]{0}); // column index

        while (!nodeQueue.isEmpty()) {
            TreeNode node = nodeQueue.poll();
            int col = queue.poll()[0];

            colMap.computeIfAbsent(col, k -> new ArrayList<>()).add(node.val);

            if (node.left != null)  { nodeQueue.offer(node.left);  queue.offer(new int[]{col - 1}); }
            if (node.right != null) { nodeQueue.offer(node.right); queue.offer(new int[]{col + 1}); }
        }

        result.addAll(colMap.values());
        return result;
    }
}
```

### Test Cases
```java
BinaryTreeVerticalOrderTraversal solver = new BinaryTreeVerticalOrderTraversal();

// Test 1:
//     3
//    / \
//   9  20
//     /  \
//    15   7
// Expected columns: [-1]=[9], [0]=[3,15], [1]=[20], [2]=[7]
TreeNode root1 = new TreeNode(3);
root1.left = new TreeNode(9); root1.right = new TreeNode(20);
root1.right.left = new TreeNode(15); root1.right.right = new TreeNode(7);
List<List<Integer>> res1 = solver.verticalOrder(root1);
assert res1.get(0).equals(List.of(9));
assert res1.get(1).equals(List.of(3, 15));
assert res1.get(2).equals(List.of(20));
assert res1.get(3).equals(List.of(7));

// Test 2: Single node → one column with one element
TreeNode root2 = new TreeNode(1);
List<List<Integer>> res2 = solver.verticalOrder(root2);
assert res2.size() == 1 && res2.get(0).equals(List.of(1));

// Test 3: Left-skewed with right child
//   1           col 0 = [1, 3]
//  /            col-1 = [2]
// 2
//  \
//   3
TreeNode root3 = new TreeNode(1);
root3.left = new TreeNode(2);
root3.left.right = new TreeNode(3);
List<List<Integer>> res3 = solver.verticalOrder(root3);
assert res3.get(0).equals(List.of(2));
assert res3.get(1).equals(List.of(1, 3));
```

---

## 426. Convert BST to Sorted Doubly Linked List

### Problem Statement
Convert a **Binary Search Tree** to a **sorted circular doubly linked list** in-place. The left pointer acts as the `prev` pointer and the right pointer acts as the `next` pointer. The "smallest" element's `prev` points to the "largest", and the "largest" element's `next` points to the "smallest" — forming a circle. Return the pointer to the smallest element.

### Java Solution
```java
class ConvertBSTtoSortedDLL {
    static class Node {
        int val;
        Node left, right;
        Node(int val) { this.val = val; }
    }

    private Node first = null;  // smallest node
    private Node last  = null;  // previously visited node

    public Node treeToDoublyList(Node root) {
        if (root == null) return null;
        inorder(root);
        // Close the circle
        last.right = first;
        first.left = last;
        return first;
    }

    private void inorder(Node node) {
        if (node == null) return;
        inorder(node.left);

        if (last != null) {
            last.right = node;
            node.left  = last;
        } else {
            first = node; // leftmost = smallest
        }
        last = node;

        inorder(node.right);
    }
}
```

### Test Cases
```java
// Test 1: Standard BST
//     4
//    / \
//   2   5
//  / \
// 1   3
// Doubly linked: 1 <-> 2 <-> 3 <-> 4 <-> 5 (circular)
Node root1 = new Node(4);
root1.left = new Node(2); root1.right = new Node(5);
root1.left.left = new Node(1); root1.left.right = new Node(3);
Node head1 = new ConvertBSTtoSortedDLL().treeToDoublyList(root1);
assert head1.val == 1;
// Traverse: 1->2->3->4->5->back to 1

// Test 2: Single node → points to itself
Node single = new Node(1);
Node head2 = new ConvertBSTtoSortedDLL().treeToDoublyList(single);
assert head2.left == head2 && head2.right == head2;

// Test 3: Two-node BST
Node root3 = new Node(2);
root3.left = new Node(1);
Node head3 = new ConvertBSTtoSortedDLL().treeToDoublyList(root3);
assert head3.val == 1 && head3.right.val == 2;
```

---

## 362. Design Hit Counter

### Problem Statement
Design a hit counter which counts the number of hits received in the **past 5 minutes** (i.e., the past `300` seconds). Implement the `HitCounter` class:
- `HitCounter()` initializes the object.
- `void hit(int timestamp)` records a hit at the given timestamp (in seconds).
- `int getHits(int timestamp)` returns total hits in `[timestamp - 299, timestamp]`.

Timestamps are non-decreasing. Multiple hits at the same timestamp are allowed.

### Java Solution
```java
class HitCounter {
    private int[] times;
    private int[] hits;
    private static final int SIZE = 300;

    public HitCounter() {
        times = new int[SIZE];
        hits  = new int[SIZE];
    }

    public void hit(int timestamp) {
        int idx = timestamp % SIZE;
        if (times[idx] != timestamp) {
            times[idx] = timestamp;
            hits[idx]  = 1;
        } else {
            hits[idx]++;
        }
    }

    public int getHits(int timestamp) {
        int total = 0;
        for (int i = 0; i < SIZE; i++) {
            if (timestamp - times[i] < SIZE) {
                total += hits[i];
            }
        }
        return total;
    }
}
```

### Test Cases
```java
// Test 1: Basic hits within window
HitCounter hc1 = new HitCounter();
hc1.hit(1); hc1.hit(2); hc1.hit(3);
assert hc1.getHits(4)   == 3;
assert hc1.getHits(300) == 3;
assert hc1.getHits(301) == 2; // hit at t=1 falls out of window

// Test 2: Multiple hits at same timestamp
HitCounter hc2 = new HitCounter();
hc2.hit(1); hc2.hit(1); hc2.hit(1);
assert hc2.getHits(1) == 3;

// Test 3: Hits beyond 300s window expire
HitCounter hc3 = new HitCounter();
hc3.hit(1);
assert hc3.getHits(301) == 0; // t=1 is 300s before t=301, just outside window
```

---

## 642. Design Search Autocomplete System

### Problem Statement
Design a search autocomplete system. Users type a query character by character, and after each character the system returns the **top 3** historical sentences (by frequency, or lexicographically if tied). Implement:
- `AutocompleteSystem(String[] sentences, int[] times)` — initializes with historical data.
- `List<String> input(char c)` — inputs a character. If `c == '#'`, the current query is saved and `[]` is returned; otherwise return top 3 matching suggestions.

### Java Solution
```java
import java.util.*;

class AutocompleteSystem {
    private Map<String, Integer> freq = new HashMap<>();
    private String prefix = "";

    public AutocompleteSystem(String[] sentences, int[] times) {
        for (int i = 0; i < sentences.length; i++)
            freq.put(sentences[i], freq.getOrDefault(sentences[i], 0) + times[i]);
    }

    public List<String> input(char c) {
        if (c == '#') {
            freq.put(prefix, freq.getOrDefault(prefix, 0) + 1);
            prefix = "";
            return new ArrayList<>();
        }

        prefix += c;

        // Collect all sentences that start with prefix
        PriorityQueue<String> heap = new PriorityQueue<>((a, b) -> {
            int diff = freq.get(b) - freq.get(a);
            return diff != 0 ? diff : a.compareTo(b);
        });

        for (String sentence : freq.keySet()) {
            if (sentence.startsWith(prefix)) heap.offer(sentence);
        }

        List<String> result = new ArrayList<>();
        for (int i = 0; i < 3 && !heap.isEmpty(); i++) result.add(heap.poll());
        return result;
    }
}
```

### Test Cases
```java
// Test 1: Top suggestions sorted by frequency
String[] sentences = {"i love you", "island", "iroman", "i love leetcode"};
int[] times = {5, 3, 2, 2};
AutocompleteSystem ac = new AutocompleteSystem(sentences, times);
List<String> r1 = ac.input('i');
// Frequencies: "i love you"=5, "island"=3, "i love leetcode"=2, "iroman"=2
assert r1.get(0).equals("i love you");
assert r1.size() == 3;

// Test 2: Continuing prefix narrows candidates
List<String> r2 = ac.input(' ');
// Only "i love you" (5) and "i love leetcode" (2) start with "i "
assert r2.size() == 2;
assert r2.get(0).equals("i love you");
assert r2.get(1).equals("i love leetcode");

// Test 3: '#' saves the current sentence; new autocomplete instance verifies saved sentence reappears
ac.input('n'); ac.input('e'); ac.input('w'); // prefix is now "i new"
List<String> saved = ac.input('#');          // saves "i new" with freq=1
assert saved.isEmpty();

// Fresh query — "i new" (freq=1) stored; verify it appears when no higher-freq competitor
AutocompleteSystem ac2 = new AutocompleteSystem(new String[]{"i new"}, new int[]{1});
List<String> r3 = ac2.input('i');
assert r3.contains("i new");  // only one sentence → must be in results

List<String> r4 = ac2.input(' ');
assert r4.contains("i new");  // "i new" starts with "i "

ac2.input('n'); ac2.input('e'); ac2.input('w');
ac2.input('#'); // save "i new" again → freq becomes 2

List<String> r5 = ac2.input('i');
assert r5.contains("i new"); // still appears, now with freq=2
```

---

## 346. Moving Average from Data Stream

### Problem Statement
Given a stream of integers and a window size `size`, calculate the **moving average** of all integers in the sliding window. Implement:
- `MovingAverage(int size)` — initializes the object with the given window size.
- `double next(int val)` — returns the moving average of the last `size` values.

### Java Solution
```java
import java.util.LinkedList;
import java.util.Queue;

class MovingAverage {
    private final int size;
    private final Queue<Integer> window;
    private double sum;

    public MovingAverage(int size) {
        this.size   = size;
        this.window = new LinkedList<>();
        this.sum    = 0;
    }

    public double next(int val) {
        if (window.size() == size) {
            sum -= window.poll(); // Remove oldest element
        }
        window.offer(val);
        sum += val;
        return sum / window.size();
    }
}
```

### Test Cases
```java
// Test 1: Standard sliding window
MovingAverage ma1 = new MovingAverage(3);
assert ma1.next(1) == 1.0;           // window=[1],      avg=1.0
assert ma1.next(10) == 5.5;          // window=[1,10],   avg=5.5
assert ma1.next(3) == (14.0/3);      // window=[1,10,3], avg≈4.67
assert ma1.next(5) == (18.0/3);      // window=[10,3,5], avg=6.0

// Test 2: Window size 1 (always returns last value)
MovingAverage ma2 = new MovingAverage(1);
assert ma2.next(4) == 4.0;
assert ma2.next(9) == 9.0;

// Test 3: Window larger than data so far
MovingAverage ma3 = new MovingAverage(5);
assert ma3.next(2) == 2.0;
assert ma3.next(4) == 3.0;  // (2+4)/2
assert ma3.next(6) == 4.0;  // (2+4+6)/3
```

