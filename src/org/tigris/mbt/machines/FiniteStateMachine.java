//This file is part of the Model-based Testing java package
//Copyright (C) 2005  Kristian Karl
//
//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

package org.tigris.mbt.machines;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.tigris.mbt.Keywords;
import org.tigris.mbt.Util;
import org.tigris.mbt.exceptions.FoundNoEdgeException;
import org.tigris.mbt.graph.AbstractElement;
import org.tigris.mbt.graph.Edge;
import org.tigris.mbt.graph.Graph;
import org.tigris.mbt.graph.Vertex;

/**
 * @author Johan Tejle
 * 
 */
public class FiniteStateMachine {

	static Logger logger = Util.setupLogger(FiniteStateMachine.class);

	protected Graph model = null;
	protected Vertex currentVertex = null;
	private boolean weighted = false;
	private Edge lastEdge = null;
	private Stack<Edge> edgeStack;
	private Stack<Integer> vertexStore;
	private int numberOfEdgesTravesed = 0;
	protected boolean backtracking = false;
	protected boolean calculatingPath = false;
	private int numOfCoveredEdges = 0;
	private int numOfCoveredVertices = 0;

	private long start_time;

	private Hashtable<String, Integer> associatedRequirements;

	public int getNumOfCoveredEdges() {
  	return numOfCoveredEdges;
  }

	public int getNumOfCoveredVertices() {
	  return numOfCoveredVertices;
  }

	public void setVertex(String vertexName) {
		logger.debug("Setting vertex to: '" + vertexName + "'");
		Vertex e = findVertex(vertexName);
		Util.AbortIf(e == null, "Vertex not Found: '" + vertexName + "'");

		currentVertex = e;
		setAsVisited(e);
	}

	public Vertex findVertex(String vertexName) {
		for (Vertex vertex : model.getVertices()) {
			if (((String) vertex.getLabelKey()).equals(vertexName)) {
				return vertex;
			}
    }
		return null;
	}

	public boolean hasVertex(String vertexName) {
		if (findVertex(vertexName) != null) {
			return true;
		}
		return false;
	}

	public Edge findEdge(String edgeName) {
		for (Edge edge : model.getEdges()) {
			if (((String) edge.getLabelKey()).equals(edgeName)) {
				return edge;
			}
		}
		return null;
	}

	public FiniteStateMachine(Graph model) {
		this();
		this.setModel(model);
	}

	public FiniteStateMachine() {
		logger.debug("Initializing");
		edgeStack = new Stack<Edge>();
		start_time = System.currentTimeMillis();
	}

	public void setModel(Graph model) {
		reset();
		this.model = model;
		setVertex(Keywords.START_NODE);
	}

	public Vertex getCurrentVertex() {
		return currentVertex;
	}

	public String getCurrentVertexName() {
		return currentVertex.getLabelKey();
	}

	public Collection<Vertex> getAllVertices() {
		return model.getVertices();
	}

	public Collection<Edge> getAllEdges() {
		return model.getEdges();
	}

	public Set<Edge> getCurrentOutEdges() throws FoundNoEdgeException {
		Set<Edge> retur = new HashSet<Edge>(model.getOutEdges(currentVertex));
		if (retur.size() == 0) {
			throw new FoundNoEdgeException("Cul-De-Sac, dead end found in '" + getCurrentVertex() + "'");
		}
		return retur;
	}

	public void setAsVisited(AbstractElement e) {
		if ( e instanceof Edge ) {
			if ( e.getVisitedKey() < 1 ) {
				numOfCoveredEdges++;
			}
		}
		else if ( e instanceof Vertex ) {
			if ( e.getVisitedKey() < 1 ) {
				numOfCoveredVertices++;
			}
		}

		e.setVisitedKey(e.getVisitedKey() + 1);

		if (e.getReqTagKey().isEmpty() == false) {
			Hashtable<String, Integer> reqs = getAllRequirements();
			String[] tags = e.getReqTagKey().split(",");
			for (int j = 0; j < tags.length; j++) {
				reqs.put(tags[j], new Integer(((Integer) reqs.get(tags[j])).intValue() + 1));
			}
		}
	}

