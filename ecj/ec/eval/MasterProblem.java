/*
  Copyright 2006 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package ec.eval;

import ec.*;
import ec.util.*;
import ec.coevolve.GroupedProblemForm;
import ec.simple.SimpleProblemForm;
import java.util.ArrayList;

import java.io.*;

/**
 * MasterProblem.java
 *

 <p>The MasterProblem is an ECJ problem that performs evaluations by pooling an available slave
 and sending all information necessary for the evaluation to that slave.  In some sense, the
 MasterProblem is the "master" of the master-slave architecture.  As it implements both the
 <i>SimpleProblemForm</i> and the <i>GroupedProblemForm</i> interfaces, the MasterProblem
 can perform both traditional EC evaluations, as well as coevolutionary evaluations.
 
 <p><b>Parameters</b><br>
 <table>
 <tr><td valign=top><i>base.</i><tt>debug-info</tt><br>
 <font size=-1>boolean</font></td>
 <td valign=top>(whether the system should display information useful for debugging purposes)<br>
 </td></tr>

 </table>

 * @author Liviu Panait
 * @version 1.0 
 */

public class MasterProblem extends Problem implements SimpleProblemForm, GroupedProblemForm 
    {

    public static final String P_DEBUG_INFO = "debug-info";
    public static final String P_JOB_SIZE = "job-size";
        
    int jobSize;
    boolean showDebugInfo;

    public Problem problem;
        
    public MasterProblemServer server;
        
    public Thread serverThread;
        
    public boolean batchMode;

    // except for the problem, everything else is shallow-cloned
    public Object clone()
        {
        MasterProblem c = (MasterProblem)(super.clone());

        // shallow-cloned stuff
        c.server = server;
        c.serverThread = serverThread;
        c.batchMode = batchMode;
        c.jobSize = jobSize; 
        c.showDebugInfo = showDebugInfo;

        // deep-cloned stuff
        c.problem = (Problem)(problem.clone());

        return c;
        }

    // setup
    public void setup(final EvolutionState state, final Parameter base) 
        {
        Thread.currentThread().setName("MainThread: ");
        super.setup(state, base);
        showDebugInfo = state.parameters.getBoolean(base.push(P_DEBUG_INFO),null,false);
                
        jobSize = state.parameters.getIntWithDefault(base.push(P_JOB_SIZE),null,1);
        if (jobSize<=0)
            state.output.fatal("The job size must be an integer > 0.", base.push(P_JOB_SIZE));

        batchMode = false;
        }

    // prepare for a batch of evaluations
    public void prepareToEvaluate(final EvolutionState state, final int threadnum)
        {
        if (jobSize > 1) queue = new ArrayList();
        batchMode = true;
        }

    // wait until a batch of evaluations is finished
    public void finishEvaluating(final EvolutionState state, final int threadnum)
        {
        if(showDebugInfo)
            state.output.message(Thread.currentThread().getName() + "Waiting for all slaves to finish.");
        flush(state, threadnum);
        queue = null;  // get rid of it just in case
                
        server.slaveMonitor.waitForAllSlavesToFinishEvaluating( state );
        batchMode = false;
        if(showDebugInfo)
            state.output.message(Thread.currentThread().getName() + "All slaves have finished their jobs.");
        }

    // evaluate a regular individual
    public void evaluate(EvolutionState state, Individual ind, int threadnum)
        {
        if (jobSize > 1 && batchMode == true)    // chunked evaluation mechanism
            {
            queue.add(ind);
            if (queue.size() >= jobSize)
                flush(state, threadnum);
            }
        else    /// ordinary evaluation mechanism  
            evaluate(state, new Individual[] { ind }, threadnum);           
        }
        
        
    ArrayList queue;
    void flush(EvolutionState state, int threadnum)
        {
        if (queue!=null && queue.size() > 0 )
            {
            Individual[] inds = new Individual[queue.size()];
            for(int i = 0; i < queue.size(); i++)
                {
                inds[i] = (Individual)(queue.get(i));
                }
            evaluate(state, inds, threadnum);
            }
        queue = new ArrayList();
        }


    // send a group of individuals to one slave for evaluation 
    void evaluate(EvolutionState state, Individual inds[], int threadnum)
        {
        if(showDebugInfo)
            state.output.message(Thread.currentThread().getName() + "Starting an evaluation.");
                
        // Determine the subpopulation number associated with this individual (assumes all individuals have the same subpopulation)
        int subPopNum = 0;
        boolean found = false;
        for (int x=0;x<state.population.subpops.length && !found;x++)
            {
            if (state.population.subpops[x].species == inds[0].species)
                {
                subPopNum = x;
                found = true;
                }
            }
                
        if (!found)
            state.output.fatal("Whoa!  Couldn't find a matching species for the individual!");
                
                
        // Acquire a slave socket
        Job job = new Job();
        //job.state = state;
        //job.mp = this;
        //job.threadnum = threadnum;
        job.batchMode = batchMode;
        job.type = Slave.V_EVALUATESIMPLE;
        job.inds = inds;
        job.subPops = new int[] { subPopNum } ;
        job.updateFitness = new boolean[inds.length]; 
        for (int i=0 ; i < inds.length; i++) 
            job.updateFitness[i]=true; 
        server.slaveMonitor.scheduleJobForEvaluation(state,job);
        if( !batchMode )
            server.slaveMonitor.waitForAllSlavesToFinishEvaluating( state );
        if(showDebugInfo) state.output.message(Thread.currentThread().getName() + "Finished evaluating the individual.");
        }
        
        
        

    /* (non-Javadoc)
     * @see ec.simple.SimpleProblemForm#describe(ec.Individual, ec.EvolutionState, int, int, int)
     */
    public void describe(Individual ind, EvolutionState state, int threadnum,
                         int log, int verbosity) 
        {
        if (!(problem instanceof SimpleProblemForm)) 
            {
            state.output.fatal("StarProblem.describe(...) invoked, but the Problem is not of SimpleProblemForm");
            }
                
        ((SimpleProblemForm)problem).describe( ind, state, threadnum, log, verbosity);
        }

    /* (non-Javadoc)
     * @see ec.coevolve.GroupedProblemForm#preprocessPopulation(ec.EvolutionState, ec.Population)
     */
    public void preprocessPopulation(EvolutionState state, Population pop) 
        {
        if (!(problem instanceof GroupedProblemForm)) 
            {
            state.output.fatal("StarProblem.preprocessPopulation(...) invoked, but the Problem is not of GroupedProblemForm");
            }
                
        ((GroupedProblemForm) problem).preprocessPopulation(state, pop);
        }

    /* (non-Javadoc)
     * @see ec.coevolve.GroupedProblemForm#postprocessPopulation(ec.EvolutionState, ec.Population)
     */
    public void postprocessPopulation(EvolutionState state, Population pop) 
        {
        if (!(problem instanceof GroupedProblemForm)) 
            {
            state.output.fatal("StarProblem.postprocessPopulation(...) invoked, but the Problem is not of GroupedProblemForm");
            }
                
        ((GroupedProblemForm) problem).postprocessPopulation(state, pop);
        }

    // regular coevolutionary evaluation
    public void evaluate(EvolutionState state, Individual[] inds,
                         boolean[] updateFitness, boolean countVictoriesOnly, int threadnum)
        {
        if(showDebugInfo)
            state.output.message("Starting a coevolutionary evaluation.");

        // Determine the subpopulation number associated with the individuals
        int[] subPopNum = new int[inds.length];
        boolean subPopNumFound = false;
        for(int i=0;i<inds.length;i++)
            {
            subPopNumFound = false;
            for (int x=0;x<state.population.subpops.length && !subPopNumFound;x++)
                {
                if (state.population.subpops[x].species == inds[i].species)
                    {
                    subPopNum[i] = x;
                    subPopNumFound = true;
                    break;
                    }
                }
            if (!subPopNumFound)
                {
                // Is it possible that there isn't a matching species?
                state.output.fatal("Whoa!  Couldn't find a matching species for Individual!");
                }
            }

        // Acquire a slave socket
        Job job = new Job();
        //job.state = state;
        //job.mp = this;
        //job.threadnum = threadnum;
        job.type = Slave.V_EVALUATEGROUPED;
        job.subPops = subPopNum;
        job.countVictoriesOnly = countVictoriesOnly;
        job.inds = inds;
        job.updateFitness = updateFitness;
        job.batchMode = batchMode;
        //job.index = 0;
        server.slaveMonitor.scheduleJobForEvaluation(state,job);
                
        if( !batchMode )
            server.slaveMonitor.waitForAllSlavesToFinishEvaluating( state );

        if(showDebugInfo)
            state.output.message("Finished the coevolutionary evaluation.");
        }

    /** Custom serialization */
    private void writeObject(ObjectOutputStream out) throws IOException
        {
        out.writeObject(problem);
        }

    /** Custom serialization */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
        {
        problem = (Problem) in.readObject();
        }

    /** Initialize contacts with the slaves */
    public void initializeContacts( final EvolutionState state )
        {
        server = new MasterProblemServer(showDebugInfo);
        server.setupServerFromDatabase(state);
        if(showDebugInfo)
            state.output.message(Thread.currentThread().getName() + "Spawning the server thread.");
        serverThread = server.spawnThread();
        }

    /** Reinitialize contacts with the slaves */
    public void reinitializeContacts( final EvolutionState state )
        {
        initializeContacts(state);
        }

    /** Gracefully close contacts with the slaves */
    public void closeContacts(EvolutionState state, int result)
        {
        this.server.shutdown();
        try
            {
            this.serverThread.join();
            }
        catch (InterruptedException e)
            {
            }
        }
        
    public boolean canEvaluate() 
        {
        return (server.slaveMonitor.numAvailableSlaves() != 0); 
        }
        
    /** This will only return true if (1) the EvolutionState is a SteadyStateEvolutionState and
        (2) an individual has returned from the system.  If you're not doing steady state evolution,
        you should not call this method.  */
    public boolean evaluatedIndividualAvailable()
        {
        return server.slaveMonitor.evaluatedIndividualAvailable();
        }
    
    /** This method blocks until an individual is available from the slaves (which will cause evaluatedIndividualAvailable()
        to return true), at which time it returns the individual.  You should only call this method
        if you're doing steady state evolution -- otherwise, the method will block forever. */
    public Individual getNextEvaluatedIndividual()
        {
        return server.slaveMonitor.waitForIndividual();
        }

    }