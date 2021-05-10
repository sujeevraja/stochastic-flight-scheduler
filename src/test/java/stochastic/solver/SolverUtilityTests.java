package stochastic.solver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import stochastic.domain.Leg;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SolverUtilityTests {
    @Test
    @DisplayName("nonzero connection slack should be computed correctly")
    void testNonZeroSlack() {
        Leg prevLeg = new Leg(
            229504572,
            1,
            100,
            101,
            70,
            10000,
            ZonedDateTime.parse("2017-03-20T11:55:00.000Z").toEpochSecond() / 60,
            ZonedDateTime.parse("2017-03-20T14:54:00.000Z").toEpochSecond() / 60
        );
        Leg nextLeg = new Leg(
            229503817,
            2,
            101,
            102,
            50,
            prevLeg.getOrigTailId(),
            ZonedDateTime.parse("2017-03-20T17:50:00.000Z").toEpochSecond() / 60,
            ZonedDateTime.parse("2017-03-20T21:35:00.000Z").toEpochSecond() / 60
        );
        assertEquals(106, SolverUtility.getSlackInMin(prevLeg, nextLeg));
    }
}
