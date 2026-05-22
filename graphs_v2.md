# LeetCode Graph Problems - Solutions in Java

---

## 94. Medium - Binary Tree Inorder Traversal

### Problem Statement
Given the root of a binary tree, return its inorder traversal. Inorder visits the left subtree, then the node, then the right subtree.

### Test Cases
**Test Case 1**
- Input: root = [1,null,2,3]
- Output: [1,3,2]
- Explanation: Visit 1, then the left side of 2, then 2.

**Test Case 2**
- Input: root = []
- Output: []
- Explanation: An empty tree has no traversal.

**Test Case 3**
- Input: root = [1]
- Output: [1]
- Explanation: The only node is returned.

### Java Solution
```java
import java.util.*;

class Solution {
    public List<Integer> inorderTraversal(TreeNode root) {
        List<Integer> ans = new ArrayList<>();
        Deque<TreeNode> stack = new ArrayDeque<>();
        TreeNode curr = root;
        while (curr != null || !stack.isEmpty()) {
            while (curr != null) {
                stack.push(curr);
                curr = curr.left;
            }
            curr = stack.pop();
            ans.add(curr.val);
            curr = curr.right;
        }
        return ans;
    }
}

/* standard LeetCode TreeNode */
```

## 124. Hard - Binary Tree Maximum Path Sum

### Problem Statement
Given a binary tree, return the maximum path sum of any non-empty path. The path may start and end at any nodes but must follow parent-child edges.

### Test Cases
**Test Case 1**
- Input: root = [1,2,3]
- Output: 6
- Explanation: The best path is 2 -> 1 -> 3.

**Test Case 2**
- Input: root = [-10,9,20,null,null,15,7]
- Output: 42
- Explanation: The best path is 15 -> 20 -> 7.

**Test Case 3**
- Input: root = [-3]
- Output: -3
- Explanation: The single node is the only path.

### Java Solution
```java
class Solution {
    private int best = Integer.MIN_VALUE;

    public int maxPathSum(TreeNode root) {
        dfs(root);
        return best;
    }

    private int dfs(TreeNode node) {
        if (node == null) return 0;
        int left = Math.max(0, dfs(node.left));
        int right = Math.max(0, dfs(node.right));
        best = Math.max(best, node.val + left + right);
        return node.val + Math.max(left, right);
    }
}
```

## 126. Hard - Word Ladder II

### Problem Statement
Return all shortest transformation sequences from beginWord to endWord. Each step changes exactly one letter and every intermediate word must be in the dictionary.

### Test Cases
**Test Case 1**
- Input: beginWord = "hit", endWord = "cog", wordList = ["hot","dot","dog","lot","log","cog"]
- Output: [["hit","hot","dot","dog","cog"],["hit","hot","lot","log","cog"]]
- Explanation: Both sequences have minimum length.

**Test Case 2**
- Input: beginWord = "hit", endWord = "cog", wordList = ["hot","dot","dog","lot","log"]
- Output: []
- Explanation: The end word is missing, so no sequence exists.

**Test Case 3**
- Input: beginWord = "red", endWord = "tax", wordList = ["ted","tex","red","tax","tad","den","rex","pee"]
- Output: [["red","ted","tad","tax"],["red","ted","tex","tax"],["red","rex","tex","tax"]]
- Explanation: These are all shortest valid sequences.

### Java Solution
```java
import java.util.*;

class Solution {
    public List<List<String>> findLadders(String beginWord, String endWord, List<String> wordList) {
        Set<String> dict = new HashSet<>(wordList);
        List<List<String>> ans = new ArrayList<>();
        if (!dict.contains(endWord)) return ans;

        Map<String, List<String>> parents = new HashMap<>();
        Set<String> curr = new HashSet<>();
        curr.add(beginWord);
        boolean found = false;

        while (!curr.isEmpty() && !found) {
            dict.removeAll(curr);
            Set<String> next = new HashSet<>();
            for (String word : curr) {
                char[] chars = word.toCharArray();
                for (int i = 0; i < chars.length; i++) {
                    char old = chars[i];
                    for (char c = 'a'; c <= 'z'; c++) {
                        chars[i] = c;
                        String cand = new String(chars);
                        if (!dict.contains(cand)) continue;
                        next.add(cand);
                        parents.computeIfAbsent(cand, k -> new ArrayList<>()).add(word);
                        if (cand.equals(endWord)) found = true;
                    }
                    chars[i] = old;
                }
            }
            curr = next;
        }

        if (!found) return ans;
        LinkedList<String> path = new LinkedList<>();
        path.add(endWord);
        backtrack(endWord, beginWord, parents, path, ans);
        return ans;
    }

    private void backtrack(String word, String beginWord, Map<String, List<String>> parents,
                           LinkedList<String> path, List<List<String>> ans) {
        if (word.equals(beginWord)) {
            List<String> seq = new ArrayList<>(path);
            Collections.reverse(seq);
            ans.add(seq);
            return;
        }
        for (String parentWord : parents.getOrDefault(word, Collections.emptyList())) {
            path.addLast(parentWord);
            backtrack(parentWord, beginWord, parents, path, ans);
            path.removeLast();
        }
    }
}
```

## 130. Medium - Surrounded Regions

### Problem Statement
Capture all regions of 'O' fully surrounded by 'X'. Any 'O' connected to a border must remain unchanged.

### Test Cases
**Test Case 1**
- Input: board = [["X","X","X","X"],["X","O","O","X"],["X","X","O","X"],["X","O","X","X"]]
- Output: [["X","X","X","X"],["X","X","X","X"],["X","X","X","X"],["X","O","X","X"]]
- Explanation: Only the bottom border region survives.

**Test Case 2**
- Input: board = [["X"]]
- Output: [["X"]]
- Explanation: Nothing changes.

**Test Case 3**
- Input: board = [["O","O"],["O","O"]]
- Output: [["O","O"],["O","O"]]
- Explanation: Every cell touches the border.

### Java Solution
```java
class Solution {
    private int m, n;
    private final int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

    public void solve(char[][] board) {
        m = board.length;
        if (m == 0) return;
        n = board[0].length;
        for (int i = 0; i < m; i++) {
            dfs(board, i, 0);
            dfs(board, i, n - 1);
        }
        for (int j = 0; j < n; j++) {
            dfs(board, 0, j);
            dfs(board, m - 1, j);
        }
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (board[i][j] == 'O') board[i][j] = 'X';
                else if (board[i][j] == '#') board[i][j] = 'O';
            }
        }
    }

    private void dfs(char[][] board, int r, int c) {
        if (r < 0 || c < 0 || r >= m || c >= n || board[r][c] != 'O') return;
        board[r][c] = '#';
        for (int[] d : dirs) dfs(board, r + d[0], c + d[1]);
    }
}
```

## 144. Medium - Binary Tree Preorder Traversal

### Problem Statement
Given a binary tree, return its preorder traversal. Preorder visits node, then left subtree, then right subtree.

### Test Cases
**Test Case 1**
- Input: root = [1,null,2,3]
- Output: [1,2,3]
- Explanation: Process each node before its children.

**Test Case 2**
- Input: root = []
- Output: []
- Explanation: Empty tree gives an empty list.

**Test Case 3**
- Input: root = [1,2,3,4,5]
- Output: [1,2,4,5,3]
- Explanation: Root-first order is preserved.

### Java Solution
```java
import java.util.*;

class Solution {
    public List<Integer> preorderTraversal(TreeNode root) {
        List<Integer> ans = new ArrayList<>();
        if (root == null) return ans;
        Deque<TreeNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            TreeNode node = stack.pop();
            ans.add(node.val);
            if (node.right != null) stack.push(node.right);
            if (node.left != null) stack.push(node.left);
        }
        return ans;
    }
}
```

## 145. Easy - Binary Tree Postorder Traversal

### Problem Statement
Given a binary tree, return its postorder traversal. Postorder visits left subtree, right subtree, then node.

### Test Cases
**Test Case 1**
- Input: root = [1,null,2,3]
- Output: [3,2,1]
- Explanation: Children are visited before parents.

**Test Case 2**
- Input: root = []
- Output: []
- Explanation: No nodes are present.

**Test Case 3**
- Input: root = [1,2,3]
- Output: [2,3,1]
- Explanation: Both children appear before the root.

### Java Solution
```java
import java.util.*;

class Solution {
    public List<Integer> postorderTraversal(TreeNode root) {
        LinkedList<Integer> ans = new LinkedList<>();
        if (root == null) return ans;
        Deque<TreeNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            TreeNode node = stack.pop();
            ans.addFirst(node.val);
            if (node.left != null) stack.push(node.left);
            if (node.right != null) stack.push(node.right);
        }
        return ans;
    }
}
```

## 210. Medium - Course Schedule II

### Problem Statement
Return any valid order to finish all courses given prerequisite pairs. If the dependency graph has a cycle, return an empty array.

### Test Cases
**Test Case 1**
- Input: numCourses = 2, prerequisites = [[1,0]]
- Output: [0,1]
- Explanation: Course 0 must come before course 1.

**Test Case 2**
- Input: numCourses = 4, prerequisites = [[1,0],[2,0],[3,1],[3,2]]
- Output: [0,1,2,3]
- Explanation: Any topological order is valid.

**Test Case 3**
- Input: numCourses = 2, prerequisites = [[0,1],[1,0]]
- Output: []
- Explanation: A cycle makes completion impossible.

