package com.stochastic.dao;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.StringTokenizer;

import com.stochastic.solver.SubSolverWrapper;

public class InputsDAO
{
    /**
     * @param fileName
     */
    public static void  ReadData()
    {
         try
         {
           String strFileName = "Inputs.txt";
           BufferedReader in  = new BufferedReader(new FileReader(strFileName));

           String str;
           String[] readVal = new String[10];
//           int index = 0;
           while ((str = in.readLine()) != null)
           {
                StringTokenizer st = new StringTokenizer(str,",");
                int arrIndex = 0;

                if(st.countTokens() <= 0)
                	continue;

                while (st.hasMoreTokens())
                {
                    readVal[arrIndex] = st.nextToken();
                    arrIndex++;
                }              
                
                SubSolverWrapper.ScenarioData sd = new SubSolverWrapper.ScenarioData();
                sd.setSceNo(Integer.parseInt(readVal[0]));
                sd.setIter(Integer.parseInt(readVal[1]));                
                sd.setPathIndex(Integer.parseInt(readVal[2]));
                sd.setTailIndex(Integer.parseInt(readVal[3]));
                sd.setLegId(Integer.parseInt(readVal[4]));
                
                SubSolverWrapper.ScenarioData.dataStore.put(sd, Integer.parseInt(readVal[4])); //.addData(sNo, pIndex, tIndex, legId, duration);
                               
           }

           readVal = null;
           in.close();
  		   System.out.println("InputsDAO.txt - Read " + SubSolverWrapper.ScenarioData.dataStore.size());
         }
         catch (FileNotFoundException ex)
         {
           System.out.println("File InputsDAO.txt NOT FOUND");
           System.exit(0);
         }
         catch(Exception e)
         {
         	e.printStackTrace();
        	System.out.println("Error in InputsDAO");
            System.exit(0);
         }
    }
}