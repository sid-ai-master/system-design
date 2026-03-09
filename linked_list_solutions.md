# LeetCode Linked List - Complete Solutions (13 Problems)

---

## Problem 1: Add Two Numbers

**Problem Statement:**
You are given two non-empty linked lists representing two non-negative integers. The digits are stored in reverse order, and each of their nodes contains a single digit. Add the two numbers and return the sum as a linked list. You may assume the two numbers do not contain any leading zero, except the number 0 itself.

**Example:**
- Input: l1 = [2,4,3], l2 = [5,6,4]
- Output: [7,0,8]
- Explanation: 342 + 465 = 807

**Category:** Linked List, Math, Simulation

**Approach:**
1. Iterate through both lists simultaneously
2. Add corresponding digits plus any carry from previous addition
3. Create new node for the sum (modulo 10)
4. Update carry for next iteration
5. Handle remaining nodes in either list
6. Don't forget the final carry if it exists

```java
class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

public class AddTwoNumbers {
    public static ListNode addTwoNumbers(ListNode l1, ListNode l2) {
        ListNode dummy = new ListNode(0);
        ListNode current = dummy;
        int carry = 0;
        
        while (l1 != null || l2 != null || carry != 0) {
            int sum = carry;
            if (l1 != null) {
                sum += l1.val;
                l1 = l1.next;
            }
            if (l2 != null) {
                sum += l2.val;
                l2 = l2.next;
            }
            carry = sum / 10;
            current.next = new ListNode(sum % 10);
            current = current.next;
        }
        
        return dummy.next;
    }
    
    // Helper to create linked list from array
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    // Helper to print linked list
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: 342 + 465 = 807
        ListNode l1_1 = createList(new int[]{2, 4, 3});
        ListNode l2_1 = createList(new int[]{5, 6, 4});
        ListNode result1 = addTwoNumbers(l1_1, l2_1);
        System.out.print("Test 1: ");
        printList(result1);
        System.out.println("Expected: 7 -> 0 -> 8");
        
        // Test case 2: 0 + 0 = 0
        ListNode l1_2 = createList(new int[]{0});
        ListNode l2_2 = createList(new int[]{0});
        ListNode result2 = addTwoNumbers(l1_2, l2_2);
        System.out.print("Test 2: ");
        printList(result2);
        System.out.println("Expected: 0");
        
        // Test case 3: 9999999 + 9999 = 10009998
        ListNode l1_3 = createList(new int[]{9, 9, 9, 9, 9, 9, 9});
        ListNode l2_3 = createList(new int[]{9, 9, 9, 9});
        ListNode result3 = addTwoNumbers(l1_3, l2_3);
        System.out.print("Test 3: ");
        printList(result3);
        System.out.println("Expected: 8 -> 9 -> 9 -> 9 -> 0 -> 0 -> 0 -> 1");
    }
}
```

**Time Complexity:** O(max(m, n)) where m and n are lengths of the two lists
**Space Complexity:** O(max(m, n)) for the result list

---

## Problem 2: Remove Nth Node From End of List

**Problem Statement:**
Given the head of a linked list, remove the nth node from the end of the list and return the head of the list.

**Example:**
- Input: head = [1,2,3,4,5], n = 2
- Output: [1,2,3,5]

**Category:** Linked List, Two Pointers

**Approach:**
1. Use two pointers (fast and slow) with n nodes gap
2. Move fast pointer n steps ahead first
3. Move both pointers until fast reaches the end
4. Remove the node by updating next pointer
5. Handle the edge case of removing the head node

