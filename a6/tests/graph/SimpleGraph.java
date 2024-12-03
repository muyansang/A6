/**
 * The classes below provide a simple implementation of the Graph interfaces where edge weights are
 * intrinsic to the Edge objects.  For convenience, a mutable set is used to store each vertex's
 * outgoing edges.  These have been factored out from the version of ShortestPathsTest that was part of
 * the student release code, in order to make them available to other test suites.
 */

package graph;

import java.util.*;

public class SimpleGraph implements Graph<SimpleGraph.SimpleVertex> {

    private final List<SimpleVertex> vertices = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();

    public int vertexCount() {
        return vertices.size();
    }

    public SimpleVertex getVertex(int id) {
        return vertices.get(id);
    }

    public SimpleVertex addVertex(String label) {
        SimpleVertex newVertex = new SimpleVertex(vertices.size(), label, new HashSet<>());
        vertices.add(newVertex);
        index.put(label, newVertex.id());
        return newVertex;
    }

    public SimpleVertex getVertexByLabel(String label) {
        return getVertex(index.get(label));
    }

    public void addEdge(int startId, int endId, int weight) {
        getVertex(startId).outgoingEdges().add(new SimpleEdge(startId, endId, weight));
    }

    public static SimpleGraph fromText(String text) {
        SimpleGraph g = new SimpleGraph();
        Scanner lines = new Scanner(text);
        Map<String, Integer> labelIndex = new HashMap<>();
        while (lines.hasNextLine()) {
            // Tokenize line
            String[] tokens = lines.nextLine().trim().split("\\s+");
            if (tokens.length == 0) {
                // Skip blank lines
                continue;
            }
            String startLabel = tokens[0];
            String edgeType = tokens[1];
            String endLabel = tokens[2];
            // If no weight token, default weight is 1
            int weight = (tokens.length > 3) ? Integer.parseInt(tokens[3]) : 1;

            // Look up vertex IDs from labels, adding new vertices as necessary
            int startId = labelIndex.computeIfAbsent(startLabel, label -> g.addVertex(label).id());
            int endId = labelIndex.computeIfAbsent(endLabel, label -> g.addVertex(label).id());

            // Add edge(s)
            if ("->".equals(edgeType)) {
                g.addEdge(startId, endId, weight);
            } else if ("--".equals(edgeType)) {
                g.addEdge(startId, endId, weight);
                g.addEdge(endId, startId, weight);
            } else {
                throw new IllegalArgumentException("Unexpected edge type: " + edgeType);
            }
        }
        return g;
    }

    //////////////////////////////////////////////////////////////////
    public record SimpleVertex(int id, String label, Set<SimpleEdge> outgoingEdges)
            implements Vertex<SimpleEdge> {
    }

    public record SimpleEdge(int startId, int endId, int weight) implements Edge {

    }

    public static class SimpleWeigher implements Weigher<SimpleEdge> {

        @Override
        public int weight(SimpleEdge edge) {
            return edge.weight();
        }
    }

} // class SimpleGraph