### Java Solution
```java
import java.util.*;

class Solution {
    public int[] findOrder(int numCourses, int[][] prerequisites) {
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < numCourses; i++) graph.add(new ArrayList<>());
        int[] indegree = new int[numCourses];
        for (int[] p : prerequisites) {
            graph.get(p[1]).add(p[0]);
            indegree[p[0]]++;
        }
        Queue<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < numCourses; i++) if (indegree[i] == 0) q.offer(i);
        int[] order = new int[numCourses];
        int idx = 0;
        while (!q.isEmpty()) {
            int node = q.poll();
            order[idx++] = node;
            for (int next : graph.get(node)) {
                if (--indegree[next] == 0) q.offer(next);
            }
        }
        return idx == numCourses ? order : new int[0];
    }
}
```

## 235. Easy - Lowest Common Ancestor of a Binary Search Tree

### Problem Statement
Given a BST and two nodes, return their lowest common ancestor. Use the BST ordering to move left or right.

### Test Cases
**Test Case 1**
- Input: root = [6,2,8,0,4,7,9,null,null,3,5], p = 2, q = 8
- Output: 6
- Explanation: The nodes split at the root.

**Test Case 2**
- Input: root = [6,2,8,0,4,7,9,null,null,3,5], p = 2, q = 4
- Output: 2
- Explanation: A node can be its own ancestor.

**Test Case 3**
- Input: root = [2,1], p = 2, q = 1
- Output: 2
- Explanation: The root is the LCA.

### Java Solution
```java
class Solution {
    public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        TreeNode curr = root;
        while (curr != null) {
            if (p.val < curr.val && q.val < curr.val) curr = curr.left;
            else if (p.val > curr.val && q.val > curr.val) curr = curr.right;
            else return curr;
        }
        return null;
    }
}
```

## 236. Medium - Lowest Common Ancestor of a Binary Tree

### Problem Statement
Given a binary tree and two nodes, return their lowest common ancestor. The LCA is the deepest node that has both targets in its subtree.

### Test Cases
**Test Case 1**
- Input: root = [3,5,1,6,2,0,8,null,null,7,4], p = 5, q = 1
- Output: 3
- Explanation: The two targets lie in different branches.

**Test Case 2**
- Input: root = [3,5,1,6,2,0,8,null,null,7,4], p = 5, q = 4
- Output: 5
- Explanation: Node 5 is an ancestor of node 4.

**Test Case 3**
- Input: root = [1,2], p = 1, q = 2
- Output: 1
- Explanation: The root contains both nodes.

### Java Solution
```java
class Solution {
    public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        if (root == null || root == p || root == q) return root;
        TreeNode left = lowestCommonAncestor(root.left, p, q);
        TreeNode right = lowestCommonAncestor(root.right, p, q);
        if (left != null && right != null) return root;
        return left != null ? left : right;
    }
}
```

## 269. Hard - Alien Dictionary

### Problem Statement
Given words sorted by an unknown alphabet, return one valid character ordering or an empty string if no valid ordering exists.

### Test Cases
**Test Case 1**
- Input: words = ["wrt","wrf","er","ett","rftt"]
- Output: "wertf"
- Explanation: Adjacent words define the ordering constraints.

**Test Case 2**
- Input: words = ["z","x"]
- Output: "zx"
- Explanation: z must come before x.

**Test Case 3**
- Input: words = ["abc","ab"]
- Output: ""
- Explanation: A longer word cannot come before its prefix.

### Java Solution
```java
import java.util.*;

class Solution {
    public String alienOrder(String[] words) {
        Map<Character, Set<Character>> graph = new HashMap<>();
        Map<Character, Integer> indegree = new HashMap<>();
        for (String word : words) {
            for (char c : word.toCharArray()) {
                graph.putIfAbsent(c, new HashSet<>());
                indegree.putIfAbsent(c, 0);
            }
        }
        for (int i = 0; i < words.length - 1; i++) {
            String a = words[i], b = words[i + 1];
            if (a.length() > b.length() && a.startsWith(b)) return "";
            for (int j = 0; j < Math.min(a.length(), b.length()); j++) {
                char x = a.charAt(j), y = b.charAt(j);
                if (x != y) {
                    if (graph.get(x).add(y)) indegree.put(y, indegree.get(y) + 1);
                    break;
                }
            }
        }
        Queue<Character> q = new ArrayDeque<>();
        for (char c : indegree.keySet()) if (indegree.get(c) == 0) q.offer(c);
        StringBuilder sb = new StringBuilder();
        while (!q.isEmpty()) {
            char c = q.poll();
            sb.append(c);
            for (char next : graph.get(c)) {
                indegree.put(next, indegree.get(next) - 1);
                if (indegree.get(next) == 0) q.offer(next);
            }
        }
        return sb.length() == indegree.size() ? sb.toString() : "";
    }
}
```

## 297. Hard - Serialize and Deserialize Binary Tree

### Problem Statement
Design methods to serialize a binary tree into a string and deserialize it back to the original tree structure.

### Test Cases
**Test Case 1**
- Input: root = [1,2,3,null,null,4,5]
- Output: [1,2,3,null,null,4,5]
- Explanation: Serializing and then deserializing preserves the tree.

**Test Case 2**
- Input: root = []
- Output: []
- Explanation: The empty tree round-trips correctly.

**Test Case 3**
- Input: root = [1]
- Output: [1]
- Explanation: A single node is preserved.

### Java Solution
```java
import java.util.*;

public class Codec {
    public String serialize(TreeNode root) {
        StringBuilder sb = new StringBuilder();
        preorder(root, sb);
        return sb.toString();
    }

    private void preorder(TreeNode node, StringBuilder sb) {
        if (node == null) {
            sb.append("#,");
            return;
        }
        sb.append(node.val).append(',');
        preorder(node.left, sb);
        preorder(node.right, sb);
    }

    public TreeNode deserialize(String data) {
        Queue<String> q = new ArrayDeque<>(Arrays.asList(data.split(",")));
        return build(q);
    }

    private TreeNode build(Queue<String> q) {
        String val = q.poll();
        if (val.equals("#")) return null;
        TreeNode node = new TreeNode(Integer.parseInt(val));
        node.left = build(q);
        node.right = build(q);
        return node;
    }
}
```

## 314. Medium - Binary Tree Vertical Order Traversal

### Problem Statement
Return the vertical order traversal of a binary tree from leftmost column to rightmost column. Nodes in the same row and column must keep level-order appearance.

### Test Cases
**Test Case 1**
- Input: root = [3,9,20,null,null,15,7]
- Output: [[9],[3,15],[20],[7]]
- Explanation: Columns are reported from left to right.

**Test Case 2**
- Input: root = [3,9,8,4,0,1,7]
- Output: [[4],[9],[3,0,1],[8],[7]]
- Explanation: Same-column nodes follow BFS order.

**Test Case 3**
- Input: root = [1,2,3,4,5,6,7]
- Output: [[4],[2],[1,5,6],[3],[7]]
- Explanation: The center column contains root and deeper nodes.

### Java Solution
```java
import java.util.*;

class Solution {
    static class Pair {
        TreeNode node;
        int col;
        Pair(TreeNode node, int col) {
            this.node = node;
            this.col = col;
        }
    }

    public List<List<Integer>> verticalOrder(TreeNode root) {
        List<List<Integer>> ans = new ArrayList<>();
        if (root == null) return ans;
        Map<Integer, List<Integer>> map = new HashMap<>();
        Queue<Pair> q = new ArrayDeque<>();
        q.offer(new Pair(root, 0));
        int min = 0, max = 0;
        while (!q.isEmpty()) {
            Pair p = q.poll();
            map.computeIfAbsent(p.col, k -> new ArrayList<>()).add(p.node.val);
            min = Math.min(min, p.col);
            max = Math.max(max, p.col);
            if (p.node.left != null) q.offer(new Pair(p.node.left, p.col - 1));
            if (p.node.right != null) q.offer(new Pair(p.node.right, p.col + 1));
        }
        for (int c = min; c <= max; c++) ans.add(map.get(c));
        return ans;
    }
}
```

## 317. Hard - Shortest Distance from All Buildings

### Problem Statement
In a grid with buildings, empty land, and obstacles, find the empty cell with minimum total distance to all buildings. Return -1 if no such cell can reach every building.

### Test Cases
**Test Case 1**
- Input: grid = [[1,0,2,0,1],[0,0,0,0,0],[0,0,1,0,0]]
- Output: 7
- Explanation: The best empty cell is at row 1, column 2.

**Test Case 2**
- Input: grid = [[1,0]]
- Output: 1
- Explanation: The only empty cell is one step away.

**Test Case 3**
- Input: grid = [[1,2,0]]
- Output: -1
- Explanation: The obstacle blocks access to the empty land.

### Java Solution
```java
import java.util.*;

class Solution {
    private final int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

    public int shortestDistance(int[][] grid) {
        int m = grid.length, n = grid[0].length;
        int[][] dist = new int[m][n];
        int[][] reach = new int[m][n];
        int buildings = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1) {
                    buildings++;
                    bfs(grid, i, j, dist, reach);
                }
            }
        }
        int ans = Integer.MAX_VALUE;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 0 && reach[i][j] == buildings) ans = Math.min(ans, dist[i][j]);
            }
        }
        return ans == Integer.MAX_VALUE ? -1 : ans;
    }

    private void bfs(int[][] grid, int sr, int sc, int[][] dist, int[][] reach) {
        int m = grid.length, n = grid[0].length;
        boolean[][] seen = new boolean[m][n];
        Queue<int[]> q = new ArrayDeque<>();
        q.offer(new int[]{sr, sc});
        seen[sr][sc] = true;
        int steps = 0;
        while (!q.isEmpty()) {
            for (int size = q.size(); size > 0; size--) {
                int[] cur = q.poll();
                int r = cur[0], c = cur[1];
                if (grid[r][c] == 0) {
                    dist[r][c] += steps;
                    reach[r][c]++;
                }
                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr < 0 || nc < 0 || nr >= m || nc >= n || seen[nr][nc] || grid[nr][nc] != 0) continue;
                    seen[nr][nc] = true;
                    q.offer(new int[]{nr, nc});
                }
            }
            steps++;
        }
    }
}
```