```java
public class RemoveNthNode {
    public static ListNode removeNthFromEnd(ListNode head, int n) {
        // Create dummy node to handle edge case of removing head
        ListNode dummy = new ListNode(0);
        dummy.next = head;
        ListNode first = dummy;
        ListNode second = dummy;
        
        // Move first pointer n+1 steps ahead
        for (int i = 0; i <= n; i++) {
            if (first == null) return head;
            first = first.next;
        }
        
        // Move both pointers until first reaches end
        while (first != null) {
            first = first.next;
            second = second.next;
        }
        
        // Remove the nth node
        second.next = second.next.next;
        
        return dummy.next;
    }
    
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Remove 2nd node from end
        ListNode head1 = createList(new int[]{1, 2, 3, 4, 5});
        ListNode result1 = removeNthFromEnd(head1, 2);
        System.out.print("Test 1: ");
        printList(result1);
        System.out.println("Expected: 1 -> 2 -> 3 -> 5");
        
        // Test case 2: Remove head node
        ListNode head2 = createList(new int[]{1});
        ListNode result2 = removeNthFromEnd(head2, 1);
        System.out.print("Test 2: ");
        if (result2 == null) System.out.println("Empty list");
        else printList(result2);
        System.out.println("Expected: Empty list");
        
        // Test case 3: Remove from list of 2
        ListNode head3 = createList(new int[]{1, 2});
        ListNode result3 = removeNthFromEnd(head3, 2);
        System.out.print("Test 3: ");
        printList(result3);
        System.out.println("Expected: 2");
    }
}
```

**Time Complexity:** O(L) where L is the length of the list
**Space Complexity:** O(1) constant space

---

## Problem 3: Merge Two Sorted Lists

**Problem Statement:**
You are given the heads of two sorted linked lists list1 and list2. Merge the two lists in a one sorted list. The list should be made by splicing together the nodes of the two lists. Return the head of the merged linked list.

**Example:**
- Input: list1 = [1,2,4], list2 = [1,3,4]
- Output: [1,1,2,3,4,4]

**Category:** Linked List, Recursion

**Approach:**
1. Compare values of current nodes in both lists
2. Append smaller value node to result
3. Move the pointer of the list that had smaller value
4. Continue until one list is exhausted
5. Append remaining nodes from non-empty list

```java
public class MergeSortedLists {
    public static ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        ListNode dummy = new ListNode(0);
        ListNode current = dummy;
        
        while (list1 != null && list2 != null) {
            if (list1.val <= list2.val) {
                current.next = list1;
                list1 = list1.next;
            } else {
                current.next = list2;
                list2 = list2.next;
            }
            current = current.next;
        }
        
        // Append remaining nodes
        if (list1 != null) {
            current.next = list1;
        } else {
            current.next = list2;
        }
        
        return dummy.next;
    }
    
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Merge [1,2,4] and [1,3,4]
        ListNode list1_1 = createList(new int[]{1, 2, 4});
        ListNode list2_1 = createList(new int[]{1, 3, 4});
        ListNode result1 = mergeTwoLists(list1_1, list2_1);
        System.out.print("Test 1: ");
        printList(result1);
        System.out.println("Expected: 1 -> 1 -> 2 -> 3 -> 4 -> 4");
        
        // Test case 2: Merge empty and non-empty
        ListNode list1_2 = createList(new int[]{});
        ListNode list2_2 = createList(new int[]{0});
        ListNode result2 = mergeTwoLists(list1_2, list2_2);
        System.out.print("Test 2: ");
        printList(result2);
        System.out.println("Expected: 0");
        
        // Test case 3: Merge [5] and [1,2,3,4,6]
        ListNode list1_3 = createList(new int[]{5});
        ListNode list2_3 = createList(new int[]{1, 2, 3, 4, 6});
        ListNode result3 = mergeTwoLists(list1_3, list2_3);
        System.out.print("Test 3: ");
        printList(result3);
        System.out.println("Expected: 1 -> 2 -> 3 -> 4 -> 5 -> 6");
    }
}
```

**Time Complexity:** O(m + n) where m and n are lengths of the two lists
**Space Complexity:** O(1) constant space

---

## Problem 4: Swap Nodes in Pairs

**Problem Statement:**
Given a linked list, swap every two adjacent nodes and return its head. You must solve the problem without modifying the values in the list's nodes (i.e., only nodes themselves may be changed).

**Example:**
- Input: head = [1,2,3,4]
- Output: [2,1,4,3]

**Category:** Linked List, Recursion

**Approach:**
1. Use pairs of pointers to identify consecutive nodes
2. Swap each pair by rearranging pointers
3. Move to the next pair
4. Handle odd length lists gracefully

