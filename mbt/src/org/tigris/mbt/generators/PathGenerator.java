package org.tigris.mbt.generators;

import java.util.Set;

import org.tigris.mbt.FiniteStateMachine;
import org.tigris.mbt.conditions.StopCondition;
import org.tigris.mbt.exceptions.FoundNoEdgeException;

import edu.uci.ics.jung.graph.impl.DirectedSparseEdge;

public abstract class PathGenerator {
    private FiniteStateMachine machine;
	private StopCondition stopCondition;
	
	public abstract String[] getNext();
    
    public boolean hasNext()
    {
    	return !stopCondition.isFulfilled();
    }
    public FiniteStateMachine getMachine() {
		return machine;
	}
    public void setMachine(FiniteStateMachine machine) {
		this.machine = machine;
		if(this.stopCondition != null)
			this.stopCondition.setMachine(machine);
	}
    public void setStopCondition(StopCondition stopCondition) {
		this.stopCondition = stopCondition;
		if(this.machine != null)
			this.stopCondition.setMachine(this.machine);
	}
    public StopCondition getStopCondition() {
		return stopCondition;
	}
    /**
     * @return the condition fulfilment
     */
    public double getConditionFulfilment()
    {
    	return stopCondition.getFulfilment();
    }

	/**
	 * Will reset the generator to its initial state.
	 */
	public void reset()
	{
	}

	PathGenerator() {
	}
    
    PathGenerator( StopCondition stopCondition ) {
    	setStopCondition(stopCondition);
	}
    
    PathGenerator( FiniteStateMachine machine, StopCondition stopCondition )
    {
    	this(stopCondition);
    	setMachine(machine);
    }
    
    public String toString() {
    	return getStopCondition().toString();
    }

    public boolean isEdgeAvailable( DirectedSparseEdge edge )
    {
		Set availableEdges;
		try 
		{
			availableEdges = getMachine().getCurrentOutEdges();
		}
		catch ( FoundNoEdgeException e )
		{
			throw new RuntimeException("No possible edges available for path", e);
		}
		if ( availableEdges.contains( edge ) )
		{
			return true;
		}
    	
		return false;
	}
}