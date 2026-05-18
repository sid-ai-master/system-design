# 🔵 Medium Priority Problems

> 6 problems covering interval merging, binary search, BST properties, tree DP, and topological sort.

---

## 759. Employee Free Time

### Problem Statement
You are given a list of `schedules` where `schedules[i]` is a list of `Interval` objects representing the working hours of the `i`-th employee. Return the list of **finite intervals** representing the **free time** common to all employees (i.e., time slots not covered by any employee's schedule), sorted.

### Java Solution
```java
import java.util.*;

class EmployeeFreeTime {
    static class Interval {
        int start, end;
        Interval(int s, int e) { start = s; end = e; }
    }

    public List<Interval> employeeFreeTime(List<List<Interval>> schedule) {
        // Flatten and sort all intervals
        List<Interval> all = new ArrayList<>();
        for (List<Interval> person : schedule) all.addAll(person);
        all.sort((a, b) -> a.start - b.start);

        List<Interval> result = new ArrayList<>();
        int end = all.get(0).end;

        for (int i = 1; i < all.size(); i++) {
            Interval curr = all.get(i);
            if (curr.start > end) {
                // Gap found → free time
                result.add(new Interval(end, curr.start));
            }
            end = Math.max(end, curr.end);
        }
        return result;
    }
}
```

### Test Cases
```java
// Test 1: Two employees with a gap
// Employee 1: [1,3],[6,7]    Employee 2: [2,4]
// Merged: [1,4],[6,7] → Free: [4,6]
List<List<Interval>> s1 = Arrays.asList(
    Arrays.asList(new Interval(1,3), new Interval(6,7)),
    Arrays.asList(new Interval(2,4))
);
List<Interval> r1 = new EmployeeFreeTime().employeeFreeTime(s1);
assert r1.size() == 1 && r1.get(0).start == 4 && r1.get(0).end == 6;

// Test 2: Multiple gaps
// Employee 1: [1,2],[2,5]    Employee 2: [7,10]
// Free: [5,7]
List<List<Interval>> s2 = Arrays.asList(
    Arrays.asList(new Interval(1,2), new Interval(2,5)),
    Arrays.asList(new Interval(7,10))
);
List<Interval> r2 = new EmployeeFreeTime().employeeFreeTime(s2);
assert r2.size() == 1 && r2.get(0).start == 5 && r2.get(0).end == 7;

// Test 3: No free time (fully covered)
// Employee 1: [1,4]    Employee 2: [2,6]
// Merged: [1,6] → Free: [] (no gaps)
List<List<Interval>> s3 = Arrays.asList(
    Arrays.asList(new Interval(1,4)),
    Arrays.asList(new Interval(2,6))
);
List<Interval> r3 = new EmployeeFreeTime().employeeFreeTime(s3);
assert r3.isEmpty();
```

---

## 702. Search in a Sorted Array of Unknown Size

### Problem Statement
You have a sorted integer array of unknown length accessible only through an `ArrayReader` API. `reader.get(i)` returns `nums[i]` if `i` is within bounds, or `2147483647` if `i` is out of bounds. Return the index of `target`, or `-1` if not found.

### Java Solution
```java
class SearchInSortedArrayUnknownSize {
    interface ArrayReader {
        int get(int index);
    }

    public int search(ArrayReader reader, int target) {
        // Exponential search to find upper bound
        int lo = 0, hi = 1;
        while (reader.get(hi) < target) {
            lo = hi;
            hi *= 2;
        }

        // Binary search within [lo, hi]
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            int val = reader.get(mid);
            if (val == target)              return mid;
            else if (val < target)          lo = mid + 1;
            else                            hi = mid - 1; // val > target OR out of bounds (INF)
        }
        return -1;
    }
}
```

### Test Cases
```java
// Test 1: Target exists near the start
// nums = [-1,0,3,5,9,12], target = 9
ArrayReader r1 = i -> {
    int[] nums = {-1,0,3,5,9,12};
    return i < nums.length ? nums[i] : Integer.MAX_VALUE;
};
assert new SearchInSortedArrayUnknownSize().search(r1, 9) == 4;

// Test 2: Target not in array
assert new SearchInSortedArrayUnknownSize().search(r1, 2) == -1;

// Test 3: Target at index 0
ArrayReader r3 = i -> {
    int[] nums = {5,7,10,15};
    return i < nums.length ? nums[i] : Integer.MAX_VALUE;
};
assert new SearchInSortedArrayUnknownSize().search(r3, 5) == 0;
```

---

## 333. Largest BST Subtree

### Problem Statement
Given the `root` of a binary tree, find the **largest subtree** that is also a **BST** (Binary Search Tree). Return the **number of nodes** in the largest BST subtree.

### Java Solution
```java
class LargestBSTSubtree {
    static class TreeNode {
        int val;
        TreeNode left, right;
        TreeNode(int val) { this.val = val; }
    }

    private int maxSize = 0;

    public int largestBSTSubtree(TreeNode root) {
        postOrder(root);
        return maxSize;
    }

    // Returns int[] {min, max, size} where size=-1 means NOT a BST
    private int[] postOrder(TreeNode node) {
        if (node == null) return new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE, 0};

        int[] left  = postOrder(node.left);
        int[] right = postOrder(node.right);

        // Check if current node forms a valid BST with its subtrees
        if (left[2]  != -1 &&
            right[2] != -1 &&
            node.val > left[1] &&   // node > max of left subtree
            node.val < right[0]) {  // node < min of right subtree

            int size = left[2] + right[2] + 1;
            maxSize = Math.max(maxSize, size);
            int min = Math.min(left[0], node.val);
            int max = Math.max(right[1], node.val);
            return new int[]{min, max, size};
        }

        return new int[]{0, 0, -1}; // Not a BST
    }
}
```

### Test Cases
```java
// Test 1: Standard tree
//       10
//      /  \
//     5   15
//    / \    \
//   1   8   7    ← 7 < 15, so right subtree is not BST
// Largest BST: subtree rooted at 5 → size = 3
TreeNode root1 = new TreeNode(10);
root1.left = new TreeNode(5); root1.right = new TreeNode(15);
root1.left.left = new TreeNode(1); root1.left.right = new TreeNode(8);
root1.right.right = new TreeNode(7);
assert new LargestBSTSubtree().largestBSTSubtree(root1) == 3;

// Test 2: Entire tree is a BST
TreeNode root2 = new TreeNode(4);
root2.left = new TreeNode(2); root2.right = new TreeNode(6);
assert new LargestBSTSubtree().largestBSTSubtree(root2) == 3;

// Test 3: Single node
TreeNode root3 = new TreeNode(1);
assert new LargestBSTSubtree().largestBSTSubtree(root3) == 1;
```

---

## 549. Binary Tree Longest Consecutive Sequence II

### Problem Statement
Given the `root` of a binary tree, return the length of the **longest consecutive path** in the tree. The path can be **increasing or decreasing** (e.g., `[1,2,3]` or `[3,2,1]`), and the path must go through a parent-child connection. The path does **not** need to pass through the root, and can go in both left and right directions (through the parent).

### Java Solution
```java
class BinaryTreeLongestConsecutiveSequenceII {
    static class TreeNode {
        int val;
        TreeNode left, right;
        TreeNode(int val) { this.val = val; }
    }

    private int maxLen = 0;

    public int longestConsecutive(TreeNode root) {
        dfs(root);
        return maxLen;
    }

    // Returns int[] {increasing_length, decreasing_length} from this node downward
    private int[] dfs(TreeNode node) {
        if (node == null) return new int[]{0, 0};

        int inc = 1, dec = 1; // increasing and decreasing streak through this node

        if (node.left != null) {
            int[] left = dfs(node.left);
            if (node.val == node.left.val + 1) inc = Math.max(inc, left[0] + 1); // e.g. parent=5, left=4
            if (node.val == node.left.val - 1) dec = Math.max(dec, left[1] + 1); // e.g. parent=3, left=4
        }

        if (node.right != null) {
            int[] right = dfs(node.right);
            if (node.val == node.right.val + 1) inc = Math.max(inc, right[0] + 1);
            if (node.val == node.right.val - 1) dec = Math.max(dec, right[1] + 1);
        }

        // Path through this node can combine increasing from one side + decreasing from other
        maxLen = Math.max(maxLen, inc + dec - 1);
        return new int[]{inc, dec};
    }
}
```

### Test Cases
```java
// Test 1: Path goes left-root-right
//     2
//    / \
//   1   3
// Path 1-2-3 length = 3
TreeNode root1 = new TreeNode(2);
root1.left = new TreeNode(1); root1.right = new TreeNode(3);
assert new BinaryTreeLongestConsecutiveSequenceII().longestConsecutive(root1) == 3;

// Test 2: Only one direction
//     1
//      \
//       2
//        \
//         3
TreeNode root2 = new TreeNode(1);
root2.right = new TreeNode(2);
root2.right.right = new TreeNode(3);
assert new BinaryTreeLongestConsecutiveSequenceII().longestConsecutive(root2) == 3;

// Test 3: Decreasing path
//     3
//    /
//   2
//  /
// 1
TreeNode root3 = new TreeNode(3);
root3.left = new TreeNode(2);
root3.left.left = new TreeNode(1);
assert new BinaryTreeLongestConsecutiveSequenceII().longestConsecutive(root3) == 3;
```

---

## 444. Sequence Reconstruction

### Problem Statement
You are given an integer array `nums` (a permutation of `[1, n]`) and a list of `sequences`. Determine whether `nums` is the **only shortest supersequence** that can be reconstructed from `sequences`. In other words, check if `nums` is the **unique** topological ordering implied by `sequences`.

### Java Solution
```java
import java.util.*;

class SequenceReconstruction {
    public boolean sequenceReconstruction(int[] nums, List<List<Integer>> sequences) {
        int n = nums.length;
        Map<Integer, Set<Integer>> adj = new HashMap<>();
        int[] inDegree = new int[n + 1];

        // Initialize adjacency list
        for (int i = 1; i <= n; i++) adj.put(i, new HashSet<>());

        // Build graph from sequences
        for (List<Integer> seq : sequences) {
            for (int i = 0; i < seq.size() - 1; i++) {
                int u = seq.get(i), v = seq.get(i + 1);
                if (u > n || v > n) return false;
                if (!adj.get(u).contains(v)) {
                    adj.get(u).add(v);
                    inDegree[v]++;
                }
            }
        }

        // Topological sort (Kahn's) — must have exactly one node with inDegree 0 at all times
        Queue<Integer> queue = new LinkedList<>();
        for (int i = 1; i <= n; i++) if (inDegree[i] == 0) queue.offer(i);

        int idx = 0;
        while (queue.size() == 1) {  // unique ordering → queue always has exactly 1 node
            int node = queue.poll();
            if (nums[idx++] != node) return false;
            for (int neighbor : adj.get(node)) {
                if (--inDegree[neighbor] == 0) queue.offer(neighbor);
            }
        }
        return idx == n;
    }
}
```

### Test Cases
```java
// Test 1: nums is the unique reconstruction
int[] nums1 = {1,2,3};
List<List<Integer>> seqs1 = Arrays.asList(
    Arrays.asList(1,2), Arrays.asList(1,3), Arrays.asList(2,3)
);
assert new SequenceReconstruction().sequenceReconstruction(nums1, seqs1) == true;

// Test 2: Ambiguous ordering (two valid topological sorts)
int[] nums2 = {1,2,3};
List<List<Integer>> seqs2 = Arrays.asList(
    Arrays.asList(1,2), Arrays.asList(1,3)
    // No constraint between 2 and 3 → both [1,2,3] and [1,3,2] are valid
);
assert new SequenceReconstruction().sequenceReconstruction(nums2, seqs2) == false;

// Test 3: Sequence contains a number out of range
int[] nums3 = {1,2,3};
List<List<Integer>> seqs3 = Arrays.asList(Arrays.asList(1,2), Arrays.asList(5,3));
assert new SequenceReconstruction().sequenceReconstruction(nums3, seqs3) == false;
```

---

## 683. K Empty Slots

### Problem Statement
You have `n` bulbs in a row labeled `1` to `n`. You are given an integer array `bulbs` where `bulbs[i]` is the position of the bulb that gets turned **ON** on day `i+1`. You are given an integer `k`. Return the **minimum day number** such that there exist two bulbs that are turned ON with exactly `k` bulbs between them that are all **OFF**. Return `-1` if no such day exists.

### Java Solution
```java
import java.util.TreeSet;

class KEmptySlots {
    public int kEmptySlots(int[] bulbs, int k) {
        TreeSet<Integer> active = new TreeSet<>(); // currently ON bulbs (by position)

        for (int day = 0; day < bulbs.length; day++) {
            int pos = bulbs[day];
            active.add(pos);

            // Check left neighbor: is there a bulb exactly k+1 positions to the left?
            Integer lower = active.lower(pos);
            if (lower != null && pos - lower - 1 == k) return day + 1;

            // Check right neighbor: is there a bulb exactly k+1 positions to the right?
            Integer higher = active.higher(pos);
            if (higher != null && higher - pos - 1 == k) return day + 1;
        }
        return -1;
    }
}
```

### Test Cases
```java
// Test 1: k=1 — gap of exactly 1 off bulb between two lit bulbs
int[] bulbs1 = {1, 3, 2};
// Day 1: {1}   — no neighbors
// Day 2: {1,3} — pos3, lower=1, 3-1-1=1=k ✓ → return day 2
assert new KEmptySlots().kEmptySlots(bulbs1, 1) == 2;

// Test 2: k=1 — first valid day is day 3 (positions 2 and 4 with exactly 1 gap: pos 3)
int[] bulbs2 = {1, 4, 2, 5, 3};
// Day 1: {1}    — no neighbors
// Day 2: {1,4}  — 4-1-1=2 ≠ 1
// Day 3: {1,2,4}— lower(2)=1: 2-1-1=0≠1; higher(2)=4: 4-2-1=1=k ✓ → return day 3
assert new KEmptySlots().kEmptySlots(bulbs2, 1) == 3;

// Test 3: No valid day — bulbs lit consecutively, no gap of size k=2 ever forms
int[] bulbs3 = {1, 2, 3};
assert new KEmptySlots().kEmptySlots(bulbs3, 2) == -1;
```