```java
public class SwapNodesInPairs {
    public static ListNode swapPairs(ListNode head) {
        ListNode dummy = new ListNode(0);
        dummy.next = head;
        ListNode prev = dummy;
        
        while (prev.next != null && prev.next.next != null) {
            // Identify the two nodes
            ListNode first = prev.next;
            ListNode second = prev.next.next;
            
            // Swap
            prev.next = second;
            first.next = second.next;
            second.next = first;
            
            // Move to next pair
            prev = first;
        }
        
        return dummy.next;
    }
    
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Swap pairs in [1,2,3,4]
        ListNode head1 = createList(new int[]{1, 2, 3, 4});
        ListNode result1 = swapPairs(head1);
        System.out.print("Test 1: ");
        printList(result1);
        System.out.println("Expected: 2 -> 1 -> 4 -> 3");
        
        // Test case 2: Single node
        ListNode head2 = createList(new int[]{1});
        ListNode result2 = swapPairs(head2);
        System.out.print("Test 2: ");
        printList(result2);
        System.out.println("Expected: 1");
        
        // Test case 3: Odd length list
        ListNode head3 = createList(new int[]{1, 2, 3});
        ListNode result3 = swapPairs(head3);
        System.out.print("Test 3: ");
        printList(result3);
        System.out.println("Expected: 2 -> 1 -> 3");
    }
}
```

**Time Complexity:** O(n) where n is the number of nodes
**Space Complexity:** O(1) constant space

---

## Problem 5: Reverse Nodes in k-Group

**Problem Statement:**
Given the head of a linked list, reverse the nodes of the list k at a time, and return the modified list. If the number of nodes is not a multiple of k then left-out nodes, in the end, should remain as is. You may not alter the values in the list's nodes, only nodes themselves may be changed.

**Example:**
- Input: head = [1,2,3,4,5], k = 2
- Output: [2,1,4,3,5]

**Category:** Linked List, Recursion

**Approach:**
1. Check if there are at least k nodes remaining
2. Reverse the first k nodes
3. Recursively reverse the remaining nodes
4. Connect the reversed groups
5. Handle the case where fewer than k nodes remain

```java
public class ReverseNodesInKGroup {
    public static ListNode reverseKGroup(ListNode head, int k) {
        // Check if there are at least k nodes
        ListNode current = head;
        for (int i = 0; i < k; i++) {
            if (current == null) return head;
            current = current.next;
        }
        
        // Reverse the first k nodes
        ListNode prev = null;
        current = head;
        for (int i = 0; i < k; i++) {
            ListNode next = current.next;
            current.next = prev;
            prev = current;
            current = next;
        }
        
        // Recursively reverse the rest
        head.next = reverseKGroup(current, k);
        
        return prev;
    }
    
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Reverse groups of 2
        ListNode head1 = createList(new int[]{1, 2, 3, 4, 5});
        ListNode result1 = reverseKGroup(head1, 2);
        System.out.print("Test 1: ");
        printList(result1);
        System.out.println("Expected: 2 -> 1 -> 4 -> 3 -> 5");
        
        // Test case 2: Reverse groups of 3
        ListNode head2 = createList(new int[]{1, 2, 3, 4, 5});
        ListNode result2 = reverseKGroup(head2, 3);
        System.out.print("Test 2: ");
        printList(result2);
        System.out.println("Expected: 3 -> 2 -> 1 -> 4 -> 5");
        
        // Test case 3: k equals list length
        ListNode head3 = createList(new int[]{1, 2, 3});
        ListNode result3 = reverseKGroup(head3, 3);
        System.out.print("Test 3: ");
        printList(result3);
        System.out.println("Expected: 3 -> 2 -> 1");
    }
}
```

**Time Complexity:** O(n) where n is the number of nodes
**Space Complexity:** O(n/k) for recursion stack

---

## Problem 6: Reverse Linked List II

**Problem Statement:**
Given the head of a singly linked list and two integers left and right where left <= right, reverse the nodes of the list from position left to position right, and return the reversed list.

**Example:**
- Input: head = [1,2,3,4,5], left = 2, right = 4
- Output: [1,4,3,2,5]

**Category:** Linked List, Two Pointers

