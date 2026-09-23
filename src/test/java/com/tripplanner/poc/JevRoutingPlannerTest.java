package com.tripplanner.poc;

import com.tripplanner.poc.agentic.CostAgent;
import com.tripplanner.poc.agentic.GeneralAgent;
import com.tripplanner.poc.agentic.JevRoutingPlanner;
import com.tripplanner.poc.agentic.JevRouter;
import com.tripplanner.poc.agentic.ReservationAgent;
import com.tripplanner.poc.agentic.WeatherAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the Jev-driven routing planner picks the correct sub-agent for each Jev decision, and
 * that it uses the router topology.
 */
class JevRoutingPlannerTest {

    private AgentInstance reservationAgent;
    private AgentInstance weatherAgent;
    private AgentInstance costAgent;
    private AgentInstance generalAgent;
    private JevRoutingPlanner planner;

    @BeforeEach
    void setUp() {
        reservationAgent = mock(AgentInstance.class);
        weatherAgent = mock(AgentInstance.class);
        costAgent = mock(AgentInstance.class);
        generalAgent = mock(AgentInstance.class);
        doReturn(ReservationAgent.class).when(reservationAgent).type();
        doReturn(WeatherAgent.class).when(weatherAgent).type();
        doReturn(CostAgent.class).when(costAgent).type();
        doReturn(GeneralAgent.class).when(generalAgent).type();

        InitPlanningContext initContext = mock(InitPlanningContext.class);
        when(initContext.subagents())
                .thenReturn(List.of(reservationAgent, weatherAgent, costAgent, generalAgent));

        planner = new JevRoutingPlanner();
        planner.init(initContext);
    }

    private JevRouter.RouteDecision decision(String route) {
        return new JevRouter.RouteDecision(route, route);
    }

    @Test
    void usesRouterTopology() {
        assertEquals(AgenticSystemTopology.ROUTER, planner.topology());
    }

    @Test
    void picksWeatherAgentForWeatherRoute() {
        assertSame(weatherAgent, planner.pickSubagent(decision(JevRouter.ROUTE_WEATHER)));
    }

    @Test
    void picksReservationAgentForReservationRoute() {
        assertSame(reservationAgent, planner.pickSubagent(decision(JevRouter.ROUTE_RESERVATION)));
    }

    @Test
    void picksCostAgentForCostRoute() {
        assertSame(costAgent, planner.pickSubagent(decision(JevRouter.ROUTE_COST)));
    }

    @Test
    void picksGeneralAgentForGeneralOrUnrecognizedRoute() {
        assertSame(generalAgent, planner.pickSubagent(decision(JevRouter.ROUTE_GENERAL)));
        assertSame(generalAgent, planner.pickSubagent(decision("something-else")));
    }

    @Test
    void unknownSubagentsAreIgnoredOnInit() {
        AgentInstance other = mock(AgentInstance.class);
        doReturn(String.class).when(other).type();
        InitPlanningContext initContext = mock(InitPlanningContext.class);
        when(initContext.subagents()).thenReturn(List.of(other, weatherAgent));
        JevRoutingPlanner p = new JevRoutingPlanner();
        p.init(initContext);
        // Weather is wired, the unknown sub-agent is ignored.
        assertSame(weatherAgent, p.pickSubagent(decision(JevRouter.ROUTE_WEATHER)));
        assertNull(p.pickSubagent(decision(JevRouter.ROUTE_COST)));
    }
}
