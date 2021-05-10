package stochastic.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import stochastic.domain.Leg;
import stochastic.domain.Tail;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathEnumeratorTests {
    private final Leg prevLeg;

    PathEnumeratorTests() {
        prevLeg = new Leg(
            0,
            1,
            100,
            101,
            45,
            0,
            ZonedDateTime.parse("2017-11-15T08:45:00.000Z").toEpochSecond() / 60,
            ZonedDateTime.parse("2017-11-15T09:45:00.000Z").toEpochSecond() / 60
        );
        prevLeg.setIndex(0);
    }

    @Test
    @DisplayName("Path with 1 flight should have no propagated delay")
    void testOneFlightPath() {
        ArrayList<Leg> legs = new ArrayList<>(Collections.singletonList(prevLeg));
        Tail tail = new Tail(0, legs);
        int[] primaryDelays = new int[1];
        primaryDelays[0] = 120;
        HashMap<Integer, ArrayList<Integer>> adj = new HashMap<>();
        PathEnumerator pathEnumerator = new PathEnumerator(tail, legs, primaryDelays, adj);
        ArrayList<Path> paths = pathEnumerator.generatePaths();

        assertEquals(1, paths.size());
        ArrayList<Integer> propagatedDelays = paths.get(0).getPropagatedDelays();
        ArrayList<Integer> expectedPropagatedDelays = new ArrayList<>(Collections.singletonList(0));
        assertEquals(expectedPropagatedDelays, propagatedDelays);
    }

    @Test
    @DisplayName("Path with 2 flights and large slack should have no propagated delay")
    void testTwoFlightPathWithLargeSlack() {
        Leg nextLeg = new Leg(
            1,
            2,
            prevLeg.getArrPort(),
            prevLeg.getDepPort(),
            prevLeg.getTurnTimeInMin(),
            prevLeg.getOrigTailId(),
            ZonedDateTime.parse("2017-11-15T15:30:00.000Z").toEpochSecond() / 60,
            ZonedDateTime.parse("2017-11-15T16:30:00.000Z").toEpochSecond() / 60
        );
        nextLeg.setIndex(1);
        ArrayList<Leg> legs = new ArrayList<>(Arrays.asList(prevLeg, nextLeg));
        Tail tail = new Tail(0, legs);
        int[] primaryDelays = new int[2];
        primaryDelays[0] = 120;
        primaryDelays[1] = 45;
        HashMap<Integer, ArrayList<Integer>> adj = new HashMap<>();
        adj.put(prevLeg.getId(), new ArrayList<>(Collections.singletonList(nextLeg.getId())));
        PathEnumerator pathEnumerator = new PathEnumerator(tail, legs, primaryDelays, adj);
        ArrayList<Path> paths = pathEnumerator.generatePaths();

        assertEquals(1, paths.size());
        ArrayList<Integer> propagatedDelays = paths.get(0).getPropagatedDelays();
        ArrayList<Integer> expectedPropagatedDelays = new ArrayList<>(Arrays.asList(0, 0));
        assertEquals(expectedPropagatedDelays, propagatedDelays);
    }

    @Test
    @DisplayName("Path with no slack should propagate primary delay of first flight")
    void testTwoFlightPathWithNoSlack() {
        Leg nextLeg = new Leg(
            1,
            2,
            prevLeg.getArrPort(),
            prevLeg.getDepPort(),
            prevLeg.getTurnTimeInMin(),
            prevLeg.getOrigTailId(),
            ZonedDateTime.parse("2017-11-15T10:30:00.000Z").toEpochSecond() / 60,
            ZonedDateTime.parse("2017-11-15T11:30:00.000Z").toEpochSecond() / 60
        );
        nextLeg.setIndex(1);
        ArrayList<Leg> legs = new ArrayList<>(Arrays.asList(prevLeg, nextLeg));
        Tail tail = new Tail(0, legs);
        int[] primaryDelays = new int[2];
        primaryDelays[0] = 120;
        primaryDelays[1] = 45;
        HashMap<Integer, ArrayList<Integer>> adj = new HashMap<>();
        adj.put(prevLeg.getId(), new ArrayList<>(Collections.singletonList(nextLeg.getId())));
        PathEnumerator pathEnumerator = new PathEnumerator(tail, legs, primaryDelays, adj);
        ArrayList<Path> paths = pathEnumerator.generatePaths();
        assertEquals(1, paths.size());

        ArrayList<Integer> propagatedDelays = paths.get(0).getPropagatedDelays();
        ArrayList<Integer> expectedPropagatedDelays = new ArrayList<>(Arrays.asList(0, 120));
        assertEquals(expectedPropagatedDelays, propagatedDelays);
    }

    @Test
    @DisplayName("Path with small slack should propagate part of primary delay of first flight")
    void testTwoFlightPathWithSmallSlack() {
        Leg nextLeg = new Leg(
            1,
            2,
            prevLeg.getArrPort(),
            prevLeg.getDepPort(),
            prevLeg.getTurnTimeInMin(),
            prevLeg.getOrigTailId(),
            ZonedDateTime.parse("2017-11-15T10:45:00.000Z").toEpochSecond() / 60,
            ZonedDateTime.parse("2017-11-15T11:45:00.000Z").toEpochSecond() / 60
        );
        nextLeg.setIndex(1);
        ArrayList<Leg> legs = new ArrayList<>(Arrays.asList(prevLeg, nextLeg));
        Tail tail = new Tail(0, legs);
        int[] primaryDelays = new int[2];
        primaryDelays[0] = 120;
        primaryDelays[1] = 45;
        HashMap<Integer, ArrayList<Integer>> adj = new HashMap<>();
        adj.put(prevLeg.getId(), new ArrayList<>(Collections.singletonList(nextLeg.getId())));
        PathEnumerator pathEnumerator = new PathEnumerator(tail, legs, primaryDelays, adj);
        ArrayList<Path> paths = pathEnumerator.generatePaths();

        assertEquals(1, paths.size());
        ArrayList<Integer> propagatedDelays = paths.get(0).getPropagatedDelays();
        ArrayList<Integer> expectedPropagatedDelays = new ArrayList<>(Arrays.asList(0, 105));
        assertEquals(expectedPropagatedDelays, propagatedDelays);
    }
}