**Approach:**
1. Find the node before the left position
2. Reverse nodes between left and right positions
3. Connect the reversed segment with the rest of the list
4. Handle edge cases like reversing from head

```java
public class ReverseLinkedListII {
    public static ListNode reverseBetween(ListNode head, int left, int right) {
        if (head == null || head.next == null || left == right) return head;
        
        ListNode dummy = new ListNode(0);
        dummy.next = head;
        ListNode prev = dummy;
        
        // Move to the node before left position
        for (int i = 0; i < left - 1; i++) {
            prev = prev.next;
        }
        
        // Start reversing
        ListNode current = prev.next;
        for (int i = 0; i < right - left; i++) {
            ListNode next = current.next;
            current.next = next.next;
            next.next = prev.next;
            prev.next = next;
        }
        
        return dummy.next;
    }
    
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Reverse from position 2 to 4
        ListNode head1 = createList(new int[]{1, 2, 3, 4, 5});
        ListNode result1 = reverseBetween(head1, 2, 4);
        System.out.print("Test 1: ");
        printList(result1);
        System.out.println("Expected: 1 -> 4 -> 3 -> 2 -> 5");
        
        // Test case 2: Reverse single element
        ListNode head2 = createList(new int[]{3, 5});
        ListNode result2 = reverseBetween(head2, 1, 1);
        System.out.print("Test 2: ");
        printList(result2);
        System.out.println("Expected: 3 -> 5");
        
        // Test case 3: Reverse entire list
        ListNode head3 = createList(new int[]{1, 2, 3});
        ListNode result3 = reverseBetween(head3, 1, 3);
        System.out.print("Test 3: ");
        printList(result3);
        System.out.println("Expected: 3 -> 2 -> 1");
    }
}
```

**Time Complexity:** O(n) where n is the number of nodes
**Space Complexity:** O(1) constant space

---

## Problem 7: Remove Linked List Elements

**Problem Statement:**
Given the head of a linked list and an integer val, remove all the nodes of the linked list that has Node.val == val, and return the new head.

**Example:**
- Input: head = [1,2,6,3,4,5,6], val = 6
- Output: [1,2,3,4,5]

**Category:** Linked List

**Approach:**
1. Use a dummy node to handle removal of head
2. Traverse the list
3. Skip nodes with value equal to val
4. Connect previous node to next valid node
5. Return dummy.next as the new head

```java
public class RemoveLinkedListElements {
    public static ListNode removeElements(ListNode head, int val) {
        ListNode dummy = new ListNode(0);
        dummy.next = head;
        ListNode current = dummy;
        
        while (current.next != null) {
            if (current.next.val == val) {
                current.next = current.next.next;
            } else {
                current = current.next;
            }
        }
        
        return dummy.next;
    }
    
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Remove 6 from [1,2,6,3,4,5,6]
        ListNode head1 = createList(new int[]{1, 2, 6, 3, 4, 5, 6});
        ListNode result1 = removeElements(head1, 6);
        System.out.print("Test 1: ");
        printList(result1);
        System.out.println("Expected: 1 -> 2 -> 3 -> 4 -> 5");
        
        // Test case 2: Remove head elements
        ListNode head2 = createList(new int[]{7, 7, 7, 7});
        ListNode result2 = removeElements(head2, 7);
        System.out.print("Test 2: ");
        if (result2 == null) System.out.println("Empty list");
        else printList(result2);
        System.out.println("Expected: Empty list");
        
        // Test case 3: No elements to remove
        ListNode head3 = createList(new int[]{1, 2, 3});
        ListNode result3 = removeElements(head3, 4);
        System.out.print("Test 3: ");
        printList(result3);
        System.out.println("Expected: 1 -> 2 -> 3");
    }
}
```

**Time Complexity:** O(n) where n is the number of nodes
**Space Complexity:** O(1) constant space

---

## Problem 8: Reverse Linked List

**Problem Statement:**
Given the head of a singly linked list, reverse the list, and return the reversed list.

**Example:**
- Input: head = [1,2,3,4,5]
- Output: [5,4,3,2,1]

**Category:** Linked List, Recursion

