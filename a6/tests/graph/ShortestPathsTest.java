package graph;

import java.util.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static graph.SimpleGraph.*;

public class ShortestPathsTest {

    /*
     * Text graph format ([weight] is optional):
     * Directed edge: startLabel -> endLabel [weight]
     * Undirected edge: startLabel -- endLabel [weight]
     */

    // Example graph from Prof. Myers's notes
    public static final String graph1 = """
        A -> B 9
        A -> C 14
        A -> D 15
        B -> E 23
        C -> E 17
        C -> D 5
        C -> F 30
        D -> F 20
        D -> G 37
        E -> F 3
        E -> G 20
        F -> G 16""";

    // Bidirectional graph that will encounter a priority reduction that will reorder the frontier
    public static final String graph2 = """
        A -- D 5
        D -- E 1
        B -- C 1
        A -- C 6
        C -- E 1
        A -- B 1
        A -- E 4""";

    // Graph exercise from OpenDSA
    public static final String graph3 = """
        A -- C 4
        A -- D 3
        A -- E 5
        B -- E 8
        C -- E 2
        C -- G 8
        D -- F 8
        E -- G 1
        E -- F 2
        F -- G 4""";


    @Test
    void testMyersExample() {
        SimpleGraph g = SimpleGraph.fromText(graph1);
        Weigher<SimpleEdge> w = new SimpleWeigher();
        ShortestPaths<SimpleVertex, SimpleEdge> pathfinder = new ShortestPaths<>(g, w);

        // Find all shortest paths from "A"
        SimpleVertex start = g.getVertexByLabel("A");
        PathfindingSnapshot paths = pathfinder.findAllPaths(start.id());
        assertTrue(pathfinder.allPathsFound());

        // Check distance of shortest path to "G"
        SimpleVertex end = g.getVertexByLabel("G");
        assertEquals(50, paths.distanceTo(end.id()));

        // All nodes are reachable from "A", so check that they are all discovered and settled.
        assertEquals(g.vertexCount(), pathfinder.settledCount());
        for (int id = 0; id < g.vertexCount(); ++id) {
            assertTrue(paths.discovered(id));
            assertTrue(paths.settled(id));
        }

        // Check the (unique) shortest path from A to G
        List<Integer> path = paths.pathTo(end.id());
        // This is CS 3110-style code to convert a list of vertex IDs to an array of labels
        String[] pathLabels = path.stream()
                .map(id -> g.getVertex(id).label())
                .toArray(String[]::new);
        assertArrayEquals(new String[]{"A", "C", "E", "F", "G"}, pathLabels);
    }

    @Test
    void testOpenDsaExample() {
        SimpleGraph g = SimpleGraph.fromText(graph3);
        Weigher<SimpleEdge> w = new SimpleWeigher();
        ShortestPaths<SimpleVertex, SimpleEdge> pathfinder = new ShortestPaths<>(g, w);

        // Find all shortest paths from "A"
        SimpleVertex start = g.getVertexByLabel("A");
        PathfindingSnapshot paths = pathfinder.findAllPaths(start.id());
        assertTrue(pathfinder.allPathsFound());

        // Check distance of shortest path to eash destination
        SimpleVertex vb = g.getVertexByLabel("B");
        assertEquals(13, paths.distanceTo(vb.id()));

        SimpleVertex vc = g.getVertexByLabel("C");
        assertEquals(4, paths.distanceTo(vc.id()));

        SimpleVertex vd = g.getVertexByLabel("D");
        assertEquals(3, paths.distanceTo(vd.id()));

        SimpleVertex ve = g.getVertexByLabel("E");
        assertEquals(5, paths.distanceTo(ve.id()));

        SimpleVertex vf = g.getVertexByLabel("F");
        assertEquals(7, paths.distanceTo(vf.id()));

        SimpleVertex vg = g.getVertexByLabel("G");
        assertEquals(6, paths.distanceTo(vg.id()));

        // All nodes are reachable from "A", so check that they are all discovered and settled.
        assertEquals(g.vertexCount(), pathfinder.settledCount());
        for (int id = 0; id < g.vertexCount(); ++id) {
            assertTrue(paths.discovered(id));
            assertTrue(paths.settled(id));
        }

        // Check the (unique) shortest path from A to F
        List<Integer> path = paths.pathTo(vf.id());
        // This is CS 3110-style code to convert a list of vertex IDs to an array of labels
        String[] pathLabels = path.stream()
                .map(id -> g.getVertex(id).label())
                .toArray(String[]::new);
        assertArrayEquals(new String[]{"A", "E", "F"}, pathLabels);

        // Check the (unique) shortest path from A to itself
        List<Integer> pathA = paths.pathTo(start.id());
        // This is CS 3110-style code to convert a list of vertex IDs to an array of labels
        String[] pathALabels = pathA.stream()
                .map(id -> g.getVertex(id).label())
                .toArray(String[]::new);
        assertArrayEquals(new String[]{"A"}, pathALabels);
    }

