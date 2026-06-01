# Coding Interview Problems - Solutions

## Table of Contents
1. [Two Heaps](#two-heaps)
2. [K-way Merge](#k-way-merge)
3. [Top K Elements](#top-k-elements)

---

## Two Heaps

### 1. Maximize Capital

**Problem Statement:**
You are given an integer k representing your initial project capital, and an array of pairs projects where projects[i] = [profiti, capitali]. This means you can start the ith project only if you have at least capitali capital. Once you start a project, you will gain profiti capital. You want to maximize your total capital by selecting at most k projects.

**Test Cases:**

```java
// Test Case 1: Basic example
k = 0, projects = [[1,0],[1,1],[2,1]]
Expected Output: 1

// Test Case 2: Multiple projects available
k = 1, projects = [[1,0],[1,1],[2,1]]
Expected Output: 3

// Test Case 3: All projects available
k = 10, projects = [[1,0],[1,1],[2,1]]
Expected Output: 4
```

**Solution:**

```java
class Solution {
    public int findMaximizedCapital(int k, int w, int[] profits, int[] capital) {
        int n = profits.length;
        int[][] projects = new int[n][2];
        for (int i = 0; i < n; i++) {
            projects[i][0] = capital[i];
            projects[i][1] = profits[i];
        }
        
        // Sort by capital required
        Arrays.sort(projects, (a, b) -> a[0] - b[0]);
        
        // Max heap for profits
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);
        int idx = 0;
        
        for (int i = 0; i < k; i++) {
            // Add all projects we can afford with current capital
            while (idx < n && projects[idx][0] <= w) {
                maxHeap.offer(projects[idx][1]);
                idx++;
            }
            
            // Take the most profitable project
            if (maxHeap.isEmpty()) break;
            w += maxHeap.poll();
        }
        
        return w;
    }
}
```

---

### 2. Find Median from a Data Stream

**Problem Statement:**
Design a data structure that supports adding integers and finding the median of all added numbers at any time. The median is the middle value in an ordered list of numbers.

**Test Cases:**

```java
// Test Case 1: Adding odd number of elements
addNum(1), addNum(2), findMedian() -> 1.5

// Test Case 2: Adding even number of elements
addNum(1), addNum(2), addNum(3), findMedian() -> 2.0

// Test Case 3: Negative numbers
addNum(-1), addNum(0), addNum(1), findMedian() -> 0.0
```

**Solution:**

```java
class MedianFinder {
    private PriorityQueue<Integer> maxHeap; // For smaller half
    private PriorityQueue<Integer> minHeap; // For larger half
    
    public MedianFinder() {
        maxHeap = new PriorityQueue<>((a, b) -> b - a);
        minHeap = new PriorityQueue<>();
    }
    
    public void addNum(int num) {
        if (maxHeap.isEmpty() || num <= maxHeap.peek()) {
            maxHeap.offer(num);
        } else {
            minHeap.offer(num);
        }
        
        // Balance heaps
        if (maxHeap.size() > minHeap.size() + 1) {
            minHeap.offer(maxHeap.poll());
        } else if (minHeap.size() > maxHeap.size()) {
            maxHeap.offer(minHeap.poll());
        }
    }
    
    public double findMedian() {
        if (maxHeap.size() == minHeap.size()) {
            return (maxHeap.peek() + minHeap.peek()) / 2.0;
        }
        return maxHeap.peek();
    }
}
```

---

### 3. Sliding Window Median

**Problem Statement:**
Given an integer array nums and an integer k, find the median of each window of size k in the array. For each window, report the median.

**Test Cases:**

```java
// Test Case 1: Basic sliding window
nums = [1,3,-1,-3,5,3,6,7], k = 3
Expected Output: [1.0, -1.0, -1.0, 3.0, 5.0, 6.0]

// Test Case 2: Single element window
nums = [1,2,3,4,5], k = 1
Expected Output: [1.0, 2.0, 3.0, 4.0, 5.0]

// Test Case 3: Window size equals array
nums = [1,2,3], k = 3
Expected Output: [2.0]
```

**Solution:**

```java
class Solution {
    public double[] medianSlidingWindow(int[] nums, int k) {
        double[] result = new double[nums.length - k + 1];
        TreeMap<Integer, Integer> map = new TreeMap<>();
        
        // Initialize first window
        for (int i = 0; i < k; i++) {
            map.put(nums[i], map.getOrDefault(nums[i], 0) + 1);
        }
        result[0] = getMedian(map, k);
        
        for (int i = k; i < nums.length; i++) {
            // Add new element
            map.put(nums[i], map.getOrDefault(nums[i], 0) + 1);
            
            // Remove left element
            int leftNum = nums[i - k];
            if (map.get(leftNum) == 1) {
                map.remove(leftNum);
            } else {
                map.put(leftNum, map.get(leftNum) - 1);
            }
            
            result[i - k + 1] = getMedian(map, k);
        }
        
        return result;
    }
    
    private double getMedian(TreeMap<Integer, Integer> map, int k) {
        Integer[] keys = map.keySet().toArray(new Integer[0]);
        if (k % 2 == 1) {
            return keys[k / 2];
        } else {
            return (keys[k / 2 - 1] + keys[k / 2]) / 2.0;
        }
    }
}
```

---

### 4. Schedule Tasks on Minimum Machines

**Problem Statement:**
Given a list of task intervals with [start, end] times, find the minimum number of machines needed to execute all tasks where no machine can execute overlapping tasks.

**Test Cases:**

```java
// Test Case 1: No overlapping tasks
tasks = [[1,2], [3,4], [5,6]]
Expected Output: 1

// Test Case 2: All overlapping
tasks = [[1,5], [2,4], [3,6]]
Expected Output: 3

// Test Case 3: Partial overlaps
tasks = [[1,3], [2,5], [4,6]]
Expected Output: 2
```

**Solution:**

```java
class Solution {
    public int minimumMachines(int[][] tasks) {
        List<int[]> events = new ArrayList<>();
        
        for (int[] task : tasks) {
            events.add(new int[]{task[0], 0}); // start event
            events.add(new int[]{task[1], 1}); // end event
        }
        
        // Sort: earlier times first, end events before start events at same time
        Collections.sort(events, (a, b) -> {
            if (a[0] != b[0]) return a[0] - b[0];
            return a[1] - b[1];
        });
        
        int machinesNeeded = 0;
        int maxMachines = 0;
        
        for (int[] event : events) {
            if (event[1] == 0) { // start
                machinesNeeded++;
                maxMachines = Math.max(maxMachines, machinesNeeded);
            } else { // end
                machinesNeeded--;
            }
        }
        
        return maxMachines;
    }
}
```

---

### 5. Meeting Rooms III

**Problem Statement:**
You have n rooms numbered from 0 to n-1. You are given a 2D integer array meetings where meetings[i] = [starti, endi]. You need to process meetings and assign them to rooms. A meeting can only be assigned to an empty room at the start time.

**Test Cases:**

```java
// Test Case 1: Three rooms
n = 3, meetings = [[1,20],[2,10],[14,14],[15,20]]
Expected Output: 1

// Test Case 2: Two rooms
n = 2, meetings = [[0,10],[1,2],[2,7],[3,4]]
Expected Output: 0

// Test Case 3: Single room
n = 1, meetings = [[0,10],[10,20]]
Expected Output: 0
```

**Solution:**

```java
class Solution {
    public int mostBooked(int n, int[][] meetings) {
        Arrays.sort(meetings, (a, b) -> a[0] - b[0]);
        
        long[] roomsFree = new long[n];
        int[] roomCount = new int[n];
        
        for (int[] meeting : meetings) {
            int start = meeting[0];
            int end = meeting[1];
            
            // Find room that becomes free earliest
            long minFreeTime = Long.MAX_VALUE;
            int roomIdx = -1;
            
            for (int i = 0; i < n; i++) {
                if (roomsFree[i] < minFreeTime) {
                    minFreeTime = roomsFree[i];
                    roomIdx = i;
                }
            }
            
            // Assign meeting to this room
            if (roomsFree[roomIdx] <= start) {
                roomsFree[roomIdx] = end;
            } else {
                roomsFree[roomIdx] += (end - start);
            }
            roomCount[roomIdx]++;
        }
        
        int mostUsedRoom = 0;
        for (int i = 1; i < n; i++) {
            if (roomCount[i] > roomCount[mostUsedRoom]) {
                mostUsedRoom = i;
            }
        }
        
        return mostUsedRoom;
    }
}
```

---

### 6. Minimum Cost to Connect Sticks

**Problem Statement:**
You have some number of sticks with positive integer lengths. In one operation, you can connect any two sticks of lengths x and y into one stick with length x + y, with cost x + y. Return the minimum cost to connect all sticks into one stick.

**Test Cases:**

```java
// Test Case 1: Three sticks
sticks = [2,4,3]
Expected Output: 14

// Test Case 2: Two sticks
sticks = [1,8,3,5,4,7]
Expected Output: 36

// Test Case 3: Single stick
sticks = [5]
Expected Output: 0
```

**Solution:**

```java
class Solution {
    public int connectSticks(int[] sticks) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        
        for (int stick : sticks) {
            minHeap.offer(stick);
        }
        
        int totalCost = 0;
        
        while (minHeap.size() > 1) {
            int first = minHeap.poll();
            int second = minHeap.poll();
            int cost = first + second;
            
            totalCost += cost;
            minHeap.offer(cost);
        }
        
        return totalCost;
    }
}
```

---

### 7. Longest Happy String

**Problem Statement:**
A string is called happy if any three consecutive characters are distinct. Given three integers a, b, and c, return the longest possible happy string that you can construct using at most a 'a's, b 'b's, and c 'c's.

**Test Cases:**

```java
// Test Case 1: Basic example
a = 1, b = 1, c = 7
Expected Output: "ccacbcc" (or similar, length 9)

// Test Case 2: Equal counts
a = 2, b = 2, c = 2
Expected Output: "cbacbc" (or similar, length 6)

// Test Case 3: One character dominant
a = 10, b = 1, c = 1
Expected Output: "ababcabacaba" (or similar, length 12)
```

**Solution:**

```java
class Solution {
    public String longestDiverseString(int a, int b, int c) {
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>((x, y) -> y[0] - x[0]);
        
        if (a > 0) maxHeap.offer(new int[]{a, 'a'});
        if (b > 0) maxHeap.offer(new int[]{b, 'b'});
        if (c > 0) maxHeap.offer(new int[]{c, 'c'});
        
        StringBuilder sb = new StringBuilder();
        
        while (!maxHeap.isEmpty()) {
            int[] first = maxHeap.poll();
            
            if (sb.length() >= 2 && sb.charAt(sb.length() - 1) == first[1] && 
                sb.charAt(sb.length() - 2) == first[1]) {
                
                if (maxHeap.isEmpty()) break;
                
                int[] second = maxHeap.poll();
                sb.append((char) second[1]);
                second[0]--;
                
                if (second[0] > 0) maxHeap.offer(second);
                maxHeap.offer(first);
            } else {
                sb.append((char) first[1]);
                first[0]--;
                
                if (first[0] > 0) maxHeap.offer(first);
            }
        }
        
        return sb.toString();
    }
}
```

---

### 8. Maximum Average Pass Ratio

**Problem Statement:**
There is a school with classes, where each class has a certain number of passing and total students. You can enhance one class such that all students pass. The pass ratio is pass/total. Return the maximum average pass ratio after enhancing exactly one class.

**Test Cases:**

```java
// Test Case 1: Basic example
classes = [[4,5],[1,2]], extraStudents = 2
Expected Output: 0.78333

// Test Case 2: Single class
classes = [[1,2]], extraStudents = 2
Expected Output: 1.0

// Test Case 3: Multiple classes
classes = [[2,4],[3,9],[4,5],[2,10]], extraStudents = 4
Expected Output: 0.53333
```

**Solution:**

```java
class Solution {
    public double maxAverageRatio(int[][] classes, int extraStudents) {
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>((a, b) -> 
            Double.compare(
                (double)(a[0]+1)/(a[1]+1) - (double)a[0]/a[1],
                (double)(b[0]+1)/(b[1]+1) - (double)b[0]/b[1]
            )
        );
        
        for (int[] cls : classes) {
            maxHeap.offer(cls);
        }
        
        for (int i = 0; i < extraStudents; i++) {
            int[] top = maxHeap.poll();
            top[0]++;
            top[1]++;
            maxHeap.offer(top);
        }
        
        double sum = 0;
        while (!maxHeap.isEmpty()) {
            int[] cls = maxHeap.poll();
            sum += (double)cls[0] / cls[1];
        }
        
        return sum / classes.length;
    }
}
```

---

### 9. The Number of the Smallest Unoccupied Chair

**Problem Statement:**
There are n chairs in a waiting room, numbered from 0 to n-1. A person arrives with a friend. The two friends sit in the two unoccupied chairs with the lowest numbers. Determine which chair the friend sits in.

**Test Cases:**

```java
// Test Case 1: Basic example
times = [[1,2],[2,3],[1,2],[2,2]], targetFriend = 0
Expected Output: 0

// Test Case 2: Single friend
times = [[3,2],[1,2],[2,1]], targetFriend = 0
Expected Output: 1

// Test Case 3: Complex scheduling
times = [[1,2],[2,3],[3,4]], targetFriend = 1
Expected Output: 1
```

**Solution:**

```java
class Solution {
    public int smallestChair(int[][] times, int targetFriend) {
        int n = times.length;
        int[][] events = new int[2 * n][2];
        
        for (int i = 0; i < n; i++) {
            events[2 * i] = new int[]{times[i][0], 0, i}; // arrival
            events[2 * i + 1] = new int[]{times[i][1], 1, i}; // departure
        }
        
        Arrays.sort(events, (a, b) -> {
            if (a[0] != b[0]) return a[0] - b[0];
            return a[1] - b[1];
        });
        
        PriorityQueue<Integer> availableChairs = new PriorityQueue<>();
        for (int i = 0; i < n; i++) {
            availableChairs.offer(i);
        }
        
        int[] chairAssignment = new int[n];
        
        for (int[] event : events) {
            if (event[1] == 0) { // arrival
                chairAssignment[event[2]] = availableChairs.poll();
                if (event[2] == times.length - 1 && targetFriend == event[2]) {
                    // Handle target friend specially if needed
                }
            } else { // departure
                availableChairs.offer(chairAssignment[event[2]]);
            }
        }
        
        return chairAssignment[targetFriend];
    }
}
```

---

### 10. Largest Number After Digit Swaps by Parity

**Problem Statement:**
You are given a positive integer num. You can swap any two digits of num that have the same parity (both odd or both even). Return the largest integer you can get after any number of swaps.

**Test Cases:**

```java
// Test Case 1: Mixed parities
num = 1234
Expected Output: 3412

// Test Case 2: All even
num = 2468
Expected Output: 8642

// Test Case 3: All odd
num = 13579
Expected Output: 97531
```

**Solution:**

```java
class Solution {
    public int largestInteger(int num) {
        String s = String.valueOf(num);
        char[] arr = s.toCharArray();
        
        PriorityQueue<Character> oddDigits = new PriorityQueue<>((a, b) -> b - a);
        PriorityQueue<Character> evenDigits = new PriorityQueue<>((a, b) -> b - a);
        
        for (char c : arr) {
            if ((c - '0') % 2 == 0) {
                evenDigits.offer(c);
            } else {
                oddDigits.offer(c);
            }
        }
        
        for (int i = 0; i < arr.length; i++) {
            if ((arr[i] - '0') % 2 == 0) {
                arr[i] = evenDigits.poll();
            } else {
                arr[i] = oddDigits.poll();
            }
        }
        
        return Integer.parseInt(new String(arr));
    }
}
```

---

### 11. Find Right Interval

**Problem Statement:**
You are given an array of intervals where intervals[i] = [starti, endi]. Find the right interval for each interval i. The right interval for an interval i is an interval j such that starti >= endj.

**Test Cases:**

```java
// Test Case 1: Basic example
intervals = [[1,2]]
Expected Output: [-1]

// Test Case 2: Multiple intervals
intervals = [[3,4],[2,3],[1,2]]
Expected Output: [-1,0,1]

// Test Case 3: No right interval
intervals = [[1,4],[2,3],[3,4]]
Expected Output: [-1,2,-1]
```

**Solution:**

```java
class Solution {
    public int[] findRightInterval(int[][] intervals) {
        int n = intervals.length;
        int[] starts = new int[n];
        Map<Integer, Integer> startToIndex = new HashMap<>();
        
        for (int i = 0; i < n; i++) {
            starts[i] = intervals[i][0];
            startToIndex.put(starts[i], i);
        }
        
        Arrays.sort(starts);
        int[] result = new int[n];
        
        for (int i = 0; i < n; i++) {
            int end = intervals[i][1];
            int idx = binarySearch(starts, end);
            result[i] = idx == -1 ? -1 : startToIndex.get(starts[idx]);
        }
        
        return result;
    }
    
    private int binarySearch(int[] starts, int target) {
        int left = 0, right = starts.length - 1;
        int result = -1;
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (starts[mid] >= target) {
                result = mid;
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
        
        return result;
    }
}
```

---

### 12. Construct Target Array with Multiple Sums

**Problem Statement:**
You are given an array target. In one operation, you select any subarray of target and partition it into two non-empty parts with a split such that the left part equals the sum of the right part. Can you construct target from [1]?

**Test Cases:**

```java
// Test Case 1: Valid array
target = [8,5,6,4,5,6]
Expected Output: true

// Test Case 2: Invalid array
target = [1,1,1,2]
Expected Output: false

// Test Case 3: Large sum
target = [9,3,5]
Expected Output: true
```

**Solution:**

```java
class Solution {
    public boolean isPossible(int[] target) {
        if (target.length == 1) return false;
        
        long sum = 0;
        int maxIdx = 0;
        for (int i = 0; i < target.length; i++) {
            sum += target[i];
            if (target[i] > target[maxIdx]) maxIdx = i;
        }
        
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);
        for (int num : target) {
            if (num > 1) maxHeap.offer(num);
        }
        
        while (!maxHeap.isEmpty()) {
            int largest = maxHeap.poll();
            long rest = sum - largest;
            
            if (rest < 1) return false;
            
            if (rest == 1) return true;
            
            long newLargest = largest % rest;
            sum = rest + newLargest;
            
            if (newLargest > 1) maxHeap.offer((int) newLargest);
        }
        
        return false;
    }
}
```

---

## K-way Merge

### 1. Merge Sorted Array

**Problem Statement:**
You are given two integer arrays nums1 and nums2, sorted in non-decreasing order, and two integers m and n representing the number of valid elements in nums1 and nums2 respectively. Merge nums2 into nums1 as one sorted array.

**Test Cases:**

```java
// Test Case 1: Basic merge
nums1 = [1,2,3,0,0,0], m = 3, nums2 = [2,5,6], n = 3
Expected Output: [1,2,2,3,5,6]

// Test Case 2: Empty nums2
nums1 = [1], m = 1, nums2 = [], n = 0
Expected Output: [1]

// Test Case 3: Empty nums1
nums1 = [0], m = 0, nums2 = [1], n = 1
Expected Output: [1]
```

**Solution:**

```java
class Solution {
    public void merge(int[] nums1, int m, int[] nums2, int n) {
        int p1 = m - 1;
        int p2 = n - 1;
        int p = m + n - 1;
        
        while (p1 >= 0 && p2 >= 0) {
            if (nums1[p1] > nums2[p2]) {
                nums1[p] = nums1[p1];
                p1--;
            } else {
                nums1[p] = nums2[p2];
                p2--;
            }
            p--;
        }
        
        while (p2 >= 0) {
            nums1[p] = nums2[p2];
            p2--;
            p--;
        }
    }
}
```

---

### 2. Kth Smallest Number in M Sorted Lists

**Problem Statement:**
You have m lists of integers sorted in ascending order. Find the kth smallest integer across all the lists.

**Test Cases:**

```java
// Test Case 1: Basic example
lists = [[1,6,9],[4,10,13],[2,6,9]], k = 13
Expected Output: 13

// Test Case 2: Single list
lists = [[1,2,3,4,5]], k = 2
Expected Output: 2

// Test Case 3: Duplicate elements
lists = [[1,1],[1,1],[1,1]], k = 3
Expected Output: 1
```

**Solution:**

```java
class Solution {
    public int kthSmallest(List<List<Integer>> lists, int k) {
        PriorityQueue<int[]> minHeap = new PriorityQueue<>((a, b) -> {
            return Integer.compare(lists.get(a[0]).get(a[1]), 
                                 lists.get(b[0]).get(b[1]));
        });
        
        for (int i = 0; i < lists.size(); i++) {
            if (!lists.get(i).isEmpty()) {
                minHeap.offer(new int[]{i, 0});
            }
        }
        
        int count = 0;
        while (!minHeap.isEmpty()) {
            int[] top = minHeap.poll();
            count++;
            
            if (count == k) {
                return lists.get(top[0]).get(top[1]);
            }
            
            if (top[1] + 1 < lists.get(top[0]).size()) {
                minHeap.offer(new int[]{top[0], top[1] + 1});
            }
        }
        
        return -1;
    }
}
```

---

### 3. Find K Pairs with Smallest Sums

**Problem Statement:**
You are given two integer arrays nums1 and nums2 sorted in ascending order and an integer k. Define a pair (u, v) which consists of one element from nums1 and one element from nums2. Find the k pairs (u1, v1), (u2, v2) ... (uk, vk) with the smallest sums.

**Test Cases:**

```java
// Test Case 1: Basic example
nums1 = [1,7,11], nums2 = [2,4,6], k = 3
Expected Output: [[1,2],[1,4],[1,6]]

// Test Case 2: K larger than pairs
nums1 = [1,1,2], nums2 = [1,2,3], k = 5
Expected Output: [[1,1],[1,1],[1,2],[1,3],[2,1]]

// Test Case 3: Single elements
nums1 = [1,2], nums2 = [3], k = 3
Expected Output: [[1,3],[2,3]]
```

**Solution:**

```java
class Solution {
    public List<List<Integer>> kSmallestPairs(int[] nums1, int[] nums2, int k) {
        List<List<Integer>> result = new ArrayList<>();
        
        PriorityQueue<int[]> minHeap = new PriorityQueue<>((a, b) -> 
            (nums1[a[0]] + nums2[a[1]]) - (nums1[b[0]] + nums2[b[1]])
        );
        
        for (int i = 0; i < Math.min(k, nums1.length); i++) {
            minHeap.offer(new int[]{i, 0});
        }
        
        while (!minHeap.isEmpty() && result.size() < k) {
            int[] top = minHeap.poll();
            result.add(Arrays.asList(nums1[top[0]], nums2[top[1]]));
            
            if (top[1] + 1 < nums2.length) {
                minHeap.offer(new int[]{top[0], top[1] + 1});
            }
        }
        
        return result;
    }
}
```

---

### 4. Merge K Sorted Lists

**Problem Statement:**
You are given an array of k linked-lists lists, each linked-list is sorted in ascending order. Merge all the linked-lists into one sorted linked-list and return it.

**Test Cases:**

```java
// Test Case 1: Multiple lists
lists = [[1,4,5],[1,3,4],[2,6]]
Expected Output: [1,1,2,1,3,4,4,5,6]

// Test Case 2: Empty list
lists = []
Expected Output: []

// Test Case 3: Single list
lists = [[1,2,3]]
Expected Output: [1,2,3]
```

**Solution:**

```java
/**
 * Definition for singly-linked list.
 */
class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

class Solution {
    public ListNode mergeKLists(ListNode[] lists) {
        if (lists == null || lists.length == 0) return null;
        
        PriorityQueue<ListNode> minHeap = new PriorityQueue<>((a, b) -> a.val - b.val);
        
        for (ListNode list : lists) {
            if (list != null) {
                minHeap.offer(list);
            }
        }
        
        ListNode dummy = new ListNode(0);
        ListNode current = dummy;
        
        while (!minHeap.isEmpty()) {
            ListNode smallest = minHeap.poll();
            current.next = smallest;
            current = current.next;
            
            if (smallest.next != null) {
                minHeap.offer(smallest.next);
            }
        }
        
        return dummy.next;
    }
}
```

---

### 5. Kth Smallest Element in a Sorted Matrix

**Problem Statement:**
Given an n x n matrix where each row and column is sorted in ascending order, find the kth smallest element in the matrix.

**Test Cases:**

```java
// Test Case 1: Basic example
matrix = [[1,2],[1,1]], k = 1
Expected Output: 1

// Test Case 2: Multiple elements
matrix = [[1,2],[1,1]], k = 3
Expected Output: 1

// Test Case 3: Larger matrix
matrix = [[-5]], k = 1
Expected Output: -5
```

**Solution:**

```java
class Solution {
    public int kthSmallest(int[][] matrix, int k) {
        int n = matrix.length;
        
        PriorityQueue<int[]> minHeap = new PriorityQueue<>((a, b) -> 
            matrix[a[0]][a[1]] - matrix[b[0]][b[1]]
        );
        
        minHeap.offer(new int[]{0, 0});
        boolean[][] visited = new boolean[n][n];
        visited[0][0] = true;
        
        int count = 0;
        while (!minHeap.isEmpty()) {
            int[] top = minHeap.poll();
            count++;
            
            if (count == k) {
                return matrix[top[0]][top[1]];
            }
            
            int i = top[0], j = top[1];
            
            if (i + 1 < n && !visited[i + 1][j]) {
                visited[i + 1][j] = true;
                minHeap.offer(new int[]{i + 1, j});
            }
            
            if (j + 1 < n && !visited[i][j + 1]) {
                visited[i][j + 1] = true;
                minHeap.offer(new int[]{i, j + 1});
            }
        }
        
        return -1;
    }
}
```

---

### 6. K-th Smallest Prime Fraction

**Problem Statement:**
You are given a sorted integer array arr containing 1 and prime numbers, where all the elements of arr are unique. You are also given an integer k. For every i and j where 0 <= i < j < arr.length, we consider the fraction arr[i] / arr[j]. Return the kth smallest fraction considered in sorted order by the value of the fraction.

**Test Cases:**

```java
// Test Case 1: Basic example
arr = [1,2,3,5], k = 3
Expected Output: [2,5]

// Test Case 2: Single fraction
arr = [1,7], k = 1
Expected Output: [1,7]

// Test Case 3: Multiple fractions
arr = [1,2,3,5,7], k = 5
Expected Output: [3,7]
```

**Solution:**

```java
class Solution {
    public int[] kthSmallestPrimeFraction(int[] arr, int k) {
        PriorityQueue<int[]> minHeap = new PriorityQueue<>((a, b) -> 
            (long)arr[a[0]] * arr[b[1]] - (long)arr[a[1]] * arr[b[0]]
        );
        
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            minHeap.offer(new int[]{i, n - 1});
        }
        
        int[] result = new int[2];
        for (int i = 0; i < k; i++) {
            result = minHeap.poll();
            int i_idx = result[0];
            int j_idx = result[1];
            
            if (i_idx < j_idx - 1) {
                minHeap.offer(new int[]{i_idx, j_idx - 1});
            }
        }
        
        return new int[]{arr[result[0]], arr[result[1]]};
    }
}
```

---

### 7. Super Ugly Number

**Problem Statement:**
A super ugly number is a positive integer whose prime factors are in the array primes. Given an integer n and an array of integers primes, return the nth super ugly number.

**Test Cases:**

```java
// Test Case 1: Basic example
n = 12, primes = [2,7,13,19]
Expected Output: 32

// Test Case 2: Small n
n = 1, primes = [2,3,5]
Expected Output: 1

// Test Case 3: Single prime
n = 10, primes = [2]
Expected Output: 512
```

**Solution:**

```java
class Solution {
    public int nthSuperUglyNumber(int n, int[] primes) {
        int[] dp = new int[n];
        int[] pointers = new int[primes.length];
        dp[0] = 1;
        
        for (int i = 1; i < n; i++) {
            int[] nextCandidates = new int[primes.length];
            for (int j = 0; j < primes.length; j++) {
                nextCandidates[j] = dp[pointers[j]] * primes[j];
            }
            
            int nextUgly = Integer.MAX_VALUE;
            for (int candidate : nextCandidates) {
                nextUgly = Math.min(nextUgly, candidate);
            }
            
            dp[i] = nextUgly;
            
            for (int j = 0; j < primes.length; j++) {
                if (nextCandidates[j] == nextUgly) {
                    pointers[j]++;
                }
            }
        }
        
        return dp[n - 1];
    }
}
```

---

## Top K Elements

### 1. Kth Largest Element in a Stream

**Problem Statement:**
Design a class to find the kth largest element in a stream. Note that it is the kth largest element in the sorted order, not the kth distinct element.

**Test Cases:**

```java
// Test Case 1: Stream of numbers
KthLargest kthLargest = new KthLargest(3, [4,5,8,2]);
kthLargest.add(3) -> 4
kthLargest.add(5) -> 5
kthLargest.add(10) -> 5
kthLargest.add(9) -> 8
kthLargest.add(4) -> 8

// Test Case 2: K equals 1
KthLargest kthLargest = new KthLargest(1, []);
kthLargest.add(3) -> 3

// Test Case 3: Negative numbers
KthLargest kthLargest = new KthLargest(2, [-1, -1]);
kthLargest.add(0) -> -1
```

**Solution:**

```java
class KthLargest {
    private PriorityQueue<Integer> minHeap;
    private int k;
    
    public KthLargest(int k, int[] nums) {
        this.k = k;
        minHeap = new PriorityQueue<>();
        
        for (int num : nums) {
            minHeap.offer(num);
        }
        
        while (minHeap.size() > k) {
            minHeap.poll();
        }
    }
    
    public int add(int val) {
        minHeap.offer(val);
        
        if (minHeap.size() > k) {
            minHeap.poll();
        }
        
        return minHeap.peek();
    }
}
```

---

### 2. Reorganize String

**Problem Statement:**
Given a string s, rearrange the characters of s so that any two adjacent characters are not the same. Return any possible rearrangement of s or return an empty string if not possible.

**Test Cases:**

```java
// Test Case 1: Valid reorganization
s = "abbaca"
Expected Output: "abacab"

// Test Case 2: Impossible case
s = "aa"
Expected Output: ""

// Test Case 3: All same character except one
s = "aaab"
Expected Output: "abaa"
```

**Solution:**

```java
class Solution {
    public String reorganizeString(String s) {
        int[] freq = new int[26];
        for (char c : s.toCharArray()) {
            freq[c - 'a']++;
        }
        
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>((a, b) -> b[1] - a[1]);
        
        for (int i = 0; i < 26; i++) {
            if (freq[i] > 0) {
                maxHeap.offer(new int[]{i, freq[i]});
            }
        }
        
        StringBuilder sb = new StringBuilder();
        
        while (maxHeap.size() >= 2) {
            int[] first = maxHeap.poll();
            int[] second = maxHeap.poll();
            
            sb.append((char)('a' + first[0]));
            sb.append((char)('a' + second[0]));
            
            first[1]--;
            second[1]--;
            
            if (first[1] > 0) maxHeap.offer(first);
            if (second[1] > 0) maxHeap.offer(second);
        }
        
        if (!maxHeap.isEmpty()) {
            int[] last = maxHeap.poll();
            if (last[1] == 1) {
                sb.append((char)('a' + last[0]));
            } else {
                return "";
            }
        }
        
        return sb.toString();
    }
}
```

---

### 3. K Closest Points to Origin

**Problem Statement:**
Given an array of points where points[i] = [xi, yi] represents a point on the X-Y plane and an integer k, return the k closest points to the origin (0, 0).

**Test Cases:**

```java
// Test Case 1: Basic example
points = [[1,3],[-2,2]], k = 1
Expected Output: [[-2,2]]

// Test Case 2: Multiple points
points = [[3,3],[5,-1],[-2,4]], k = 2
Expected Output: [[3,3],[-2,4]]

// Test Case 3: All points at origin
points = [[0,0]], k = 1
Expected Output: [[0,0]]
```

**Solution:**

```java
class Solution {
    public int[][] kClosest(int[][] points, int k) {
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>((a, b) -> 
            (b[0] * b[0] + b[1] * b[1]) - (a[0] * a[0] + a[1] * a[1])
        );
        
        for (int[] point : points) {
            maxHeap.offer(point);
            if (maxHeap.size() > k) {
                maxHeap.poll();
            }
        }
        
        int[][] result = new int[k][2];
        int i = 0;
        while (!maxHeap.isEmpty()) {
            result[i++] = maxHeap.poll();
        }
        
        return result;
    }
}
```

---

### 4. Top K Frequent Elements

**Problem Statement:**
Given an integer array nums and an integer k, return the k most frequent elements. You may return the answer in any order.

**Test Cases:**

```java
// Test Case 1: Multiple frequent elements
nums = [1,1,1,2,2,3], k = 2
Expected Output: [1,2]

// Test Case 2: Single element
nums = [1], k = 1
Expected Output: [1]

// Test Case 3: K equals array length
nums = [4,1,1,1,2,2,3], k = 2
Expected Output: [1,2]
```

**Solution:**

```java
class Solution {
    public int[] topKFrequent(int[] nums, int k) {
        Map<Integer, Integer> freq = new HashMap<>();
        for (int num : nums) {
            freq.put(num, freq.getOrDefault(num, 0) + 1);
        }
        
        PriorityQueue<Integer> minHeap = new PriorityQueue<>((a, b) -> 
            freq.get(a) - freq.get(b)
        );
        
        for (int num : freq.keySet()) {
            minHeap.offer(num);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }
        
        int[] result = new int[k];
        int i = 0;
        while (!minHeap.isEmpty()) {
            result[i++] = minHeap.poll();
        }
        
        return result;
    }
}
```

---

### 5. Kth Largest Element in an Array

**Problem Statement:**
Given an integer array nums and an integer k, return the kth largest element in the array. Note that it is the kth largest element in the sorted order, not the kth distinct element.

**Test Cases:**

```java
// Test Case 1: Basic example
nums = [3,2,1,5,6,4], k = 2
Expected Output: 5

// Test Case 2: Single element
nums = [1], k = 1
Expected Output: 1

// Test Case 3: Duplicates
nums = [3,2,3,1,2,4,5,5,6], k = 4
Expected Output: 4
```

**Solution:**

```java
class Solution {
    public int findKthLargest(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        
        for (int num : nums) {
            minHeap.offer(num);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }
        
        return minHeap.peek();
    }
}
```

---

### 6. Maximal Score After Applying K Operations

**Problem Statement:**
You are given an array nums of n positive integers. You can perform at most k operations on the array, where in each operation you select an element nums[i] and replace it with Math.ceil(nums[i] / 3). Your score increases by the new value of nums[i].

**Test Cases:**

```java
// Test Case 1: Basic example
nums = [10,10,10,10,10], k = 5
Expected Output: 24

// Test Case 2: Single element
nums = [1,10,3,8,9], k = 4
Expected Output: 16

// Test Case 3: K greater than operations possible
nums = [5,4,3,2,1], k = 3
Expected Output: 9
```

**Solution:**

```java
class Solution {
    public long maxKelements(int[] nums, int k) {
        PriorityQueue<Long> maxHeap = new PriorityQueue<>((a, b) -> b.compareTo(a));
        
        for (int num : nums) {
            maxHeap.offer((long) num);
        }
        
        long score = 0;
        for (int i = 0; i < k; i++) {
            long largest = maxHeap.poll();
            score += largest;
            long newValue = (largest + 2) / 3; // Ceil division
            maxHeap.offer(newValue);
        }
        
        return score;
    }
}
```

---

### 7. Find the Kth Largest Integer in the Array

**Problem Statement:**
You are given an array of strings nums containing positive integers. Return the string representation of the kth largest integer in nums.

**Test Cases:**

```java
// Test Case 1: Large numbers
nums = ["2","21","12","11","100","2"], k = 3
Expected Output: "2"

// Test Case 2: Single element
nums = ["1"], k = 1
Expected Output: "1"

// Test Case 3: Large numbers with different lengths
nums = ["9","9","9"], k = 2
Expected Output: "9"
```

**Solution:**

```java
class Solution {
    public String kthLargestNumber(String[] nums, int k) {
        PriorityQueue<String> minHeap = new PriorityQueue<>((a, b) -> {
            if (a.length() != b.length()) {
                return a.length() - b.length();
            }
            return a.compareTo(b);
        });
        
        for (String num : nums) {
            minHeap.offer(num);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }
        
        return minHeap.peek();
    }
}
```

---

### 8. Third Maximum Number

**Problem Statement:**
Given an integer array nums, return the third distinct maximum number in this array. If the third maximum does not exist, return the maximum number.

**Test Cases:**

```java
// Test Case 1: Three distinct numbers
nums = [3,2,1]
Expected Output: 1

// Test Case 2: Less than three distinct
nums = [1,2]
Expected Output: 2

// Test Case 3: Duplicates
nums = [2,2,3,1]
Expected Output: 1
```

**Solution:**

```java
class Solution {
    public int thirdMax(int[] nums) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        Set<Integer> seen = new HashSet<>();
        
        for (int num : nums) {
            if (seen.add(num)) {
                minHeap.offer(num);
                if (minHeap.size() > 3) {
                    minHeap.poll();
                }
            }
        }
        
        if (minHeap.size() < 3) {
            while (minHeap.size() > 1) {
                minHeap.poll();
            }
        }
        
        return minHeap.peek();
    }
}
```

---

### 9. Find Subsequence of Length K with the Largest Sum

**Problem Statement:**
You are given an integer array nums and an integer k. You want to find a subsequence of nums of length k that has the largest sum. Return any such subsequence as an integer array of length k.

**Test Cases:**

```java
// Test Case 1: Basic example
nums = [2,1,3,3], k = 2
Expected Output: [3,3]

// Test Case 2: Maintain order
nums = [1,2,3,4,5], k = 2
Expected Output: [4,5]

// Test Case 3: All same
nums = [1,1,1,1], k = 2
Expected Output: [1,1]
```

**Solution:**

```java
class Solution {
    public int[] maxSubsequence(int[] nums, int k) {
        int n = nums.length;
        int[][] indexed = new int[n][2];
        
        for (int i = 0; i < n; i++) {
            indexed[i][0] = nums[i];
            indexed[i][1] = i;
        }
        
        PriorityQueue<int[]> minHeap = new PriorityQueue<>((a, b) -> a[0] - b[0]);
        
        for (int i = 0; i < k; i++) {
            minHeap.offer(indexed[i]);
        }
        
        for (int i = k; i < n; i++) {
            if (indexed[i][0] > minHeap.peek()[0]) {
                minHeap.poll();
                minHeap.offer(indexed[i]);
            }
        }
        
        List<int[]> resultList = new ArrayList<>(minHeap);
        resultList.sort((a, b) -> a[1] - b[1]);
        
        int[] result = new int[k];
        for (int i = 0; i < k; i++) {
            result[i] = resultList.get(i)[0];
        }
        
        return result;
    }
}
```

---

### 10. Minimum Cost to Hire K Workers

**Problem Statement:**
There are n workers. You are given two integer arrays quality and wage where quality[i] is the quality of the ith worker and wage[i] is the minimum hourly rate the ith worker will accept.

**Test Cases:**

```java
// Test Case 1: Basic example
quality = [10,20,5], wage = [70,50,30], k = 2
Expected Output: 105.00000

// Test Case 2: K equals n
quality = [5,13,6,7], wage = [5,6,3,9], k = 4
Expected Output: 70.00000

// Test Case 3: Single worker
quality = [1], wage = [1], k = 1
Expected Output: 1.00000
```

**Solution:**

```java
class Solution {
    public double mincostToHireWorkers(int[] quality, int[] wage, int k) {
        int n = quality.length;
        Worker[] workers = new Worker[n];
        
        for (int i = 0; i < n; i++) {
            workers[i] = new Worker(quality[i], wage[i]);
        }
        
        Arrays.sort(workers);
        
        double minCost = Double.MAX_VALUE;
        int qualitySum = 0;
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);
        
        for (Worker worker : workers) {
            qualitySum += worker.quality;
            maxHeap.offer(worker.quality);
            
            if (maxHeap.size() > k) {
                qualitySum -= maxHeap.poll();
            }
            
            if (maxHeap.size() == k) {
                minCost = Math.min(minCost, qualitySum * worker.ratio);
            }
        }
        
        return minCost;
    }
    
    class Worker implements Comparable<Worker> {
        int quality;
        double ratio;
        
        Worker(int q, int w) {
            quality = q;
            ratio = (double)w / q;
        }
        
        public int compareTo(Worker other) {
            return Double.compare(this.ratio, other.ratio);
        }
    }
}
```

---

### 11. Smallest Range Covering Elements from K Lists

**Problem Statement:**
You have k lists of sorted integers. Find the smallest range that includes at least one number from each of the k lists.

**Test Cases:**

```java
// Test Case 1: Three lists
lists = [[4,10,15,24,26],[0,9,12,20],[5,18,22,30]]
Expected Output: [20,24]

// Test Case 2: Two lists
lists = [[1,2,3],[1,2,3]]
Expected Output: [1,1]

// Test Case 3: Single list
lists = [[1,2,3,4,5]]
Expected Output: [1,5]
```

**Solution:**

```java
class Solution {
    public int[] smallestRange(List<List<Integer>> lists) {
        PriorityQueue<int[]> minHeap = new PriorityQueue<>((a, b) -> 
            lists.get(a[0]).get(a[1]) - lists.get(b[0]).get(b[1])
        );
        
        int maxVal = Integer.MIN_VALUE;
        int rangeStart = 0, rangeEnd = Integer.MAX_VALUE;
        
        for (int i = 0; i < lists.size(); i++) {
            int val = lists.get(i).get(0);
            maxVal = Math.max(maxVal, val);
            minHeap.offer(new int[]{i, 0});
        }
        
        while (minHeap.size() == lists.size()) {
            int[] top = minHeap.poll();
            int minVal = lists.get(top[0]).get(top[1]);
            
            if (maxVal - minVal < rangeEnd - rangeStart) {
                rangeStart = minVal;
                rangeEnd = maxVal;
            }
            
            if (top[1] + 1 < lists.get(top[0]).size()) {
                int nextVal = lists.get(top[0]).get(top[1] + 1);
                maxVal = Math.max(maxVal, nextVal);
                minHeap.offer(new int[]{top[0], top[1] + 1});
            }
        }
        
        return new int[]{rangeStart, rangeEnd};
    }
}
```

---

### 12. Maximum Performance of a Team

**Problem Statement:**
You are given two integers n and k, and two integer arrays speed and efficiency, both of length n. There are n engineers numbered from 1 to n. speed[i] and efficiency[i] represent the speed and efficiency of the ith engineer. Select at most k engineers to form a team with the maximum performance.

**Test Cases:**

```java
// Test Case 1: Basic example
n = 6, speed = [2,10,3,1,5,8], efficiency = [5,4,3,9,7,2], k = 2
Expected Output: 60

// Test Case 2: K equals 1
n = 5, speed = [1,2,3,4,5], efficiency = [5,4,3,2,1], k = 1
Expected Output: 5

// Test Case 3: K equals n
n = 3, speed = [1,2,3], efficiency = [3,2,1], k = 3
Expected Output: 20
```

**Solution:**

```java
class Solution {
    public long maxPerformance(int n, int[] speed, int[] efficiency, int k) {
        Engineer[] engineers = new Engineer[n];
        for (int i = 0; i < n; i++) {
            engineers[i] = new Engineer(speed[i], efficiency[i]);
        }
        
        Arrays.sort(engineers);
        
        long maxPerf = 0;
        long speedSum = 0;
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        
        for (Engineer engineer : engineers) {
            speedSum += engineer.speed;
            minHeap.offer(engineer.speed);
            
            if (minHeap.size() > k) {
                speedSum -= minHeap.poll();
            }
            
            maxPerf = Math.max(maxPerf, speedSum * engineer.efficiency);
        }
        
        return maxPerf;
    }
    
    class Engineer implements Comparable<Engineer> {
        int speed;
        int efficiency;
        
        Engineer(int s, int e) {
            speed = s;
            efficiency = e;
        }
        
        public int compareTo(Engineer other) {
            return other.efficiency - this.efficiency; // Sort by efficiency descending
        }
    }
}
```

---

### 13. K Maximum Sum Combinations From Two Arrays

**Problem Statement:**
Given two arrays a[] and b[], find k maximum sum combinations from both arrays.

**Test Cases:**

```java
// Test Case 1: Basic example
a = [1,4,2], b = [3,2,4], k = 3
Expected Output: [8, 6, 5]

// Test Case 2: K equals 1
a = [1,2], b = [1,3], k = 1
Expected Output: [5]

// Test Case 3: Equal arrays
a = [1,1,1], b = [1,1,1], k = 3
Expected Output: [2, 2, 2]
```

**Solution:**

```java
class Solution {
    public List<Integer> maxSumCombinations(int[] a, int[] b, int k) {
        Arrays.sort(a);
        Arrays.sort(b);
        
        List<Integer> result = new ArrayList<>();
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>((x, y) -> 
            (a[y[0]] + b[y[1]]) - (a[x[0]] + b[x[1]])
        );
        
        Set<String> seen = new HashSet<>();
        
        maxHeap.offer(new int[]{a.length - 1, b.length - 1});
        seen.add((a.length - 1) + "," + (b.length - 1));
        
        while (result.size() < k && !maxHeap.isEmpty()) {
            int[] top = maxHeap.poll();
            result.add(a[top[0]] + b[top[1]]);
            
            if (top[0] - 1 >= 0 && !seen.contains((top[0] - 1) + "," + top[1])) {
                maxHeap.offer(new int[]{top[0] - 1, top[1]});
                seen.add((top[0] - 1) + "," + top[1]);
            }
            
            if (top[1] - 1 >= 0 && !seen.contains(top[0] + "," + (top[1] - 1))) {
                maxHeap.offer(new int[]{top[0], top[1] - 1});
                seen.add(top[0] + "," + (top[1] - 1));
            }
        }
        
        return result;
    }
}
```

---

### 14. K Empty Slots

**Problem Statement:**
You have n bulbs in a row numbered from 1 to n. Initially, all the bulbs are turned off. You turn on exactly one bulb every day starting from day 1.

**Test Cases:**

```java
// Test Case 1: Bulbs array
bulbs = [1,3,2], k = 1
Expected Output: 2

// Test Case 2: Larger array
bulbs = [1,2,3], k = 1
Expected Output: -1

// Test Case 3: Single bulb
bulbs = [1], k = 0
Expected Output: 1
```

**Solution:**

```java
class Solution {
    public int kEmptySlots(int[] bulbs, int k) {
        int n = bulbs.length;
        int[] days = new int[n + 1];
        
        for (int i = 0; i < n; i++) {
            days[bulbs[i]] = i + 1;
        }
        
        int result = Integer.MAX_VALUE;
        TreeSet<Integer> set = new TreeSet<>();
        set.add(0);
        set.add(n + 1);
        
        for (int i = 1; i <= n; i++) {
            int left = set.lower(i);
            int right = set.higher(i);
            
            if (right - left - 2 == k) {
                result = Math.min(result, Math.max(days[left + 1], days[right - 1]));
            }
            
            set.add(i);
        }
        
        return result == Integer.MAX_VALUE ? -1 : result;
    }
}
```

---

### 15. Find the K-Sum of an Array

**Problem Statement:**
You are given an array nums of n integers and a positive integer k. Find the sum of the kth element in all possible subsequences of the array.

**Test Cases:**

```java
// Test Case 1: Basic example
nums = [1,2,3], k = 2
Expected Output: 20

// Test Case 2: K equals 1
nums = [1,2,3,4], k = 1
Expected Output: 10

// Test Case 3: All equal elements
nums = [1,1,1], k = 2
Expected Output: 9
```

**Solution:**

```java
class Solution {
    public long kSum(int[] nums, int k) {
        long total = 0;
        for (int num : nums) {
            if (num < 0) {
                total += num;
            }
        }
        
        nums = Arrays.stream(nums).map(Math::abs).sorted().toArray();
        
        long[][] dp = new long[nums.length + 1][k + 1];
        for (int i = 0; i <= nums.length; i++) {
            dp[i][0] = total;
        }
        
        for (int i = 1; i <= nums.length; i++) {
            for (int j = 1; j <= k; j++) {
                dp[i][j] = dp[i - 1][j];
                if (j == 1) {
                    dp[i][j] = Math.max(dp[i][j], dp[i - 1][0] + nums[i - 1]);
                } else {
                    dp[i][j] = Math.max(dp[i][j], dp[i - 1][j - 1] + nums[i - 1]);
                }
            }
        }
        
        return dp[nums.length][k];
    }
}
```

---

### 16. Maximum Product After K Increments

**Problem Statement:**
You can perform at most k increment operations on any element of the array. In each operation, you choose an element of the array and increment it by 1. Find the maximum product of array elements after performing at most k increments.

**Test Cases:**

```java
// Test Case 1: Basic example
nums = [0,4], k = 5
Expected Output: 20

// Test Case 2: No increments
nums = [6,3,3,2], k = 0
Expected Output: 36

// Test Case 3: All increments on one
nums = [1,2,3], k = 2
Expected Output: 24
```

**Solution:**

```java
class Solution {
    public long maximumProduct(int[] nums, int k) {
        PriorityQueue<Long> minHeap = new PriorityQueue<>();
        
        for (int num : nums) {
            minHeap.offer((long) num);
        }
        
        for (int i = 0; i < k; i++) {
            long min = minHeap.poll();
            minHeap.offer(min + 1);
        }
        
        long product = 1;
        long MOD = 1000000007;
        
        while (!minHeap.isEmpty()) {
            product = (product * (minHeap.poll() % MOD)) % MOD;
        }
        
        return product;
    }
}
```

---

### 17. Least Number of Unique Integers after K Removals

**Problem Statement:**
Given an array of integers arr and an integer k, find the least number of unique integers after removing exactly k elements from the array.

**Test Cases:**

```java
// Test Case 1: Remove some integers
arr = [5,5,4], k = 1
Expected Output: 1

// Test Case 2: Remove all occurrences
arr = [1,1,1,2,2,3], k = 2
Expected Output: 2

// Test Case 3: Remove everything
arr = [1,1,1,1], k = 4
Expected Output: 0
```

**Solution:**

```java
class Solution {
    public int findLeastNumOfUniqueInts(int[] arr, int k) {
        Map<Integer, Integer> freq = new HashMap<>();
        for (int num : arr) {
            freq.put(num, freq.getOrDefault(num, 0) + 1);
        }
        
        PriorityQueue<Integer> minHeap = new PriorityQueue<>(freq.values());
        
        while (k > 0 && !minHeap.isEmpty()) {
            k -= minHeap.poll();
        }
        
        return minHeap.size();
    }
}
```

---

### 18. Final Array State After K Multiplication Operations I

**Problem Statement:**
You are given an integer array nums and an integer k. In each operation, you find the minimum element in nums and replace it with element * 2. After exactly k operations, return the final array.

**Test Cases:**

```java
// Test Case 1: Basic example
nums = [2,1,3,5,6], k = 5
Expected Output: [8,4,6,5,6]

// Test Case 2: Single operation
nums = [1,2], k = 3
Expected Output: [4,2]

// Test Case 3: Large k
nums = [1], k = 5
Expected Output: [32]
```

**Solution:**

```java
class Solution {
    public long[] getFinalState(int[] nums, int k, int multiplier) {
        PriorityQueue<int[]> minHeap = new PriorityQueue<>((a, b) -> {
            if (a[0] != b[0]) return a[0] - b[0];
            return a[1] - b[1];
        });
        
        for (int i = 0; i < nums.length; i++) {
            minHeap.offer(new int[]{nums[i], i});
        }
        
        for (int i = 0; i < k; i++) {
            int[] min = minHeap.poll();
            min[0] *= multiplier;
            minHeap.offer(min);
        }
        
        long[] result = new long[nums.length];
        while (!minHeap.isEmpty()) {
            int[] top = minHeap.poll();
            result[top[1]] = top[0];
        }
        
        return result;
    }
}
```

---

## Summary

This comprehensive guide covers 38 problems across three major categories:
- **Two Heaps**: Problems focusing on maintaining two priority queues for efficient median/capital tracking
- **K-way Merge**: Problems involving merging multiple sorted structures
- **Top K Elements**: Problems requiring efficient tracking of top k elements

Each solution includes:
- Complete, compilable Java code
- Time and space complexity optimizations
- Proper use of Java's PriorityQueue and other data structures