**Approach (Iterative):**
1. Use three pointers: prev, current, next
2. Traverse through the list
3. For each node, reverse its next pointer to point to previous node
4. Move prev and current forward
5. Continue until current becomes null

```java
public class ReverseLinkedList {
    // Iterative approach
    public static ListNode reverseListIterative(ListNode head) {
        ListNode prev = null;
        ListNode current = head;
        
        while (current != null) {
            ListNode next = current.next;
            current.next = prev;
            prev = current;
            current = next;
        }
        
        return prev;
    }
    
    // Recursive approach
    public static ListNode reverseListRecursive(ListNode head) {
        if (head == null || head.next == null) return head;
        
        ListNode newHead = reverseListRecursive(head.next);
        head.next.next = head;
        head.next = null;
        
        return newHead;
    }
    
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Reverse [1,2,3,4,5]
        ListNode head1 = createList(new int[]{1, 2, 3, 4, 5});
        ListNode result1 = reverseListIterative(head1);
        System.out.print("Test 1 (Iterative): ");
        printList(result1);
        System.out.println("Expected: 5 -> 4 -> 3 -> 2 -> 1");
        
        // Test case 2: Reverse single node
        ListNode head2 = createList(new int[]{1});
        ListNode result2 = reverseListRecursive(head2);
        System.out.print("Test 2 (Recursive): ");
        printList(result2);
        System.out.println("Expected: 1");
        
        // Test case 3: Reverse two nodes
        ListNode head3 = createList(new int[]{1, 2});
        ListNode result3 = reverseListIterative(head3);
        System.out.print("Test 3 (Iterative): ");
        printList(result3);
        System.out.println("Expected: 2 -> 1");
    }
}
```

**Time Complexity:** O(n) where n is the number of nodes
**Space Complexity:** O(1) for iterative, O(n) for recursive (recursion stack)

---

## Problem 9: Palindrome Linked List

**Problem Statement:**
Given the head of a singly linked list, return true if it is a palindrome or false otherwise.

**Example:**
- Input: head = [1,2,2,1]
- Output: true

**Category:** Linked List, Two Pointers, Stack

**Approach:**
1. Find the middle of the linked list using slow and fast pointers
2. Reverse the second half of the list
3. Compare the first half with the reversed second half
4. Return true if all nodes match

```java
public class PalindromeLinkedList {
    public static boolean isPalindrome(ListNode head) {
        if (head == null || head.next == null) return true;
        
        // Find middle
        ListNode slow = head;
        ListNode fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        
        // Reverse second half
        ListNode reverseHead = reverseList(slow);
        
        // Compare first and second half
        ListNode p1 = head;
        ListNode p2 = reverseHead;
        while (p2 != null) {
            if (p1.val != p2.val) return false;
            p1 = p1.next;
            p2 = p2.next;
        }
        
        return true;
    }
    
    private static ListNode reverseList(ListNode head) {
        ListNode prev = null;
        ListNode current = head;
        while (current != null) {
            ListNode next = current.next;
            current.next = prev;
            prev = current;
            current = next;
        }
        return prev;
    }
    
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Palindrome [1,2,2,1]
        ListNode head1 = createList(new int[]{1, 2, 2, 1});
        boolean result1 = isPalindrome(head1);
        System.out.println("Test 1: [1,2,2,1] is palindrome? " + result1);
        System.out.println("Expected: true");
        
        // Test case 2: Not palindrome [1,2]
        ListNode head2 = createList(new int[]{1, 2});
        boolean result2 = isPalindrome(head2);
        System.out.println("Test 2: [1,2] is palindrome? " + result2);
        System.out.println("Expected: false");
        
        // Test case 3: Palindrome [9,9,9,9]
        ListNode head3 = createList(new int[]{9, 9, 9, 9});
        boolean result3 = isPalindrome(head3);
        System.out.println("Test 3: [9,9,9,9] is palindrome? " + result3);
        System.out.println("Expected: true");
    }
}
```

**Time Complexity:** O(n) where n is the number of nodes
**Space Complexity:** O(1) if not counting the output

---

## Problem 10: Delete Node in a Linked List