## 332. Medium - Reconstruct Itinerary

### Problem Statement
Given airline tickets [from, to], reconstruct the itinerary that uses all tickets exactly once and starts at "JFK". If several itineraries are valid, return the lexicographically smallest one.

### Test Cases
**Test Case 1**
- Input: tickets = [["MUC","LHR"],["JFK","MUC"],["SFO","SJC"],["LHR","SFO"]]
- Output: ["JFK","MUC","LHR","SFO","SJC"]
- Explanation: There is only one valid route.

**Test Case 2**
- Input: tickets = [["JFK","SFO"],["JFK","ATL"],["SFO","ATL"],["ATL","JFK"],["ATL","SFO"]]
- Output: ["JFK","ATL","JFK","SFO","ATL","SFO"]
- Explanation: This is the smallest lexical Eulerian path.

**Test Case 3**
- Input: tickets = [["JFK","KUL"],["JFK","NRT"],["NRT","JFK"]]
- Output: ["JFK","NRT","JFK","KUL"]
- Explanation: All tickets must be used exactly once.

### Java Solution
```java
import java.util.*;

class Solution {
    private Map<String, PriorityQueue<String>> graph = new HashMap<>();
    private LinkedList<String> route = new LinkedList<>();

    public List<String> findItinerary(List<List<String>> tickets) {
        for (List<String> t : tickets) {
            graph.computeIfAbsent(t.get(0), k -> new PriorityQueue<>()).offer(t.get(1));
        }
        dfs("JFK");
        return route;
    }

    private void dfs(String airport) {
        PriorityQueue<String> pq = graph.get(airport);
        while (pq != null && !pq.isEmpty()) dfs(pq.poll());
        route.addFirst(airport);
    }
}
```

## 365. Medium - Water and Jug Problem

### Problem Statement
Given two jugs with fixed capacities, determine whether it is possible to measure exactly targetCapacity liters using fill, empty, and pour operations.

### Test Cases
**Test Case 1**
- Input: jug1Capacity = 3, jug2Capacity = 5, targetCapacity = 4
- Output: true
- Explanation: The classic 3-and-5 process measures 4 liters.

**Test Case 2**
- Input: jug1Capacity = 2, jug2Capacity = 6, targetCapacity = 5
- Output: false
- Explanation: Only multiples of gcd(2,6) are measurable.

**Test Case 3**
- Input: jug1Capacity = 1, jug2Capacity = 2, targetCapacity = 3
- Output: true
- Explanation: Filling both jugs gives 3 liters total.

### Java Solution
```java
class Solution {
    public boolean canMeasureWater(int jug1Capacity, int jug2Capacity, int targetCapacity) {
        if (targetCapacity == 0) return true;
        if (jug1Capacity + jug2Capacity < targetCapacity) return false;
        return targetCapacity % gcd(jug1Capacity, jug2Capacity) == 0;
    }

    private int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
```

## 399. Hard - Evaluate Division

### Problem Statement
Given equations such as a / b = value and queries about ratios, return each query result or -1.0 if the value cannot be determined.

### Test Cases
**Test Case 1**
- Input: equations = [["a","b"],["b","c"]], values = [2.0,3.0], queries = [["a","c"],["b","a"],["a","e"],["a","a"],["x","x"]]
- Output: [6.0,0.5,-1.0,1.0,-1.0]
- Explanation: Ratios are found by multiplying along graph edges.

**Test Case 2**
- Input: equations = [["a","b"],["b","c"],["bc","cd"]], values = [1.5,2.5,5.0], queries = [["a","c"],["c","b"],["bc","cd"],["cd","bc"]]
- Output: [3.75,0.4,5.0,0.2]
- Explanation: Each query follows a reachable weighted path.

**Test Case 3**
- Input: equations = [["a","b"]], values = [0.5], queries = [["a","b"],["b","a"],["a","c"],["x","y"]]
- Output: [0.5,2.0,-1.0,-1.0]
- Explanation: Unknown variables produce -1.0.

### Java Solution
```java
import java.util.*;

class Solution {
    public double[] calcEquation(List<List<String>> equations, double[] values, List<List<String>> queries) {
        Map<String, Map<String, Double>> graph = new HashMap<>();
        for (int i = 0; i < equations.size(); i++) {
            String a = equations.get(i).get(0), b = equations.get(i).get(1);
            graph.computeIfAbsent(a, k -> new HashMap<>()).put(b, values[i]);
            graph.computeIfAbsent(b, k -> new HashMap<>()).put(a, 1.0 / values[i]);
        }
        double[] ans = new double[queries.size()];
        for (int i = 0; i < queries.size(); i++) {
            String s = queries.get(i).get(0), t = queries.get(i).get(1);
            if (!graph.containsKey(s) || !graph.containsKey(t)) ans[i] = -1.0;
            else if (s.equals(t)) ans[i] = 1.0;
            else ans[i] = dfs(s, t, 1.0, graph, new HashSet<>());
        }
        return ans;
    }

    private double dfs(String cur, String target, double product,
                       Map<String, Map<String, Double>> graph, Set<String> seen) {
        if (cur.equals(target)) return product;
        seen.add(cur);
        for (Map.Entry<String, Double> next : graph.get(cur).entrySet()) {
            if (seen.contains(next.getKey())) continue;
            double val = dfs(next.getKey(), target, product * next.getValue(), graph, seen);
            if (val != -1.0) return val;
        }
        return -1.0;
    }
}
```

## 407. Hard - Trapping Rain Water II

### Problem Statement
Given a 2D height map, return the total water trapped after raining. Water escapes from the boundary, so process from the boundary inward.

### Test Cases
**Test Case 1**
- Input: heightMap = [[1,4,3,1,3,2],[3,2,1,3,2,4],[2,3,3,2,3,1]]
- Output: 4
- Explanation: Four units of water are trapped in the inner basin.

**Test Case 2**
- Input: heightMap = [[3,3,3,3,3],[3,2,2,2,3],[3,2,1,2,3],[3,2,2,2,3],[3,3,3,3,3]]
- Output: 10
- Explanation: The center region fills up to height 3.

**Test Case 3**
- Input: heightMap = [[1]]
- Output: 0
- Explanation: A single cell cannot trap water.

### Java Solution
```java
import java.util.*;

class Solution {
    static class Cell {
        int r, c, h;
        Cell(int r, int c, int h) {
            this.r = r;
            this.c = c;
            this.h = h;
        }
    }

    public int trapRainWater(int[][] heightMap) {
        int m = heightMap.length, n = heightMap[0].length;
        if (m < 3 || n < 3) return 0;
        boolean[][] seen = new boolean[m][n];
        PriorityQueue<Cell> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a.h));
        for (int i = 0; i < m; i++) {
            pq.offer(new Cell(i, 0, heightMap[i][0]));
            pq.offer(new Cell(i, n - 1, heightMap[i][n - 1]));
            seen[i][0] = seen[i][n - 1] = true;
        }
        for (int j = 1; j < n - 1; j++) {
            pq.offer(new Cell(0, j, heightMap[0][j]));
            pq.offer(new Cell(m - 1, j, heightMap[m - 1][j]));
            seen[0][j] = seen[m - 1][j] = true;
        }
        int water = 0;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!pq.isEmpty()) {
            Cell cur = pq.poll();
            for (int[] d : dirs) {
                int nr = cur.r + d[0], nc = cur.c + d[1];
                if (nr < 0 || nc < 0 || nr >= m || nc >= n || seen[nr][nc]) continue;
                seen[nr][nc] = true;
                water += Math.max(0, cur.h - heightMap[nr][nc]);
                pq.offer(new Cell(nr, nc, Math.max(cur.h, heightMap[nr][nc])));
            }
        }
        return water;
    }
}
```

## 417. Medium - Pacific Atlantic Water Flow

### Problem Statement
Return all coordinates from which water can flow to both the Pacific and Atlantic oceans. Water moves from a cell to neighbors with height less than or equal to the current cell.

### Test Cases
**Test Case 1**
- Input: heights = [[1,2,2,3,5],[3,2,3,4,4],[2,4,5,3,1],[6,7,1,4,5],[5,1,1,2,4]]
- Output: [[0,4],[1,3],[1,4],[2,2],[3,0],[3,1],[4,0]]
- Explanation: These cells can reach both borders.

**Test Case 2**
- Input: heights = [[1]]
- Output: [[0,0]]
- Explanation: The single cell touches both oceans.

**Test Case 3**
- Input: heights = [[2,1],[1,2]]
- Output: [[0,0],[0,1],[1,0],[1,1]]
- Explanation: Every cell can reach both sides.

### Java Solution
```java
import java.util.*;

class Solution {
    private int m, n;
    private final int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

    public List<List<Integer>> pacificAtlantic(int[][] heights) {
        m = heights.length;
        n = heights[0].length;
        boolean[][] pac = new boolean[m][n];
        boolean[][] atl = new boolean[m][n];
        for (int i = 0; i < m; i++) {
            dfs(heights, pac, i, 0);
            dfs(heights, atl, i, n - 1);
        }
        for (int j = 0; j < n; j++) {
            dfs(heights, pac, 0, j);
            dfs(heights, atl, m - 1, j);
        }
        List<List<Integer>> ans = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (pac[i][j] && atl[i][j]) ans.add(Arrays.asList(i, j));
            }
        }
        return ans;
    }

    private void dfs(int[][] h, boolean[][] seen, int r, int c) {
        seen[r][c] = true;
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            if (nr < 0 || nc < 0 || nr >= m || nc >= n || seen[nr][nc]) continue;
            if (h[nr][nc] < h[r][c]) continue;
            dfs(h, seen, nr, nc);
        }
    }
}
```