	public void setAsUnvisited(AbstractElement e) {
		Integer visits = e.getVisitedKey();
		e.setVisitedKey(e.getVisitedKey() - 1);
		if ( e instanceof Edge ) {
			if ( e.getVisitedKey() < 1 ) {
				numOfCoveredEdges--;
			}
		}
		else if ( e instanceof Vertex ) {
			if ( e.getVisitedKey() < 1 ) {
				numOfCoveredVertices--;
			}
		}

		if (visits <= 0)
			logger.error(e + ", has a negative number in VISITED_KEY");

		if (!e.getReqTagKey().isEmpty()) {
			Hashtable<String, Integer> reqs = getAllRequirements();
			String[] tags = e.getReqTagKey().split(",");
			for (int j = 0; j < tags.length; j++) {
				reqs.put(tags[j], new Integer(((Integer) reqs.get(tags[j])).intValue() - 1));
			}
		}
	}

	public void walkPath(Stack<Edge> path) {
		for (Edge edge : path) {
			walkEdge(edge);
		}
	}

	public boolean walkEdge(Edge edge) {
		if (model.isSource(currentVertex, edge)) {
			lastEdge = edge;
			if (isBacktrackPossible()) {
				track();
			}

			currentVertex = model.getDest(edge);
			setAsVisited(lastEdge);
			setAsVisited(currentVertex);
			numberOfEdgesTravesed++;
			logger.debug("No. of walked edges: " + numberOfEdgesTravesed);
			return true;
		} else
			logger.error(edge + ", is not the source of: " + currentVertex);
		return false;
	}

	public Edge getLastEdge() {
		return lastEdge;
	}

	public String getStatisticsStringCompact() {
		int stats[] = getStatistics();
		int e = stats[0];
		int ec = stats[1];
		int v = stats[2];
		int vc = stats[3];
		int len = stats[4];
		int req = stats[5];
		int reqc = stats[6];

		return (req > 0 ? "RC: " + reqc + "/" + req + " => " + (100 * reqc) / req + "% " : "") + "EC: " + ec + "/" + e + " => " + (100 * ec)
		    / e + "% " + "SC: " + vc + "/" + v + " => " + (100 * vc) / v + "% " + "L: " + len;
	}

	public String getStatisticsString() {
		int stats[] = getStatistics();
		int e = stats[0];
		int ec = stats[1];
		int v = stats[2];
		int vc = stats[3];
		int len = stats[4];
		int req = stats[5];
		int reqc = stats[6];

		String str = "";
		if (e > 0 && v > 0)
			str = "Coverage Edges: " + ec + "/" + e + " => " + (100 * ec) / e + "%\n" + "Coverage Vertices: " + vc + "/" + v + " => "
			    + (100 * vc) / v + "%\n" + "Unvisited Edges:  " + (e - ec) + "\n" + "Unvisited Vertices: " + (v - vc) + "\n"
			    + "Test sequence length:  " + len;
		else if (req > 0)
			str = "Coverage Requirements: " + reqc + "/" + req + " => " + (100 * reqc) / req + "%\n";
		else
			str = "No statistics available. Probably no run made?";

		return str;
	}

	public int[] getStatistics() {
		Collection<Edge> e = model.getEdges();
		Collection<Vertex> v = model.getVertices();

		int[] retur = { e.size(), getEdgeCoverage(e), v.size(), getVertexCoverage(v), numberOfEdgesTravesed, getAllRequirements().size(),
		    getCoveredRequirements().size() };
		return retur;
	}