**Problem Statement:**
There is a singly-linked list head, and we want to delete a node node in it. You are given the node to be deleted node. You will not be given access to the first node head. All the values of the linked list are unique, and it is guaranteed that the given node node is not the last node in the linked list.

**Example:**
- Input: head = [4,5,1,9], node = 5
- Output: [4,1,9]

**Category:** Linked List

**Approach:**
1. Cannot delete the node directly since we don't have access to previous node
2. Copy value from next node to current node
3. Delete the next node by skipping it
4. This effectively removes the target node

```java
public class DeleteNodeLinkedList {
    public static void deleteNode(ListNode node) {
        // Copy next node's value to current node
        node.val = node.next.val;
        // Delete next node
        node.next = node.next.next;
    }
    
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Delete node with value 5
        ListNode head1 = createList(new int[]{4, 5, 1, 9});
        ListNode nodeToDelete1 = head1.next; // Node with value 5
        deleteNode(nodeToDelete1);
        System.out.print("Test 1: ");
        printList(head1);
        System.out.println("Expected: 4 -> 1 -> 9");
        
        // Test case 2: Delete middle node
        ListNode head2 = createList(new int[]{1, 2, 3});
        ListNode nodeToDelete2 = head2.next; // Node with value 2
        deleteNode(nodeToDelete2);
        System.out.print("Test 2: ");
        printList(head2);
        System.out.println("Expected: 1 -> 3");
        
        // Test case 3: Delete first middle element
        ListNode head3 = createList(new int[]{1, 0, 1});
        ListNode nodeToDelete3 = head3.next; // Node with value 0
        deleteNode(nodeToDelete3);
        System.out.print("Test 3: ");
        printList(head3);
        System.out.println("Expected: 1 -> 1");
    }
}
```

**Time Complexity:** O(1) constant time
**Space Complexity:** O(1) constant space

---

## Problem 11: Odd Even Linked List

**Problem Statement:**
Given the head of a singly linked list, group all nodes with odd indices together followed by the nodes with even indices, and return the new head. Here, the index of the first node is considered odd, and the index of the second node is even, and so on.

**Example:**
- Input: head = [1,2,3,4,5]
- Output: [1,3,5,2,4]

**Category:** Linked List, Two Pointers

**Approach:**
1. Create two separate lists: one for odd indices, one for even indices
2. Traverse and distribute nodes alternately
3. Connect the odd list to the even list
4. Return the head of odd list

```java
public class OddEvenLinkedList {
    public static ListNode oddEvenList(ListNode head) {
        if (head == null || head.next == null) return head;
        
        ListNode odd = head;
        ListNode even = head.next;
        ListNode evenHead = even;
        
        while (even != null && even.next != null) {
            odd.next = even.next;
            odd = odd.next;
            even.next = odd.next;
            even = even.next;
        }
        
        odd.next = evenHead;
        return head;
    }
    
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Odd-even grouping [1,2,3,4,5]
        ListNode head1 = createList(new int[]{1, 2, 3, 4, 5});
        ListNode result1 = oddEvenList(head1);
        System.out.print("Test 1: ");
        printList(result1);
        System.out.println("Expected: 1 -> 3 -> 5 -> 2 -> 4");
        
        // Test case 2: Two nodes
        ListNode head2 = createList(new int[]{2, 1});
        ListNode result2 = oddEvenList(head2);
        System.out.print("Test 2: ");
        printList(result2);
        System.out.println("Expected: 2 -> 1");
        
        // Test case 3: Longer list [1,2,3,4,5,6,7,8,9]
        ListNode head3 = createList(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9});
        ListNode result3 = oddEvenList(head3);
        System.out.print("Test 3: ");
        printList(result3);
        System.out.println("Expected: 1 -> 3 -> 5 -> 7 -> 9 -> 2 -> 4 -> 6 -> 8");
    }
}
```

**Time Complexity:** O(n) where n is the number of nodes
**Space Complexity:** O(1) constant space

---

## Problem 12: Flatten a Multilevel Doubly Linked List

