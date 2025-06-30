package org.sbpo2025.challenge;

import org.apache.commons.lang3.time.StopWatch;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ChallengeSolver {
    private final long MAX_RUNTIME = 600000; // milliseconds; 10 minutes

    protected List<Map<Integer, Integer>> orders;
    protected List<Map<Integer, Integer>> aisles;
    protected int nItems;
    protected int waveSizeLB;
    protected int waveSizeUB;

    public ChallengeSolver(
            List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;
    }

    public ChallengeSolution solve(StopWatch stopWatch) {
        // Implement your solution here
        return null;
    }

    /*
     * Get the remaining time in seconds
     */
    protected long getRemainingTime(StopWatch stopWatch) {
        return Math.max(
                TimeUnit.SECONDS.convert(MAX_RUNTIME - stopWatch.getTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS),
                0);
    }

    protected boolean isSolutionFeasible(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.orders();
        Set<Integer> visitedAisles = challengeSolution.aisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return false;
        }

        int[] totalUnitsPicked = new int[nItems];
        int[] totalUnitsAvailable = new int[nItems];

        // Calculate total units picked
        for (int order : selectedOrders) {
            for (Map.Entry<Integer, Integer> entry : orders.get(order).entrySet()) {
                totalUnitsPicked[entry.getKey()] += entry.getValue();
            }
        }

        // Calculate total units available
        for (int aisle : visitedAisles) {
            for (Map.Entry<Integer, Integer> entry : aisles.get(aisle).entrySet()) {
                totalUnitsAvailable[entry.getKey()] += entry.getValue();
            }
        }

        // Check if the total units picked are within bounds
        int totalUnits = Arrays.stream(totalUnitsPicked).sum();
        if (totalUnits < waveSizeLB || totalUnits > waveSizeUB) {
            return false;
        }

        // Check if the units picked do not exceed the units available
        for (int i = 0; i < nItems; i++) {
            if (totalUnitsPicked[i] > totalUnitsAvailable[i]) {
                return false;
            }
        }

        return true;
    }

    protected double computeObjectiveFunction(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.orders();
        Set<Integer> visitedAisles = challengeSolution.aisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return 0.0;
        }
        int totalUnitsPicked = 0;

        // Calculate total units picked
        for (int order : selectedOrders) {
            totalUnitsPicked += orders.get(order).values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        // Calculate the number of visited aisles
        int numVisitedAisles = visitedAisles.size();

        // Objective function: total units picked / number of visited aisles
        return (double) totalUnitsPicked / numVisitedAisles;
    }
}