## 465. Hard - Optimal Account Balancing

### Problem Statement
Given transactions between people, return the minimum number of transactions required to settle all resulting debts.

### Test Cases
**Test Case 1**
- Input: transactions = [[0,1,10],[2,0,5]]
- Output: 2
- Explanation: Two transfers settle the net balances.

**Test Case 2**
- Input: transactions = [[0,1,10],[1,0,1],[1,2,5],[2,0,5]]
- Output: 1
- Explanation: After netting, one payment is enough.

**Test Case 3**
- Input: transactions = [[0,1,5],[0,2,5],[1,2,5]]
- Output: 1
- Explanation: Only one final transfer is required after netting.

### Java Solution
```java
import java.util.*;

class Solution {
    public int minTransfers(int[][] transactions) {
        Map<Integer, Integer> balance = new HashMap<>();
        for (int[] t : transactions) {
            balance.put(t[0], balance.getOrDefault(t[0], 0) - t[2]);
            balance.put(t[1], balance.getOrDefault(t[1], 0) + t[2]);
        }
        List<Integer> debts = new ArrayList<>();
        for (int val : balance.values()) if (val != 0) debts.add(val);
        return dfs(0, debts);
    }

    private int dfs(int start, List<Integer> debts) {
        while (start < debts.size() && debts.get(start) == 0) start++;
        if (start == debts.size()) return 0;
        int ans = Integer.MAX_VALUE;
        for (int i = start + 1; i < debts.size(); i++) {
            if (debts.get(start) * debts.get(i) < 0) {
                debts.set(i, debts.get(i) + debts.get(start));
                ans = Math.min(ans, 1 + dfs(start + 1, debts));
                debts.set(i, debts.get(i) - debts.get(start));
                if (debts.get(i) + debts.get(start) == 0) break;
            }
        }
        return ans;
    }
}
```

## 684. Medium - Redundant Connection

### Problem Statement
Given an undirected graph that started as a tree with one extra edge added, return the extra edge that creates a cycle.

### Test Cases
**Test Case 1**
- Input: edges = [[1,2],[1,3],[2,3]]
- Output: [2,3]
- Explanation: The last edge closes the cycle.

**Test Case 2**
- Input: edges = [[1,2],[2,3],[3,4],[1,4],[1,5]]
- Output: [1,4]
- Explanation: Removing [1,4] restores a tree.

**Test Case 3**
- Input: edges = [[1,2],[2,3],[3,1]]
- Output: [3,1]
- Explanation: The third edge is redundant.

### Java Solution
```java
class Solution {
    static class DSU {
        int[] parent, rank;
        DSU(int n) {
            parent = new int[n + 1];
            rank = new int[n + 1];
            for (int i = 0; i <= n; i++) parent[i] = i;
        }
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        boolean union(int a, int b) {
            int pa = find(a), pb = find(b);
            if (pa == pb) return false;
            if (rank[pa] < rank[pb]) parent[pa] = pb;
            else if (rank[pa] > rank[pb]) parent[pb] = pa;
            else {
                parent[pb] = pa;
                rank[pa]++;
            }
            return true;
        }
    }

    public int[] findRedundantConnection(int[][] edges) {
        DSU dsu = new DSU(edges.length);
        for (int[] e : edges) if (!dsu.union(e[0], e[1])) return e;
        return new int[0];
    }
}
```

## 685. Hard - Redundant Connection II

### Problem Statement
A rooted tree with one extra directed edge is given. Return the edge that should be removed so the remaining graph is a valid rooted tree.

### Test Cases
**Test Case 1**
- Input: edges = [[1,2],[1,3],[2,3]]
- Output: [2,3]
- Explanation: Node 3 has two parents, so remove the later edge.

**Test Case 2**
- Input: edges = [[1,2],[2,3],[3,4],[4,1],[1,5]]
- Output: [4,1]
- Explanation: The issue is a directed cycle.

**Test Case 3**
- Input: edges = [[2,1],[3,1],[4,2],[1,4]]
- Output: [2,1]
- Explanation: The two-parent edge also participates in the cycle.

### Java Solution
```java
class Solution {
    static class DSU {
        int[] parent;
        DSU(int n) {
            parent = new int[n + 1];
            for (int i = 0; i <= n; i++) parent[i] = i;
        }
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        boolean union(int a, int b) {
            int pa = find(a), pb = find(b);
            if (pa == pb) return false;
            parent[pb] = pa;
            return true;
        }
    }

    public int[] findRedundantDirectedConnection(int[][] edges) {
        int n = edges.length;
        int[] parent = new int[n + 1];
        int[] candA = null, candB = null;
        int skip = -1;
        for (int i = 0; i < n; i++) {
            int u = edges[i][0], v = edges[i][1];
            if (parent[v] == 0) parent[v] = u;
            else {
                candA = new int[]{parent[v], v};
                candB = new int[]{u, v};
                skip = i;
            }
        }
        DSU dsu = new DSU(n);
        for (int i = 0; i < n; i++) {
            if (i == skip) continue;
            int u = edges[i][0], v = edges[i][1];
            if (!dsu.union(u, v)) return candA == null ? edges[i] : candA;
        }
        return candB;
    }
}
```

## 721. Medium - Accounts Merge

### Problem Statement
Merge accounts that belong to the same person when they share at least one email. Return merged accounts with emails sorted lexicographically.

### Test Cases
**Test Case 1**
- Input: accounts = [["John","johnsmith@mail.com","john_newyork@mail.com"],["John","johnsmith@mail.com","john00@mail.com"],["Mary","mary@mail.com"],["John","johnnybravo@mail.com"]]
- Output: [["John","john00@mail.com","john_newyork@mail.com","johnsmith@mail.com"],["John","johnnybravo@mail.com"],["Mary","mary@mail.com"]]
- Explanation: The first two accounts share an email and are merged.

**Test Case 2**
- Input: accounts = [["Alex","a@mail.com","b@mail.com"],["Alex","c@mail.com"],["Alex","b@mail.com","d@mail.com"]]
- Output: [["Alex","a@mail.com","b@mail.com","d@mail.com"],["Alex","c@mail.com"]]
- Explanation: b@mail.com links the first and third accounts.

**Test Case 3**
- Input: accounts = [["Mary","m1@mail.com"],["Mary","m2@mail.com"]]
- Output: [["Mary","m1@mail.com"],["Mary","m2@mail.com"]]
- Explanation: No shared email means no merge.

### Java Solution
```java
import java.util.*;

class Solution {
    static class DSU {
        int[] parent, rank;
        DSU(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        void union(int a, int b) {
            int pa = find(a), pb = find(b);
            if (pa == pb) return;
            if (rank[pa] < rank[pb]) parent[pa] = pb;
            else if (rank[pa] > rank[pb]) parent[pb] = pa;
            else {
                parent[pb] = pa;
                rank[pa]++;
            }
        }
    }

    public List<List<String>> accountsMerge(List<List<String>> accounts) {
        Map<String, Integer> id = new HashMap<>();
        Map<String, String> owner = new HashMap<>();
        int nextId = 0;
        for (List<String> acc : accounts) {
            for (int i = 1; i < acc.size(); i++) {
                String email = acc.get(i);
                if (!id.containsKey(email)) id.put(email, nextId++);
                owner.put(email, acc.get(0));
            }
        }
        DSU dsu = new DSU(nextId);
        for (List<String> acc : accounts) {
            int root = id.get(acc.get(1));
            for (int i = 2; i < acc.size(); i++) dsu.union(root, id.get(acc.get(i)));
        }
        Map<Integer, TreeSet<String>> groups = new HashMap<>();
        for (String email : id.keySet()) {
            groups.computeIfAbsent(dsu.find(id.get(email)), k -> new TreeSet<>()).add(email);
        }
        List<List<String>> ans = new ArrayList<>();
        for (TreeSet<String> emails : groups.values()) {
            List<String> merged = new ArrayList<>();
            merged.add(owner.get(emails.first()));
            merged.addAll(emails);
            ans.add(merged);
        }
        return ans;
    }
}
```

## 778. Hard - Swim in Rising Water

### Problem Statement
In a grid where cell values represent elevation and water level rises over time, return the minimum time needed to move from top-left to bottom-right.

### Test Cases
**Test Case 1**
- Input: grid = [[0,2],[1,3]]
- Output: 3
- Explanation: You must wait until elevation 3 is reachable.

**Test Case 2**
- Input: grid = [[0,1,2,3,4],[24,23,22,21,5],[12,13,14,15,16],[11,17,18,19,20],[10,9,8,7,6]]
- Output: 16
- Explanation: The optimal path minimizes the highest elevation on the route.

**Test Case 3**
- Input: grid = [[7]]
- Output: 7
- Explanation: Start and end are the same cell.

### Java Solution
```java
import java.util.*;

class Solution {
    public int swimInWater(int[][] grid) {
        int n = grid.length;
        boolean[][] seen = new boolean[n][n];
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
        pq.offer(new int[]{grid[0][0], 0, 0});
        seen[0][0] = true;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!pq.isEmpty()) {
            int[] cur = pq.poll();
            int time = cur[0], r = cur[1], c = cur[2];
            if (r == n - 1 && c == n - 1) return time;
            for (int[] d : dirs) {
                int nr = r + d[0], nc = c + d[1];
                if (nr < 0 || nc < 0 || nr >= n || nc >= n || seen[nr][nc]) continue;
                seen[nr][nc] = true;
                pq.offer(new int[]{Math.max(time, grid[nr][nc]), nr, nc});
            }
        }
        return -1;
    }
}
```

## 785. Medium - Is Graph Bipartite?