	public String getStatisticsVerbose() {
		String retur = "";
		String newLine = "\n";

		Vector<String> notCovered = new Vector<String>();
		for (Edge edge : model.getEdges()) {
			if (edge.getVisitedKey() <= 0) {
				notCovered.add("Edge not reached: " + edge + newLine);
			}
		}
		for (Vertex vertex : model.getVertices()) {
			if (vertex.getVisitedKey() <= 0) {
				notCovered.add("Vertex not reached: " + vertex + newLine);
			}
		}
		if (notCovered.size() > 0) {
			Collections.sort(notCovered);
			for (String string : notCovered) {
				retur += string;
      }
		}
		retur += getStatisticsString() + newLine;
		retur += "Execution time: " + ((System.currentTimeMillis() - start_time) / 1000) + " seconds";
		return retur;
	}

	public boolean isCurrentVertex(Vertex vertex) {
		return getCurrentVertex().equals(vertex);
	}

	protected int getCoverage(Collection<AbstractElement> modelItems) {
		int unique = 0;

		for (AbstractElement abstractElement : modelItems) {
			if (abstractElement.getVisitedKey() > 0) {
				unique++;
			}
		}

		return unique;
	}

	protected int getVertexCoverage(Collection<Vertex> modelItems) {
		int unique = 0;

		for (Vertex vertex : modelItems) {
			if (vertex.getVisitedKey() > 0) {
				unique++;
			}	    
    }

		return unique;
	}

	protected int getEdgeCoverage(Collection<Edge> modelItems) {
		int unique = 0;

		for (Edge edge : modelItems) {
			if (edge.getVisitedKey() > 0) {
				unique++;
			}	    
    }

		return unique;
	}

	public Hashtable<String, Integer> getAllRequirements() {
		if (associatedRequirements == null) {
			associatedRequirements = new Hashtable<String, Integer>();

			Vector<AbstractElement> abstractElements = new Vector<AbstractElement>();
			abstractElements.addAll(getAllVertices());
			abstractElements.addAll(getAllEdges());

			for (AbstractElement abstractElement : abstractElements) {
				String reqtags = abstractElement.getReqTagKey();
				if (!reqtags.isEmpty()) {
					String[] tags = reqtags.split(",");
					for (int j = 0; j < tags.length; j++) {
						associatedRequirements.put(tags[j], new Integer(0));
					}
				}
			}
		}
		return associatedRequirements;
	}

	@SuppressWarnings("unchecked")
	public Set<String> getCoveredRequirements() {
		Vector<Integer> notCoveredValues = new Vector<Integer>();
		notCoveredValues.add(new Integer(0));
		Hashtable<String, Integer> allRequirements = (Hashtable<String, Integer>) getAllRequirements().clone();
		allRequirements.values().removeAll(notCoveredValues);
		return allRequirements.keySet();
	}

	public String getEdgeName(Edge edge) {
		if (edge.getParameterKey().isEmpty()) {
			return edge.getLabelKey();
		}

		return edge.getLabelKey() + " " + edge.getParameterKey();
	}

	public String getVertexName(Vertex vertex) {
		String l = vertex.getLabelKey();
		String p = vertex.getParameterKey();

		return (l == null ? "" : l) + (p == null ? "" : " " + p);
	}

	public void storeVertex() {
		if (this.vertexStore == null)
			this.vertexStore = new Stack<Integer>();
		this.vertexStore.push(new Integer(edgeStack.size()));
	}

	public void restoreVertex() {
		if (this.vertexStore == null || this.vertexStore.size() == 0)
			throw new RuntimeException("Nothing to restore");
		int prevVertex = ((Integer) this.vertexStore.pop()).intValue();
		if (prevVertex > edgeStack.size())
			throw new RuntimeException("Cannot restore vertex from backtrack");
		while (prevVertex < edgeStack.size()) {
			popVertex();
		}
	}

	protected void track() {
		edgeStack.push(getLastEdge());
	}