/* 

comentario
package org.sbpo2025.challenge;

import org.apache.commons.lang3.time.StopWatch;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class ChallengeSolver {
    private final long MAX_RUNTIME = 60000; // milliseconds; 1 minute

    protected List<Map<Integer, Integer>> orders;
    protected List<Map<Integer, Integer>> aisles;
    protected int nItems;
    protected int waveSizeLB;
    protected int waveSizeUB;

    private Random random;

    public ChallengeSolver(
            List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;
        this.random = new Random();
    }

    public ChallengeSolution solve(StopWatch stopWatch) {
        ChallengeSolution currentSolution = generateInitialSolution();
        ChallengeSolution bestSolution = currentSolution;

        // Simple Simulated Annealing acceptance criterion
        double temperature = 100.0;
        double coolingRate = 0.995;

        while (getRemainingTime(stopWatch) > 0) {
            // Destroy phase
            ChallengeSolution destroyedSolution = destroy(currentSolution);

            // Repair phase
            ChallengeSolution newSolution = repair(destroyedSolution);

            if (newSolution != null && isSolutionFeasible(newSolution)) {
                double currentObjective = computeObjectiveFunction(currentSolution);
                double newObjective = computeObjectiveFunction(newSolution);

                if (newObjective > currentObjective) {
                    currentSolution = newSolution;
                } else {
                    // Acceptance with probability for worse solutions
                    if (random.nextDouble() < Math.exp((newObjective - currentObjective) / temperature)) {
                        currentSolution = newSolution;
                    }
                }

                if (newObjective > computeObjectiveFunction(bestSolution)) {
                    bestSolution = newSolution;
                }
            }
            temperature *= coolingRate;
        }

        return bestSolution;
    }

    private Set<Integer> calculateAislesForOrders(Set<Integer> currentOrders) {
        Set<Integer> newAisles = new HashSet<>();
        for (int orderIndex : currentOrders) {
            for (Map.Entry<Integer, Integer> itemEntry : orders.get(orderIndex).entrySet()) {
                int itemIndex = itemEntry.getKey();
                for (int aisle = 0; aisle < aisles.size(); aisle++) {
                    if (aisles.get(aisle).containsKey(itemIndex)) {
                        newAisles.add(aisle);
                    }
                }
            }
        }
        return newAisles;
    }

    private ChallengeSolution destroy(ChallengeSolution solution) {
        Set<Integer> currentOrders = new HashSet<>(solution.orders());
        
        // Determine how many orders to remove (e.g., 10% to 30%)
        int numOrdersToRemove = (int) (currentOrders.size() * (0.1 + random.nextDouble() * 0.2));
        numOrdersToRemove = Math.max(1, numOrdersToRemove); // Ensure at least one order is removed

        List<Integer> ordersList = new ArrayList<>(currentOrders);
        Collections.shuffle(ordersList, random);

        // Remove orders that contribute most to the number of aisles visited
        // or orders that have many unique items.
        // For simplicity, we\\\'ll stick to random removal for now, but this is a good area for improvement.

        for (int i = 0; i < numOrdersToRemove && i < ordersList.size(); i++) {
            currentOrders.remove(ordersList.get(i));
        }
        System.out.println("Destroyed solution: removed " + numOrdersToRemove + " orders.");
        Set<Integer> newAisles = calculateAislesForOrders(currentOrders);
        return new ChallengeSolution(currentOrders, newAisles);
    }

    private ChallengeSolution repair(ChallengeSolution solution) {
        Set<Integer> currentOrders = new HashSet<>(solution.orders());
        
        List<Integer> unselectedOrders = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            if (!currentOrders.contains(i)) {
                unselectedOrders.add(i);
            }
        }
        Collections.shuffle(unselectedOrders, random);

        // Try to add orders back or new orders
        int ordersAdded = 0;
        for (int orderIndex : unselectedOrders) {
            Set<Integer> tempOrders = new HashSet<>(currentOrders);
            tempOrders.add(orderIndex);

            int potentialTotalUnits = 0;
            for (int order : tempOrders) {
                potentialTotalUnits += orders.get(order).values().stream().mapToInt(Integer::intValue).sum();
            }

            if (potentialTotalUnits <= waveSizeUB) {
                Set<Integer> tempAisles = calculateAislesForOrders(tempOrders);
                ChallengeSolution candidateSolution = new ChallengeSolution(tempOrders, tempAisles);
                
                // Only accept if it\\\'s feasible and improves the objective or is accepted by SA
                if (isSolutionFeasible(candidateSolution)) {
                    currentOrders = tempOrders;
                    ordersAdded++;
                }
            }
        }
        System.out.println("Repaired solution: added " + ordersAdded + " orders.");
        Set<Integer> finalAisles = calculateAislesForOrders(currentOrders);
        return new ChallengeSolution(currentOrders, finalAisles);
    }

    /*
     * Get the remaining time in seconds
     */

    /*
    comentarios
    
    protected long getRemainingTime(StopWatch stopWatch) {
        return Math.max(
                TimeUnit.SECONDS.convert(MAX_RUNTIME - stopWatch.getTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS),
                0);
    }

    protected boolean isSolutionFeasible(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.orders();
        Set<Integer> visitedAisles = challengeSolution.aisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return false;
        }

        int[] totalUnitsPicked = new int[nItems];
        int[] totalUnitsAvailable = new int[nItems];

        // Calculate total units picked
        for (int order : selectedOrders) {
            for (Map.Entry<Integer, Integer> entry : orders.get(order).entrySet()) {
                totalUnitsPicked[entry.getKey()] += entry.getValue();
            }
        }

        // Calculate total units available
        for (int aisle : visitedAisles) {
            for (Map.Entry<Integer, Integer> entry : aisles.get(aisle).entrySet()) {
                totalUnitsAvailable[entry.getKey()] += entry.getValue();
            }
        }

        // Check if the total units picked are within bounds
        int totalUnits = Arrays.stream(totalUnitsPicked).sum();
        if (totalUnits < waveSizeLB || totalUnits > waveSizeUB) {
            return false;
        }

        // Check if the units picked do not exceed the units available
        for (int i = 0; i < nItems; i++) {
            if (totalUnitsPicked[i] > totalUnitsAvailable[i]) {
                return false;
            }
        }

        return true;
    }

    protected double computeObjectiveFunction(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.orders();
        Set<Integer> visitedAisles = challengeSolution.aisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return 0.0;
        }
        int totalUnitsPicked = 0;

        // Calculate total units picked
        for (int order : selectedOrders) {
            totalUnitsPicked += orders.get(order).values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        // Calculate the number of visited aisles
        int numVisitedAisles = visitedAisles.size();

        // Objective function: total units picked / number of visited aisles
        return (double) totalUnitsPicked / numVisitedAisles;
    }

    private ChallengeSolution generateInitialSolution() {
        Set<Integer> selectedOrders = new HashSet<>();
        int currentTotalUnits = 0;

        List<Integer> allOrders = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            allOrders.add(i);
        }
        Collections.shuffle(allOrders, random);

        // Try to add orders to meet the lower bound (waveSizeLB) and upper bound (waveSizeUB)
        for (int orderIndex : allOrders) {
            Set<Integer> tempOrders = new HashSet<>(selectedOrders);
            tempOrders.add(orderIndex);

            int unitsInThisOrder = orders.get(orderIndex).values().stream().mapToInt(Integer::intValue).sum();
            int potentialTotalUnits = currentTotalUnits + unitsInThisOrder;

            if (potentialTotalUnits <= waveSizeUB) {
                Set<Integer> potentialAisles = calculateAislesForOrders(tempOrders);
                ChallengeSolution candidateSolution = new ChallengeSolution(tempOrders, potentialAisles);

                // Check feasibility with the new order added
                if (isSolutionFeasible(candidateSolution)) {
                    selectedOrders = tempOrders;
                    currentTotalUnits = potentialTotalUnits;
                }
            }
        }

        // If after trying all orders, the solution is still not feasible (e.g., LB not met),
        // try to adjust by removing orders until it becomes feasible or return the best found.
        // This part can be more sophisticated, but for now, we\\\'ll rely on the LNS to improve it.
        if (!isSolutionFeasible(new ChallengeSolution(selectedOrders, calculateAislesForOrders(selectedOrders)))) {
            // If no feasible solution found by simple greedy, try to build one from scratch more carefully
            // or return an empty solution to be repaired by LNS.
            // For now, return an empty solution and let LNS try to build it.
            System.out.println("Initial solution not feasible. Returning empty solution.");
            return new ChallengeSolution(new HashSet<>(), new HashSet<>());
        }

        System.out.println("Initial solution generated with " + selectedOrders.size() + " orders and " + calculateAislesForOrders(selectedOrders).size() + " aisles.");
        return new ChallengeSolution(selectedOrders, calculateAislesForOrders(selectedOrders));
    }
}*/