### Problem Statement
Determine whether an undirected graph can be colored with two colors so that every edge connects nodes of different colors.

### Test Cases
**Test Case 1**
- Input: graph = [[1,2,3],[0,2],[0,1,3],[0,2]]
- Output: false
- Explanation: The triangle prevents a bipartition.

**Test Case 2**
- Input: graph = [[1,3],[0,2],[1,3],[0,2]]
- Output: true
- Explanation: A 4-cycle is bipartite.

**Test Case 3**
- Input: graph = [[],[2],[1],[]]
- Output: true
- Explanation: Disconnected components can be colored separately.

### Java Solution
```java
import java.util.*;

class Solution {
    public boolean isBipartite(int[][] graph) {
        int n = graph.length;
        int[] color = new int[n];
        for (int i = 0; i < n; i++) {
            if (color[i] != 0) continue;
            Queue<Integer> q = new ArrayDeque<>();
            q.offer(i);
            color[i] = 1;
            while (!q.isEmpty()) {
                int node = q.poll();
                for (int next : graph[node]) {
                    if (color[next] == 0) {
                        color[next] = -color[node];
                        q.offer(next);
                    } else if (color[next] == color[node]) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
```

## 787. Medium - Cheapest Flights Within K Stops

### Problem Statement
Given flights with prices, return the cheapest cost from src to dst using at most k stops. Return -1 if no such route exists.

### Test Cases
**Test Case 1**
- Input: n = 4, flights = [[0,1,100],[1,2,100],[2,3,100],[0,3,500]], src = 0, dst = 3, k = 1
- Output: 500
- Explanation: The two-stop route is invalid, so the direct flight is best.

**Test Case 2**
- Input: n = 3, flights = [[0,1,100],[1,2,100],[0,2,500]], src = 0, dst = 2, k = 1
- Output: 200
- Explanation: One stop through city 1 is allowed.

**Test Case 3**
- Input: n = 3, flights = [[0,1,100],[1,2,100],[0,2,500]], src = 0, dst = 2, k = 0
- Output: 500
- Explanation: Only the direct flight is valid.

### Java Solution
```java
import java.util.*;

class Solution {
    public int findCheapestPrice(int n, int[][] flights, int src, int dst, int k) {
        int INF = 1_000_000_000;
        int[] dist = new int[n];
        Arrays.fill(dist, INF);
        dist[src] = 0;
        for (int i = 0; i <= k; i++) {
            int[] next = dist.clone();
            for (int[] f : flights) {
                if (dist[f[0]] != INF) next[f[1]] = Math.min(next[f[1]], dist[f[0]] + f[2]);
            }
            dist = next;
        }
        return dist[dst] == INF ? -1 : dist[dst];
    }
}
```

## 797. Medium - All Paths From Source to Target

### Problem Statement
Given a DAG, return all possible paths from node 0 to node n - 1.

### Test Cases
**Test Case 1**
- Input: graph = [[1,2],[3],[3],[]]
- Output: [[0,1,3],[0,2,3]]
- Explanation: There are two source-to-target paths.

**Test Case 2**
- Input: graph = [[4,3,1],[3,2,4],[3],[4],[]]
- Output: [[0,4],[0,3,4],[0,1,3,4],[0,1,2,3,4],[0,1,4]]
- Explanation: DFS explores every DAG route.

**Test Case 3**
- Input: graph = [[1],[]]
- Output: [[0,1]]
- Explanation: The path is unique.

### Java Solution
```java
import java.util.*;

class Solution {
    public List<List<Integer>> allPathsSourceTarget(int[][] graph) {
        List<List<Integer>> ans = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        path.add(0);
        dfs(graph, 0, path, ans);
        return ans;
    }

    private void dfs(int[][] graph, int node, List<Integer> path, List<List<Integer>> ans) {
        if (node == graph.length - 1) {
            ans.add(new ArrayList<>(path));
            return;
        }
        for (int next : graph[node]) {
            path.add(next);
            dfs(graph, next, path, ans);
            path.remove(path.size() - 1);
        }
    }
}
```

## 834. Hard - Sum of Distances in Tree

### Problem Statement
For each node in a tree, compute the sum of its distances to all other nodes.

### Test Cases
**Test Case 1**
- Input: n = 6, edges = [[0,1],[0,2],[2,3],[2,4],[2,5]]
- Output: [8,12,6,10,10,10]
- Explanation: Rerooting computes every node's total efficiently.

**Test Case 2**
- Input: n = 1, edges = []
- Output: [0]
- Explanation: The only node has distance 0 to itself.

**Test Case 3**
- Input: n = 2, edges = [[1,0]]
- Output: [1,1]
- Explanation: Each node is one edge away from the other.

### Java Solution
```java
import java.util.*;

class Solution {
    private List<List<Integer>> graph;
    private int[] count, ans;
    private int n;

    public int[] sumOfDistancesInTree(int n, int[][] edges) {
        this.n = n;
        graph = new ArrayList<>();
        for (int i = 0; i < n; i++) graph.add(new ArrayList<>());
        for (int[] e : edges) {
            graph.get(e[0]).add(e[1]);
            graph.get(e[1]).add(e[0]);
        }
        count = new int[n];
        Arrays.fill(count, 1);
        ans = new int[n];
        post(0, -1);
        pre(0, -1);
        return ans;
    }

    private void post(int node, int parent) {
        for (int next : graph.get(node)) {
            if (next == parent) continue;
            post(next, node);
            count[node] += count[next];
            ans[node] += ans[next] + count[next];
        }
    }

    private void pre(int node, int parent) {
        for (int next : graph.get(node)) {
            if (next == parent) continue;
            ans[next] = ans[node] - count[next] + (n - count[next]);
            pre(next, node);
        }
    }
}
```

## 839. Hard - Similar String Groups

### Problem Statement
Two strings are similar if they are equal or can become equal after swapping exactly two positions. Given an array of anagrams, return the number of connected similarity groups.

### Test Cases
**Test Case 1**
- Input: strs = ["tars","rats","arts","star"]
- Output: 2
- Explanation: The first three strings form one group and "star" forms another.

**Test Case 2**
- Input: strs = ["omv","ovm"]
- Output: 1
- Explanation: One swap makes the strings equal.

**Test Case 3**
- Input: strs = ["abc","abc"]
- Output: 1
- Explanation: Identical strings are similar.

### Java Solution
```java
class Solution {
    static class DSU {
        int[] parent;
        int groups;
        DSU(int n) {
            parent = new int[n];
            groups = n;
            for (int i = 0; i < n; i++) parent[i] = i;
        }
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        void union(int a, int b) {
            int pa = find(a), pb = find(b);
            if (pa == pb) return;
            parent[pb] = pa;
            groups--;
        }
    }

    public int numSimilarGroups(String[] strs) {
        int n = strs.length;
        DSU dsu = new DSU(n);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (similar(strs[i], strs[j])) dsu.union(i, j);
            }
        }
        return dsu.groups;
    }

    private boolean similar(String a, String b) {
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i) && ++diff > 2) return false;
        }
        return diff == 0 || diff == 2;
    }
}
```

## 864. Hard - Shortest Path to Get All Keys

### Problem Statement
Given a grid containing walls, locks, keys, and a starting cell, return the minimum steps needed to collect all keys. A lock can be passed only after its matching key has been collected.

### Test Cases
**Test Case 1**
- Input: grid = ["@.a..","###.#","b.A.B"]
- Output: 8
- Explanation: The shortest path collects both keys before crossing the locks.

**Test Case 2**
- Input: grid = ["@..aA","..B#.","....b"]
- Output: 6
- Explanation: Key collection order matters because of the locks.

**Test Case 3**
- Input: grid = ["@Aa"]
- Output: -1
- Explanation: The key is blocked by its own lock.

### Java Solution
```java
import java.util.*;

class Solution {
    public int shortestPathAllKeys(String[] grid) {
        int m = grid.length, n = grid[0].length();
        int sr = 0, sc = 0, keyCount = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                char ch = grid[i].charAt(j);
                if (ch == '@') {
                    sr = i;
                    sc = j;
                } else if (ch >= 'a' && ch <= 'f') {
                    keyCount = Math.max(keyCount, ch - 'a' + 1);
                }
            }
        }
        int target = (1 << keyCount) - 1;
        boolean[][][] seen = new boolean[m][n][1 << keyCount];
        Queue<int[]> q = new ArrayDeque<>();
        q.offer(new int[]{sr, sc, 0});
        seen[sr][sc][0] = true;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        int steps = 0;
        while (!q.isEmpty()) {
            for (int size = q.size(); size > 0; size--) {
                int[] cur = q.poll();
                int r = cur[0], c = cur[1], keys = cur[2];
                if (keys == target) return steps;
                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr < 0 || nc < 0 || nr >= m || nc >= n) continue;
                    char ch = grid[nr].charAt(nc);
                    if (ch == '#') continue;
                    int nextKeys = keys;
                    if (ch >= 'a' && ch <= 'f') nextKeys |= 1 << (ch - 'a');
                    if (ch >= 'A' && ch <= 'F' && ((keys >> (ch - 'A')) & 1) == 0) continue;
                    if (!seen[nr][nc][nextKeys]) {
                        seen[nr][nc][nextKeys] = true;
                        q.offer(new int[]{nr, nc, nextKeys});
                    }
                }
            }
            steps++;
        }
        return -1;
    }
}
```

## 909. Medium - Snakes and Ladders

### Problem Statement
Given a snakes and ladders board, return the minimum number of moves to reach the final square. Each move rolls 1 through 6, then follows any snake or ladder on the landing square.

