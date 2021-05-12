package stochastic.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stochastic.domain.Leg;
import stochastic.output.RescheduleSolution;
import stochastic.registry.Parameters;
import stochastic.utility.CSVHelper;
import stochastic.utility.OptException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Builds a RescheduleSolution object from a given csv file.
 */
public class RescheduleSolutionDAO {
    private final static Logger logger = LogManager.getLogger(RescheduleSolutionDAO.class);
    private final RescheduleSolution rescheduleSolution;

    public RescheduleSolutionDAO(String modelName, ArrayList<Leg> legs) throws OptException {
        try {
            // Collect reschedule values.
            String filePath = Parameters.getOutputPath() + "/reschedule_solution_" + modelName +
                ".csv";
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            Map<Integer, Integer> legIdRescheduleMap = new HashMap<>();
            CSVHelper.parseLine(reader); // parse once to skip headers
            while (true) {
                List<String> line = CSVHelper.parseLine(reader);
                if (line == null)
                    break;

                Integer legId = Integer.parseInt(line.get(0));
                Integer reschedule = Integer.parseInt(line.get(2));
                legIdRescheduleMap.put(legId, reschedule);
            }
            reader.close();

            // Build reschedule list and cost.
            double rescheduleCost = 0;
            int[] reschedules = new int[legs.size()];
            for (Leg leg : legs) {
                int rescheduleTime = legIdRescheduleMap.getOrDefault(leg.getId(), 0);
                reschedules[leg.getIndex()] = rescheduleTime;
                rescheduleCost += (rescheduleTime * leg.getRescheduleCostPerMin());
            }
            rescheduleSolution = new RescheduleSolution(modelName, rescheduleCost, reschedules);
        } catch (IOException ex) {
            logger.error(ex);

            throw new OptException("error parsing reschedule solution csv");
        }
    }

    public RescheduleSolution getRescheduleSolution() {
        return rescheduleSolution;
    }
}