    @DisplayName("WHEN a change in priority changes the next node in the frontier")
    @Test
    void testPriorityInversion() {
        SimpleGraph g = SimpleGraph.fromText(graph2);
        Weigher<SimpleEdge> w = new SimpleWeigher();
        ShortestPaths<SimpleVertex, SimpleEdge> pathfinder = new ShortestPaths<>(g, w);

        // Find all shortest paths from "A"
        SimpleVertex start = g.getVertexByLabel("A");
        PathfindingSnapshot paths = pathfinder.findAllPaths(start.id());
        assertTrue(pathfinder.allPathsFound());

        // Check distance of shortest path to "G"
        SimpleVertex end = g.getVertexByLabel("D");
        assertEquals(4, paths.distanceTo(end.id()));

        // This graph is connected, so check that all vertices are discovered and settled
        assertEquals(g.vertexCount(), pathfinder.settledCount());
        for (int id = 0; id < g.vertexCount(); ++id) {
            assertTrue(paths.discovered(id));
            assertTrue(paths.settled(id));
        }

        // Check the (unique) shortest path from A to D
        List<Integer> path = paths.pathTo(end.id());
        // This is CS 3110-style code to convert a list of vertex IDs to an array of labels
        String[] pathLabels = path.stream()
                .map(id -> g.getVertex(id).label())
                .toArray(String[]::new);
        assertArrayEquals(new String[]{"A", "B", "C", "E", "D"}, pathLabels);
    }

    @DisplayName("WHEN the graph is disconnected")
    @Test
    void testDisconnected() {
        SimpleGraph g = new SimpleGraph();
        SimpleVertex a = g.addVertex("A");
        SimpleVertex b = g.addVertex("B");
        Weigher<SimpleEdge> w = new SimpleWeigher();
        ShortestPaths<SimpleVertex, SimpleEdge> pathfinder = new ShortestPaths<>(g, w);

        // Finding paths in a disconnected graph should work fine (no exceptions, no infinite loops)
        PathfindingSnapshot paths = pathfinder.findAllPaths(a.id());
        assertTrue(pathfinder.allPathsFound());

        // The distance from the start to itself is 0
        assertEquals(0, paths.distanceTo(a.id()));

        // Unreachable nodes should be neither discovered nor reachable
        assertEquals(1, pathfinder.settledCount());
        assertFalse(paths.discovered(b.id()));
        assertFalse(paths.settled(b.id()));
    }

    @DisplayName("Extending a search should not settle more than the specified number of vertices")
    @Test
    void testExtendSearch() {
        SimpleGraph g = SimpleGraph.fromText(graph1);
        Weigher<SimpleEdge> w = new SimpleWeigher();
        ShortestPaths<SimpleVertex, SimpleEdge> pathfinder = new ShortestPaths<>(g, w);

        // Start finding shortest paths from "A"
        SimpleVertex start = g.getVertexByLabel("A");
        pathfinder.setStart(start.id());
        int maxToSettle = 3;
        PathfindingSnapshot snapshot = pathfinder.extendSearch(maxToSettle);
        assertEquals(maxToSettle, pathfinder.settledCount());

        // Extend search
        int additionalToSettle = 2;
        snapshot = pathfinder.extendSearch(additionalToSettle);
        assertEquals(maxToSettle + additionalToSettle, pathfinder.settledCount());

        // Extend by more than remaining vertices
        snapshot = pathfinder.extendSearch(2*g.vertexCount());
        assertEquals(g.vertexCount(), pathfinder.settledCount());
        assertTrue(pathfinder.allPathsFound());
    }
}