### Test Cases
**Test Case 1**
- Input: board = [[-1,-1,-1,-1,-1,-1],[-1,-1,-1,-1,-1,-1],[-1,-1,-1,-1,-1,-1],[-1,35,-1,-1,13,-1],[-1,-1,-1,-1,-1,-1],[-1,15,-1,-1,-1,-1]]
- Output: 4
- Explanation: BFS finds the minimum dice throws to the end.

**Test Case 2**
- Input: board = [[-1,-1],[-1,3]]
- Output: 1
- Explanation: From square 1 you can land on the ladder and reach square 3 immediately.

**Test Case 3**
- Input: board = [[-1,-1,-1],[-1,9,8],[-1,8,9]]
- Output: 1
- Explanation: One move can lead to the final square.

### Java Solution
```java
import java.util.*;

class Solution {
    public int snakesAndLadders(int[][] board) {
        int n = board.length;
        Queue<Integer> q = new ArrayDeque<>();
        boolean[] seen = new boolean[n * n + 1];
        q.offer(1);
        seen[1] = true;
        int moves = 0;
        while (!q.isEmpty()) {
            for (int size = q.size(); size > 0; size--) {
                int cur = q.poll();
                if (cur == n * n) return moves;
                for (int next = cur + 1; next <= Math.min(cur + 6, n * n); next++) {
                    int[] pos = getPos(next, n);
                    int dest = board[pos[0]][pos[1]] == -1 ? next : board[pos[0]][pos[1]];
                    if (!seen[dest]) {
                        seen[dest] = true;
                        q.offer(dest);
                    }
                }
            }
            moves++;
        }
        return -1;
    }

    private int[] getPos(int num, int n) {
        int r = (num - 1) / n;
        int c = (num - 1) % n;
        int row = n - 1 - r;
        int col = (r % 2 == 0) ? c : n - 1 - c;
        return new int[]{row, col};
    }
}
```

## 924. Hard - Minimize Malware Spread

### Problem Statement
In an undirected graph with initially infected nodes, remove one infected node before spread begins to minimize the final infected count. Break ties by smaller node index.

### Test Cases
**Test Case 1**
- Input: graph = [[1,1,0],[1,1,0],[0,0,1]], initial = [0,1]
- Output: 0
- Explanation: Both choices are equally good, so return the smaller index.

**Test Case 2**
- Input: graph = [[1,0,0],[0,1,0],[0,0,1]], initial = [0,2]
- Output: 0
- Explanation: Each infected node only infects itself.

**Test Case 3**
- Input: graph = [[1,1,1],[1,1,1],[1,1,1]], initial = [1,2]
- Output: 1
- Explanation: The whole graph is one component, so choose the smaller index.

### Java Solution
```java
import java.util.*;

class Solution {
    static class DSU {
        int[] parent, size;
        DSU(int n) {
            parent = new int[n];
            size = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                size[i] = 1;
            }
        }
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        void union(int a, int b) {
            int pa = find(a), pb = find(b);
            if (pa == pb) return;
            if (size[pa] < size[pb]) {
                int t = pa; pa = pb; pb = t;
            }
            parent[pb] = pa;
            size[pa] += size[pb];
        }
    }

    public int minMalwareSpread(int[][] graph, int[] initial) {
        int n = graph.length;
        DSU dsu = new DSU(n);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (graph[i][j] == 1) dsu.union(i, j);
            }
        }
        int[] infected = new int[n];
        for (int node : initial) infected[dsu.find(node)]++;
        Arrays.sort(initial);
        int bestNode = initial[0], bestSaved = -1;
        for (int node : initial) {
            int root = dsu.find(node);
            int saved = infected[root] == 1 ? dsu.size[root] : 0;
            if (saved > bestSaved) {
                bestSaved = saved;
                bestNode = node;
            }
        }
        return bestNode;
    }
}
```

## 928. Hard - Minimize Malware Spread II

### Problem Statement
Remove exactly one initially infected node so that after malware spreads, the final number of infected nodes is minimized. Break ties by smaller index.

### Test Cases
**Test Case 1**
- Input: graph = [[1,1,0],[1,1,0],[0,0,1]], initial = [0,1]
- Output: 0
- Explanation: Both removals lead to the same answer, so choose the smaller index.

**Test Case 2**
- Input: graph = [[1,0,0],[0,1,0],[0,0,1]], initial = [0,2]
- Output: 0
- Explanation: There is no spread beyond the removed node itself.

**Test Case 3**
- Input: graph = [[1,1,0,0],[1,1,1,0],[0,1,1,1],[0,0,1,1]], initial = [0,2]
- Output: 2
- Explanation: Removing node 2 uniquely protects more clean nodes.

### Java Solution
```java
import java.util.*;

class Solution {
    static class DSU {
        int[] parent, size;
        DSU(int n) {
            parent = new int[n];
            size = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                size[i] = 1;
            }
        }
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        void union(int a, int b) {
            int pa = find(a), pb = find(b);
            if (pa == pb) return;
            if (size[pa] < size[pb]) {
                int t = pa; pa = pb; pb = t;
            }
            parent[pb] = pa;
            size[pa] += size[pb];
        }
    }

    public int minMalwareSpread(int[][] graph, int[] initial) {
        int n = graph.length;
        boolean[] infected = new boolean[n];
        for (int x : initial) infected[x] = true;
        DSU dsu = new DSU(n);
        for (int i = 0; i < n; i++) {
            if (infected[i]) continue;
            for (int j = i + 1; j < n; j++) {
                if (!infected[j] && graph[i][j] == 1) dsu.union(i, j);
            }
        }
        Map<Integer, Integer> compSources = new HashMap<>();
        Map<Integer, Set<Integer>> reach = new HashMap<>();
        for (int src : initial) {
            Set<Integer> comps = new HashSet<>();
            for (int v = 0; v < n; v++) {
                if (!infected[v] && graph[src][v] == 1) comps.add(dsu.find(v));
            }
            reach.put(src, comps);
            for (int root : comps) compSources.put(root, compSources.getOrDefault(root, 0) + 1);
        }
        Arrays.sort(initial);
        int bestNode = initial[0], bestSaved = -1;
        for (int src : initial) {
            int saved = 0;
            for (int root : reach.get(src)) {
                if (compSources.get(root) == 1) saved += dsu.size[dsu.find(root)];
            }
            if (saved > bestSaved) {
                bestSaved = saved;
                bestNode = src;
            }
        }
        return bestNode;
    }
}
```

## 947. Medium - Most Stones Removed with Same Row or Column

### Problem Statement
Given stones on a 2D plane, remove as many stones as possible where each removed stone shares a row or column with another remaining stone.

### Test Cases
**Test Case 1**
- Input: stones = [[0,0],[0,1],[1,0],[1,2],[2,1],[2,2]]
- Output: 5
- Explanation: All stones are connected, so one stone must remain.

**Test Case 2**
- Input: stones = [[0,0],[0,2],[1,1],[2,0],[2,2]]
- Output: 3
- Explanation: Two components remain, so 5 - 2 = 3 stones can be removed.

**Test Case 3**
- Input: stones = [[0,0]]
- Output: 0
- Explanation: A single stone cannot be removed.

### Java Solution
```java
import java.util.*;

class Solution {
    static class DSU {
        int[] parent;
        int components;
        DSU(int n) {
            parent = new int[n];
            components = n;
            for (int i = 0; i < n; i++) parent[i] = i;
        }
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        void union(int a, int b) {
            int pa = find(a), pb = find(b);
            if (pa == pb) return;
            parent[pb] = pa;
            components--;
        }
    }

    public int removeStones(int[][] stones) {
        int n = stones.length;
        DSU dsu = new DSU(n);
        Map<Integer, Integer> row = new HashMap<>();
        Map<Integer, Integer> col = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (row.containsKey(stones[i][0])) dsu.union(i, row.get(stones[i][0]));
            else row.put(stones[i][0], i);
            if (col.containsKey(stones[i][1])) dsu.union(i, col.get(stones[i][1]));
            else col.put(stones[i][1], i);
        }
        return n - dsu.components;
    }
}
```

## 959. Medium - Regions Cut By Slashes

### Problem Statement
Each cell contains '/', '\\', or a blank. Count how many regions are formed when these slashes divide the grid.

### Test Cases
**Test Case 1**
- Input: grid = [" /","/ "]
- Output: 2
- Explanation: The two slashes create two regions.

**Test Case 2**
- Input: grid = [" /","  "]
- Output: 1
- Explanation: The slash does not enclose an extra region.

**Test Case 3**
- Input: grid = ["/\\","\\/"]
- Output: 5
- Explanation: Crossing diagonals create multiple enclosed areas.

### Java Solution
```java
class Solution {
    private final int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

    public int regionsBySlashes(String[] grid) {
        int n = grid.length;
        int size = n * 3;
        int[][] expanded = new int[size][size];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                char ch = grid[i].charAt(j);
                if (ch == '/') {
                    expanded[i * 3][j * 3 + 2] = 1;
                    expanded[i * 3 + 1][j * 3 + 1] = 1;
                    expanded[i * 3 + 2][j * 3] = 1;
                } else if (ch == '\\') {
                    expanded[i * 3][j * 3] = 1;
                    expanded[i * 3 + 1][j * 3 + 1] = 1;
                    expanded[i * 3 + 2][j * 3 + 2] = 1;
                }
            }
        }
        int regions = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (expanded[i][j] == 0) {
                    regions++;
                    dfs(expanded, i, j);
                }
            }
        }
        return regions;
    }

    private void dfs(int[][] grid, int r, int c) {
        int n = grid.length;
        if (r < 0 || c < 0 || r >= n || c >= n || grid[r][c] != 0) return;
        grid[r][c] = 1;
        for (int[] d : dirs) dfs(grid, r + d[0], c + d[1]);
    }
}
```