**Problem Statement:**
You are given a doubly linked list which in addition to the next and previous pointers has an additional child pointer, which may or may not point to a separate doubly linked list. These child lists may have one or more children of their own, and so on, to produce a multilevel data structure, as shown in the example below. Flatten the list so that all the nodes appear in a single-level, doubly linked list. You are given the head of the first level of the list.

**Example:**
- Input: head = [1,2,3,4,5,6,null,null,null,7,8,9,10,null,null,11,12]
- Output: [1,2,3,7,8,11,12,9,10,4,5,6]

**Category:** Linked List, DFS, Recursion

**Approach:**
1. DFS traverse through the multilevel structure
2. When encountering a child, recursively flatten it
3. Connect the flattened child list to the current position
4. Maintain continuity through next and prev pointers

```java
public class MultiLevelDoublyLinkedList {
    static class DoublyListNode {
        int val;
        DoublyListNode next;
        DoublyListNode prev;
        DoublyListNode child;
        DoublyListNode() {}
        DoublyListNode(int val) { this.val = val; }
    }
    
    public static DoublyListNode flatten(DoublyListNode head) {
        if (head == null) return null;
        flattenHelper(head);
        return head;
    }
    
    private static DoublyListNode flattenHelper(DoublyListNode head) {
        DoublyListNode current = head;
        DoublyListNode last = null;
        
        while (current != null) {
            DoublyListNode next = current.next;
            
            if (current.child != null) {
                DoublyListNode childLast = flattenHelper(current.child);
                
                // Connect child to current
                current.next = current.child;
                current.child.prev = current;
                
                // Connect child list end to next
                childLast.next = next;
                if (next != null) {
                    next.prev = childLast;
                }
                
                current.child = null;
                last = childLast;
            } else {
                last = current;
            }
            
            current = next;
        }
        
        return last;
    }
    
    static void printList(DoublyListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Simple multilevel list
        DoublyListNode head1 = new DoublyListNode(1);
        head1.next = new DoublyListNode(2);
        head1.next.prev = head1;
        head1.next.child = new DoublyListNode(3);
        head1.next.child.next = new DoublyListNode(4);
        head1.next.child.next.prev = head1.next.child;
        
        DoublyListNode result1 = flatten(head1);
        System.out.print("Test 1: ");
        printList(result1);
        System.out.println("Expected: 1 -> 2 -> 3 -> 4");
        
        // Test case 2: No children
        DoublyListNode head2 = new DoublyListNode(1);
        head2.next = new DoublyListNode(2);
        head2.next.prev = head2;
        
        DoublyListNode result2 = flatten(head2);
        System.out.print("Test 2: ");
        printList(result2);
        System.out.println("Expected: 1 -> 2");
        
        // Test case 3: Single node with child
        DoublyListNode head3 = new DoublyListNode(1);
        head3.child = new DoublyListNode(2);
        
        DoublyListNode result3 = flatten(head3);
        System.out.print("Test 3: ");
        printList(result3);
        System.out.println("Expected: 1 -> 2");
    }
}
```

**Time Complexity:** O(n) where n is the total number of nodes
**Space Complexity:** O(h) where h is the maximum depth of nesting (recursion stack)

---

## Problem 13: Merge In Between Linked Lists

**Problem Statement:**
Given two linked lists and the values a and b, merge the second list between the nodes of the first list where the node values equal a and b. Return the first list after the merge.

**Example:**
- Input: list1 = [10,1,13,6,9,5], a = 6, b = 2, list2 = [1000000,1000001,1000002]
- Output: [10,1,13,1000000,1000001,1000002,5]

**Category:** Linked List, Two Pointers

**Approach:**
1. Find the node in list1 with value equal to a
2. Find the last node with value equal to b in list1
3. Extract the list2 head
4. Find the last node of list2
5. Merge list2 between nodes a and b
6. Skip all nodes between a and b

