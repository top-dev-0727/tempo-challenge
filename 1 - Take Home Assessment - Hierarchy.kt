import kotlin.test.*

// The task:
// 1. Read and understand the Hierarchy data structure described in this file.
// 2. Implement filter() function.
// 3. Implement more test cases.
//
// The task should take 30-90 minutes.
//
// When assessing the submission, we will pay attention to:
// - correctness, efficiency, and clarity of the code;
// - the test cases.

/**
 * A `Hierarchy` stores an arbitrary _forest_ (an ordered collection of ordered trees)
 * as an array of node IDs in the order of DFS traversal, combined with a parallel array of node depths.
 *
 * Parent-child relationships are identified by the position in the array and the associated depth.
 * Each tree root has depth 0, its children have depth 1 and follow it in the array, their children have depth 2 and follow them, etc.
 *
 * Example:
 * ```
 * nodeIds: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
 * depths:  0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2
 * ```
 *
 * the forest can be visualized as follows:
 * ```
 * 1
 * - 2
 * - - 3
 * - - - 4
 * - 5
 * 6
 * - 7
 * 8
 * - 9
 * - 10
 * - - 11
 *```
 * 1 is a parent of 2 and 5, 2 is a parent of 3, etc. Note that depth is equal to the number of hyphens for each node.
 *
 * Invariants on the depths array:
 *  * Depth of the first element is 0.
 *  * If the depth of a node is `D`, the depth of the next node in the array can be:
 *      * `D + 1` if the next node is a child of this node;
 *      * `D` if the next node is a sibling of this node;
 *      * `d < D` - in this case the next node is not related to this node.
 */
interface Hierarchy {
  /** The number of nodes in the hierarchy. */
  val size: Int

  /**
   * Returns the unique ID of the node identified by the hierarchy index. The depth for this node will be `depth(index)`.
   * @param index must be non-negative and less than [size]
   * */
  fun nodeId(index: Int): Int

  /**
   * Returns the depth of the node identified by the hierarchy index. The unique ID for this node will be `nodeId(index)`.
   * @param index must be non-negative and less than [size]
   * */
  fun depth(index: Int): Int

  fun formatString(): String {
    return (0 until size).joinToString(
      separator = ", ",
      prefix = "[",
      postfix = "]"
    ) { i -> "${nodeId(i)}:${depth(i)}" }
  }
}

/**
 * A node is present in the filtered hierarchy iff its node ID passes the predicate and all of its ancestors pass it as well.
 */
fun Hierarchy.filter(nodeIdPredicate: (Int) -> Boolean): Hierarchy {
  val filteredNodeIds = mutableListOf<Int>()
  val filteredDepths = mutableListOf<Int>()
  
  // Track which ancestors are included (by their depth)
  val includedAncestors = mutableSetOf<Int>()
  
  for (index in 0 until size) {
    val nodeId = nodeId(index)
    val depth = depth(index)
    
    // Remove ancestors at depth >= current depth (they're no longer ancestors)
    includedAncestors.removeAll { it >= depth }
    
    // Check if node passes predicate and has all ancestors included
    if (nodeIdPredicate(nodeId) && (depth == 0 || includedAncestors.contains(depth - 1))) {
      filteredNodeIds.add(nodeId)
      filteredDepths.add(depth)
      includedAncestors.add(depth)
    }
  }
  
  return ArrayBasedHierarchy(filteredNodeIds.toIntArray(), filteredDepths.toIntArray())
}

class ArrayBasedHierarchy(
  private val myNodeIds: IntArray,
  private val myDepths: IntArray,
) : Hierarchy {
  override val size: Int = myDepths.size

  override fun nodeId(index: Int): Int = myNodeIds[index]

  override fun depth(index: Int): Int = myDepths[index]
}

class FilterTest {
  @Test
  fun testFilter() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
      intArrayOf(0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2))
    val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId % 3 != 0 }
    val filteredExpected: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 5, 8, 10, 11),
      intArrayOf(0, 1, 1, 0, 1, 2))
    assertEquals(filteredExpected.formatString(), filteredActual.formatString())
  }

  @Test
  fun testFilterAllPass() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3),
      intArrayOf(0, 1, 1))
    val filteredActual: Hierarchy = unfiltered.filter { true }
    assertEquals(unfiltered.formatString(), filteredActual.formatString())
  }

  @Test
  fun testFilterNonePass() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3),
      intArrayOf(0, 1, 1))
    val filteredActual: Hierarchy = unfiltered.filter { false }
    val filteredExpected: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(),
      intArrayOf())
    assertEquals(filteredExpected.formatString(), filteredActual.formatString())
  }

  @Test
  fun testFilterRootFails() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5),
      intArrayOf(0, 1, 2, 1, 0))
    val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId != 1 }
    // Node 1 fails, so all its descendants should be excluded
    val filteredExpected: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(5),
      intArrayOf(0))
    assertEquals(filteredExpected.formatString(), filteredActual.formatString())
  }

  @Test
  fun testFilterParentFails() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4),
      intArrayOf(0, 1, 2, 1))
    val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId != 2 }
    // Node 2 fails, so all its descendants (3) should be excluded, but 4 (sibling of 3) should pass
    val filteredExpected: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 4),
      intArrayOf(0, 1))
    assertEquals(filteredExpected.formatString(), filteredActual.formatString())
  }

  @Test
  fun testFilterSingleNode() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(42),
      intArrayOf(0))
    val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId == 42 }
    assertEquals(unfiltered.formatString(), filteredActual.formatString())
  }

  @Test
  fun testFilterDeepHierarchy() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5),
      intArrayOf(0, 1, 2, 3, 4))
    // Linear chain: 1 -> 2 -> 3 -> 4 -> 5
    val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId % 2 == 1 } // Keep odd numbers
    val filteredExpected: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 3, 5),
      intArrayOf(0, 2, 4))
    assertEquals(filteredExpected.formatString(), filteredActual.formatString())
  }
}