## 1036. Hard - Escape a Large Maze

### Problem Statement
On a 1,000,000 by 1,000,000 grid with at most 200 blocked cells, determine whether source can reach target. The blocked cells can only enclose a limited region, so limited BFS is sufficient.

### Test Cases
**Test Case 1**
- Input: blocked = [[0,1],[1,0]], source = [0,0], target = [0,2]
- Output: false
- Explanation: The source is trapped against the border.

**Test Case 2**
- Input: blocked = [], source = [0,0], target = [999999,999999]
- Output: true
- Explanation: With no blocked cells, a path exists.

**Test Case 3**
- Input: blocked = [[691938,300406],[710196,624190],[858790,609485],[268029,225806],[200010,188664],[132599,612099],[329444,633495],[196657,757958],[628509,883388]], source = [655988,180910], target = [267728,840949]
- Output: true
- Explanation: Neither endpoint is enclosed by the blocked set.

### Java Solution
```java
import java.util.*;

class Solution {
    private static final int LIMIT = 1_000_000;
    private final int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

    public boolean isEscapePossible(int[][] blocked, int[] source, int[] target) {
        Set<Long> blockedSet = new HashSet<>();
        for (int[] b : blocked) blockedSet.add(encode(b[0], b[1]));
        int maxArea = blocked.length * (blocked.length - 1) / 2;
        return bfs(source, target, blockedSet, maxArea) && bfs(target, source, blockedSet, maxArea);
    }

    private boolean bfs(int[] start, int[] finish, Set<Long> blocked, int maxArea) {
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.offer(start);
        seen.add(encode(start[0], start[1]));
        while (!q.isEmpty() && seen.size() <= maxArea) {
            int[] cur = q.poll();
            if (cur[0] == finish[0] && cur[1] == finish[1]) return true;
            for (int[] d : dirs) {
                int nr = cur[0] + d[0], nc = cur[1] + d[1];
                long key = encode(nr, nc);
                if (nr < 0 || nc < 0 || nr >= LIMIT || nc >= LIMIT || blocked.contains(key) || !seen.add(key)) continue;
                q.offer(new int[]{nr, nc});
            }
        }
        return seen.size() > maxArea;
    }

    private long encode(int r, int c) {
        return ((long) r << 20) | c;
    }
}
```

## 1059. Medium - All Paths from Source Lead to Destination

### Problem Statement
Given a directed graph, return true only if every path starting from source eventually ends at destination and nowhere else.

### Test Cases
**Test Case 1**
- Input: n = 3, edges = [[0,1],[0,2]], source = 0, destination = 2
- Output: false
- Explanation: Path 0 -> 1 ends at a dead end that is not the destination.

**Test Case 2**
- Input: n = 4, edges = [[0,1],[0,3],[1,2],[2,1]], source = 0, destination = 3
- Output: false
- Explanation: The cycle between 1 and 2 never reaches the destination.

**Test Case 3**
- Input: n = 4, edges = [[0,1],[0,2],[1,3],[2,3]], source = 0, destination = 3
- Output: true
- Explanation: Every possible path from source ends at destination.

### Java Solution
```java
import java.util.*;

class Solution {
    public boolean leadsToDestination(int n, int[][] edges, int source, int destination) {
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) graph.add(new ArrayList<>());
        for (int[] e : edges) graph.get(e[0]).add(e[1]);
        int[] state = new int[n];
        return dfs(source, destination, graph, state);
    }

    private boolean dfs(int node, int destination, List<List<Integer>> graph, int[] state) {
        if (graph.get(node).isEmpty()) return node == destination;
        if (state[node] == 1) return false;
        if (state[node] == 2) return true;
        state[node] = 1;
        for (int next : graph.get(node)) {
            if (!dfs(next, destination, graph, state)) return false;
        }
        state[node] = 2;
        return true;
    }
}
```

## 1096. Hard - Brace Expansion II

### Problem Statement
Given a brace expression with union and concatenation, return all distinct expanded strings in lexicographical order.

### Test Cases
**Test Case 1**
- Input: expression = "{a,b}{c,{d,e}}"
- Output: ["ac","ad","ae","bc","bd","be"]
- Explanation: Concatenate each option from the first group with each option from the second.

**Test Case 2**
- Input: expression = "{{a,z},a{b,c},{ab,z}}"
- Output: ["a","ab","ac","z"]
- Explanation: Duplicate strings are removed from the result.

**Test Case 3**
- Input: expression = "abcd"
- Output: ["abcd"]
- Explanation: A plain word expands to itself.

### Java Solution
```java
import java.util.*;

class Solution {
    private String s;
    private int idx;

    public List<String> braceExpansionII(String expression) {
        s = expression;
        idx = 0;
        return new ArrayList<>(new TreeSet<>(parseExpression()));
    }

    private Set<String> parseExpression() {
        Set<String> res = parseTerm();
        while (idx < s.length() && s.charAt(idx) == ',') {
            idx++;
            res.addAll(parseTerm());
        }
        return res;
    }

    private Set<String> parseTerm() {
        Set<String> res = new HashSet<>();
        res.add("");
        while (idx < s.length() && s.charAt(idx) != '}' && s.charAt(idx) != ',') {
            res = product(res, parseFactor());
        }
        return res;
    }

    private Set<String> parseFactor() {
        if (s.charAt(idx) == '{') {
            idx++;
            Set<String> inner = parseExpression();
            idx++;
            return inner;
        }
        int start = idx;
        while (idx < s.length() && Character.isLowerCase(s.charAt(idx))) idx++;
        return new HashSet<>(Collections.singletonList(s.substring(start, idx)));
    }

    private Set<String> product(Set<String> a, Set<String> b) {
        Set<String> res = new HashSet<>();
        for (String x : a) for (String y : b) res.add(x + y);
        return res;
    }
}
```

## 1110. Medium - Delete Nodes And Return Forest

### Problem Statement
Delete the nodes whose values are in to_delete and return the roots of the remaining forest.

### Test Cases
**Test Case 1**
- Input: root = [1,2,3,4,5,6,7], to_delete = [3,5]
- Output: [[1,2,null,4],[6],[7]]
- Explanation: Deleting 3 and 5 splits the tree into three roots.

**Test Case 2**
- Input: root = [1,2,4,null,3], to_delete = [3]
- Output: [[1,2,4]]
- Explanation: Deleting a leaf does not create extra trees beyond the original root.

**Test Case 3**
- Input: root = [1], to_delete = [1]
- Output: []
- Explanation: Deleting the root removes the only tree.

### Java Solution
```java
import java.util.*;

class Solution {
    public List<TreeNode> delNodes(TreeNode root, int[] to_delete) {
        Set<Integer> del = new HashSet<>();
        for (int x : to_delete) del.add(x);
        List<TreeNode> ans = new ArrayList<>();
        dfs(root, true, del, ans);
        return ans;
    }

    private TreeNode dfs(TreeNode node, boolean isRoot, Set<Integer> del, List<TreeNode> ans) {
        if (node == null) return null;
        boolean deleted = del.contains(node.val);
        if (isRoot && !deleted) ans.add(node);
        node.left = dfs(node.left, deleted, del, ans);
        node.right = dfs(node.right, deleted, del, ans);
        return deleted ? null : node;
    }
}
```

## 1129. Medium - Shortest Path with Alternating Colors

### Problem Statement
Given a directed graph with red and blue edges, return the shortest distance from node 0 to every node using paths with alternating edge colors.

### Test Cases
**Test Case 1**
- Input: n = 3, redEdges = [[0,1],[1,2]], blueEdges = []
- Output: [0,1,-1]
- Explanation: Two consecutive red edges are not allowed.

**Test Case 2**
- Input: n = 3, redEdges = [[0,1]], blueEdges = [[1,2]]
- Output: [0,1,2]
- Explanation: The path uses one red edge then one blue edge.

**Test Case 3**
- Input: n = 3, redEdges = [[0,1],[0,2]], blueEdges = [[1,0]]
- Output: [0,1,1]
- Explanation: Nodes 1 and 2 are each one edge away from the start.

### Java Solution
```java
import java.util.*;

class Solution {
    public int[] shortestAlternatingPaths(int n, int[][] redEdges, int[][] blueEdges) {
        List<Integer>[] red = new ArrayList[n];
        List<Integer>[] blue = new ArrayList[n];
        for (int i = 0; i < n; i++) {
            red[i] = new ArrayList<>();
            blue[i] = new ArrayList<>();
        }
        for (int[] e : redEdges) red[e[0]].add(e[1]);
        for (int[] e : blueEdges) blue[e[0]].add(e[1]);
        int[][] dist = new int[n][2];
        for (int[] row : dist) Arrays.fill(row, Integer.MAX_VALUE);
        Queue<int[]> q = new ArrayDeque<>();
        dist[0][0] = dist[0][1] = 0;
        q.offer(new int[]{0, 0});
        q.offer(new int[]{0, 1});
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            int node = cur[0], last = cur[1];
            List<Integer>[] nextGraph = last == 0 ? blue : red;
            int nextColor = 1 - last;
            for (int next : nextGraph[node]) {
                if (dist[next][nextColor] != Integer.MAX_VALUE) continue;
                dist[next][nextColor] = dist[node][last] + 1;
                q.offer(new int[]{next, nextColor});
            }
        }
        int[] ans = new int[n];
        for (int i = 0; i < n; i++) {
            int best = Math.min(dist[i][0], dist[i][1]);
            ans[i] = best == Integer.MAX_VALUE ? -1 : best;
        }
        return ans;
    }
}
```

## 1168. Hard - Optimize Water Distribution in a Village

### Problem Statement
Each house can either build its own well or connect through pipes. Return the minimum total cost to supply water to every house.