```java
public class MergeInBetweenLinkedLists {
    public static ListNode mergeInBetween(ListNode list1, int a, int b, ListNode list2) {
        // Find the node at position a-1
        ListNode prev = list1;
        for (int i = 0; i < a - 1; i++) {
            prev = prev.next;
        }
        
        // Find the node at position b
        ListNode curr = prev;
        for (int i = 0; i < b - a + 1; i++) {
            curr = curr.next;
        }
        
        // Find the last node of list2
        ListNode list2End = list2;
        while (list2End.next != null) {
            list2End = list2End.next;
        }
        
        // Merge
        list2End.next = curr;
        prev.next = list2;
        
        return list1;
    }
    
    static ListNode createList(int[] arr) {
        if (arr.length == 0) return null;
        ListNode head = new ListNode(arr[0]);
        ListNode current = head;
        for (int i = 1; i < arr.length; i++) {
            current.next = new ListNode(arr[i]);
            current = current.next;
        }
        return head;
    }
    
    static void printList(ListNode head) {
        while (head != null) {
            System.out.print(head.val);
            if (head.next != null) System.out.print(" -> ");
            head = head.next;
        }
        System.out.println();
    }
    
    public static void main(String[] args) {
        // Test case 1: Merge lists
        ListNode list1_1 = createList(new int[]{10, 1, 13, 6, 9, 5});
        ListNode list2_1 = createList(new int[]{1000000, 1000001, 1000002});
        ListNode result1 = mergeInBetween(list1_1, 6, 2, list2_1);
        System.out.print("Test 1: ");
        printList(result1);
        System.out.println("Expected: 10 -> 1 -> 13 -> 1000000 -> 1000001 -> 1000002 -> 5");
        
        // Test case 2: Merge at position 1 and 2
        ListNode list1_2 = createList(new int[]{0, 1, 1, 1, 1});
        ListNode list2_2 = createList(new int[]{1, 0, 1});
        ListNode result2 = mergeInBetween(list1_2, 1, 2, list2_2);
        System.out.print("Test 2: ");
        printList(result2);
        System.out.println("Expected: 0 -> 1 -> 0 -> 1 -> 1");
        
        // Test case 3: Merge single nodes
        ListNode list1_3 = createList(new int[]{1, 2, 3});
        ListNode list2_3 = createList(new int[]{10});
        ListNode result3 = mergeInBetween(list1_3, 2, 2, list2_3);
        System.out.print("Test 3: ");
        printList(result3);
        System.out.println("Expected: 1 -> 2 -> 10 -> 3");
    }
}
```

**Time Complexity:** O(m + n) where m is the length of list1 and n is the length of list2
**Space Complexity:** O(1) constant space

---

# Summary of All 13 Linked List Problems

| # | Problem | Difficulty | Key Technique |
|---|---------|------------|---|
| 1 | Add Two Numbers | Medium | Simulation, Carry |
| 2 | Remove Nth Node From End | Medium | Two Pointers, Dummy Node |
| 3 | Merge Two Sorted Lists | Easy | Iteration, Merging |
| 4 | Swap Nodes in Pairs | Medium | Iteration, Swapping |
| 5 | Reverse Nodes in k-Group | Hard | Recursion, Reversal |
| 6 | Reverse Linked List II | Medium | Two Pointers, Routing |
| 7 | Remove Linked List Elements | Easy | Iteration, Dummy Node |
| 8 | Reverse Linked List | Easy | Iteration/Recursion |
| 9 | Palindrome Linked List | Easy | Two Pointers, Reversal |
| 10 | Delete Node in Linked List | Easy | Value Copying |
| 11 | Odd Even Linked List | Medium | Two Pointers |
| 12 | Flatten Multilevel List | Medium | DFS, Recursion |
| 13 | Merge In Between Lists | Medium | Pointer Routing |

---

## Common Patterns and Techniques

**Two Pointers:**
- Slow and fast pointers for finding middle
- Lead pointer to create gap
- Used in reversing, merging, and finding cycles

**Dummy Node:**
- Handles edge cases (removing head, etc.)
- Simplifies code logic
- Initialization: `new ListNode(0)` or similar

**Recursion:**
- Used for reversal operations
- DFS for multilevel structures
- Can simplify complex pointer operations

**Stack Usage:**
- For palindrome checking
- For reversing without modifying pointers

**Key Patterns:**
- Always preserve node connections
- Handle null checks carefully
- Consider edge cases (single node, empty list, etc.)
- Use dummy nodes to avoid special-casing head