	protected void popVertex() {
		setAsUnvisited(getLastEdge());
		setAsUnvisited(getCurrentVertex());

		edgeStack.pop();
		if (lastEdge == null) {
			setVertex(Keywords.START_NODE);
		} else {
			currentVertex = model.getSource(lastEdge);
		}
		lastEdge = (edgeStack.size() > 0 ? (Edge) edgeStack.peek() : null);
		numberOfEdgesTravesed--;
	}

	/**
	 * @param weighted
	 *          if edge weights are to be considered
	 */
	public void setWeighted(boolean weighted) {
		this.weighted = weighted;
	}

	/**
	 * @return true if the edge weights is considered
	 */
	public boolean isWeighted() {
		return weighted;
	}

	/**
	 * @return the number of edges traversed
	 */
	public int getNumberOfEdgesTravesed() {
		return numberOfEdgesTravesed;
	}

	public void backtrack() {
		if (isBacktrackPossible()) {
			popVertex();
		} else {
			if (!isLastEdgeBacktrackSupported())
				throw new RuntimeException("Backtracking was asked for, but model does not suppport BACKTRACK at egde: " + getLastEdge());
			if (!isBacktrackEnabled())
				throw new RuntimeException("Backtracking was asked for, but was disabled.");
			throw new RuntimeException("Backtracking was asked for, but was refused");
		}
	}

	public void setBacktrackEnabled(boolean backtracking) {
		this.backtracking = backtracking;
	}

	public boolean isBacktrackEnabled() {
		return this.backtracking;
	}

	public boolean isBacktrackPossible() {
		return isBacktrackEnabled() || isCalculatingPath() || isLastEdgeBacktrackSupported();
	}

	private boolean isLastEdgeBacktrackSupported() {
		return getLastEdge().isBacktrackKey();
	}

	public boolean isCalculatingPath() {
		return calculatingPath;
	}

	public void setCalculatingPath(boolean calculatingPath) {
		this.calculatingPath = calculatingPath;
	}

	/**
	 * This functions returns a list of edges, which has not yet been covered
	 */
	public Vector<Edge> getUncoveredEdges() {
		Vector<Edge> retur = new Vector<Edge>();
		for (Edge edge : getAllEdges()) {
			if (edge.getVisitedKey() <= 0) {
				retur.add(edge);
			}
		}
		return retur;
	}

	/**
	 * This functions returns a list of edges, which has been covered
	 */
	public Vector<Edge> getCoveredEdges() {
		Vector<Edge> retur = new Vector<Edge>(getAllEdges());
		retur.removeAll(getUncoveredEdges());
		return retur;
	}

	public Vector<Vertex> getUncoveredVertices() {
		Vector<Vertex> retur = new Vector<Vertex>();
		for (Vertex vertex : getAllVertices()) {
			if (vertex.getVisitedKey() <= 0) {
				retur.add(vertex);
			}
		}
		return retur;
	}

	public Vector<Vertex> getCoveredVertices() {
		Vector<Vertex> retur = new Vector<Vertex>(getAllVertices());
		retur.removeAll(getUncoveredVertices());
		return retur;
	}

	public Vector<AbstractElement> getUncoveredElements() {
		Vector<AbstractElement> retur = new Vector<AbstractElement>(getUncoveredEdges());
		retur.addAll(getUncoveredVertices());
		return retur;
	}

	public Vector<AbstractElement> getCoveredElements() {
		Vector<AbstractElement> retur = new Vector<AbstractElement>(getCoveredEdges());
		retur.addAll(getCoveredVertices());
		return retur;
	}

	public Graph getModel() {
		return model;
	}

	public String getCurrentDataString() {
		return "";
	}

	public boolean hasInternalVariables() {
		return false;
	}

	public void reset() {
		numberOfEdgesTravesed = 0;
		backtracking = false;
		calculatingPath = false;
		numOfCoveredEdges = 0;
		numOfCoveredVertices = 0;
  }
}