### Test Cases
**Test Case 1**
- Input: n = 3, wells = [1,2,2], pipes = [[1,2,1],[2,3,1]]
- Output: 3
- Explanation: Build one well and connect the other two houses by cheap pipes.

**Test Case 2**
- Input: n = 2, wells = [1,1], pipes = [[1,2,1],[1,2,2]]
- Output: 2
- Explanation: Total minimum cost is 2.

**Test Case 3**
- Input: n = 3, wells = [5,5,5], pipes = [[1,2,1],[2,3,1]]
- Output: 7
- Explanation: One well plus two pipes is optimal.

### Java Solution
```java
import java.util.*;

class Solution {
    static class DSU {
        int[] parent, rank;
        DSU(int n) {
            parent = new int[n + 1];
            rank = new int[n + 1];
            for (int i = 0; i <= n; i++) parent[i] = i;
        }
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        boolean union(int a, int b) {
            int pa = find(a), pb = find(b);
            if (pa == pb) return false;
            if (rank[pa] < rank[pb]) parent[pa] = pb;
            else if (rank[pa] > rank[pb]) parent[pb] = pa;
            else {
                parent[pb] = pa;
                rank[pa]++;
            }
            return true;
        }
    }

    public int minCostToSupplyWater(int n, int[] wells, int[][] pipes) {
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) edges.add(new int[]{0, i + 1, wells[i]});
        Collections.addAll(edges, pipes);
        edges.sort(Comparator.comparingInt(a -> a[2]));
        DSU dsu = new DSU(n);
        int cost = 0;
        for (int[] e : edges) if (dsu.union(e[0], e[1])) cost += e[2];
        return cost;
    }
}
```

## 1192. Hard - Critical Connections in a Network

### Problem Statement
Given a connected undirected network, return all critical connections whose removal disconnects the graph.

### Test Cases
**Test Case 1**
- Input: n = 4, connections = [[0,1],[1,2],[2,0],[1,3]]
- Output: [[1,3]]
- Explanation: Edge [1,3] is the only bridge.

**Test Case 2**
- Input: n = 2, connections = [[0,1]]
- Output: [[0,1]]
- Explanation: Removing the only edge disconnects the graph.

**Test Case 3**
- Input: n = 5, connections = [[0,1],[1,2],[2,0],[1,3],[3,4]]
- Output: [[3,4],[1,3]]
- Explanation: Both edges are bridges on the tail.

### Java Solution
```java
import java.util.*;

class Solution {
    private List<List<Integer>> graph;
    private int[] disc, low;
    private List<List<Integer>> ans = new ArrayList<>();
    private int time = 0;

    public List<List<Integer>> criticalConnections(int n, List<List<Integer>> connections) {
        graph = new ArrayList<>();
        for (int i = 0; i < n; i++) graph.add(new ArrayList<>());
        for (List<Integer> e : connections) {
            int u = e.get(0), v = e.get(1);
            graph.get(u).add(v);
            graph.get(v).add(u);
        }
        disc = new int[n];
        low = new int[n];
        Arrays.fill(disc, -1);
        dfs(0, -1);
        return ans;
    }

    private void dfs(int node, int parent) {
        disc[node] = low[node] = time++;
        for (int next : graph.get(node)) {
            if (next == parent) continue;
            if (disc[next] == -1) {
                dfs(next, node);
                low[node] = Math.min(low[node], low[next]);
                if (low[next] > disc[node]) ans.add(Arrays.asList(node, next));
            } else {
                low[node] = Math.min(low[node], disc[next]);
            }
        }
    }
}
```

## 1293. Hard - Shortest Path in a Grid with Obstacles Elimination

### Problem Statement
Given a grid with obstacles and an integer k, return the shortest path from top-left to bottom-right if you may eliminate at most k obstacles.

### Test Cases
**Test Case 1**
- Input: grid = [[0,0,0],[1,1,0],[0,0,0],[0,1,1],[0,0,0]], k = 1
- Output: 6
- Explanation: Eliminating one obstacle gives the shortest valid route.

**Test Case 2**
- Input: grid = [[0,1,1],[1,1,1],[1,0,0]], k = 1
- Output: -1
- Explanation: One elimination is insufficient.

**Test Case 3**
- Input: grid = [[0,0],[0,0]], k = 0
- Output: 2
- Explanation: Move right and down in two steps.

### Java Solution
```java
import java.util.*;

class Solution {
    public int shortestPath(int[][] grid, int k) {
        int m = grid.length, n = grid[0].length;
        if (k >= m + n - 2) return m + n - 2;
        int[][] best = new int[m][n];
        for (int[] row : best) Arrays.fill(row, -1);
        Queue<int[]> q = new ArrayDeque<>();
        q.offer(new int[]{0, 0, k});
        best[0][0] = k;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        int steps = 0;
        while (!q.isEmpty()) {
            for (int size = q.size(); size > 0; size--) {
                int[] cur = q.poll();
                int r = cur[0], c = cur[1], remain = cur[2];
                if (r == m - 1 && c == n - 1) return steps;
                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr < 0 || nc < 0 || nr >= m || nc >= n) continue;
                    int nextRemain = remain - grid[nr][nc];
                    if (nextRemain < 0 || best[nr][nc] >= nextRemain) continue;
                    best[nr][nc] = nextRemain;
                    q.offer(new int[]{nr, nc, nextRemain});
                }
            }
            steps++;
        }
        return -1;
    }
}
```

## 1928. Hard - Minimum Cost to Reach Destination in Time

### Problem Statement
Given roads with travel times, a maximum total time, and passing fees for cities, return the minimum fee needed to reach the destination within maxTime.

### Test Cases
**Test Case 1**
- Input: maxTime = 30, edges = [[0,1,10],[1,2,10],[2,5,10],[0,3,1],[3,4,10],[4,5,15]], passingFees = [5,1,2,20,20,3]
- Output: 11
- Explanation: The cheapest valid route is 0 -> 1 -> 2 -> 5.

**Test Case 2**
- Input: maxTime = 29, edges = [[0,1,10],[1,2,10],[2,5,10],[0,3,1],[3,4,10],[4,5,15]], passingFees = [5,1,2,20,20,3]
- Output: 48
- Explanation: The cheaper path is too slow, so a more expensive faster route is needed.

**Test Case 3**
- Input: maxTime = 25, edges = [[0,1,10],[1,2,10],[2,3,10]], passingFees = [1,2,3,4]
- Output: -1
- Explanation: No route reaches the destination in time.

### Java Solution
```java
import java.util.*;

class Solution {
    public int minCost(int maxTime, int[][] edges, int[] passingFees) {
        int n = passingFees.length;
        int INF = 1_000_000_000;
        int[][] dp = new int[maxTime + 1][n];
        for (int[] row : dp) Arrays.fill(row, INF);
        dp[0][0] = passingFees[0];
        for (int t = 1; t <= maxTime; t++) {
            for (int[] e : edges) {
                int u = e[0], v = e[1], time = e[2];
                if (t >= time) {
                    if (dp[t - time][u] != INF) dp[t][v] = Math.min(dp[t][v], dp[t - time][u] + passingFees[v]);
                    if (dp[t - time][v] != INF) dp[t][u] = Math.min(dp[t][u], dp[t - time][v] + passingFees[u]);
                }
            }
        }
        int ans = INF;
        for (int t = 0; t <= maxTime; t++) ans = Math.min(ans, dp[t][n - 1]);
        return ans == INF ? -1 : ans;
    }
}
```

## 2421. Hard - Number of Good Paths

### Problem Statement
Count good paths in an undirected graph where the two endpoints have equal value and every node on the path has value no greater than that value. Single-node paths also count.

### Test Cases
**Test Case 1**
- Input: vals = [1,3,2,1,3], edges = [[0,1],[0,2],[2,3],[2,4]]
- Output: 6
- Explanation: Five single-node paths plus one extra path between the two nodes with value 3.

**Test Case 2**
- Input: vals = [1,1,2,2,3], edges = [[0,1],[1,2],[2,3],[2,4]]
- Output: 7
- Explanation: Single-node paths plus one extra good path for value 1 and one for value 2.

**Test Case 3**
- Input: vals = [1], edges = []
- Output: 1
- Explanation: The only path is the single node itself.

### Java Solution
```java
import java.util.*;

class Solution {
    static class DSU {
        int[] parent, size;
        DSU(int n) {
            parent = new int[n];
            size = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                size[i] = 1;
            }
        }
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        void union(int a, int b) {
            int pa = find(a), pb = find(b);
            if (pa == pb) return;
            if (size[pa] < size[pb]) {
                int t = pa; pa = pb; pb = t;
            }
            parent[pb] = pa;
            size[pa] += size[pb];
        }
    }

    public int numberOfGoodPaths(int[] vals, int[][] edges) {
        int n = vals.length;
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) graph.add(new ArrayList<>());
        for (int[] e : edges) {
            graph.get(e[0]).add(e[1]);
            graph.get(e[1]).add(e[0]);
        }
        TreeMap<Integer, List<Integer>> byValue = new TreeMap<>();
        for (int i = 0; i < n; i++) byValue.computeIfAbsent(vals[i], k -> new ArrayList<>()).add(i);
        DSU dsu = new DSU(n);
        int ans = 0;
        for (int value : byValue.keySet()) {
            for (int node : byValue.get(value)) {
                for (int nei : graph.get(node)) {
                    if (vals[nei] <= value) dsu.union(node, nei);
                }
            }
            Map<Integer, Integer> count = new HashMap<>();
            for (int node : byValue.get(value)) {
                int root = dsu.find(node);
                count.put(root, count.getOrDefault(root, 0) + 1);
            }
            for (int c : count.values()) ans += c * (c + 1) / 2;
        }
        return ans;
    }
}
```
