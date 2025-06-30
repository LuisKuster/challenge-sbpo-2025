package org.sbpo2025.challenge;

import org.apache.commons.lang3.time.StopWatch;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ChallengeSolver {
    private final long MAX_RUNTIME = 600000; // 10 minutos
    private final double MIN_DESTROY_RATIO = 0.1;
    private final double MAX_DESTROY_RATIO = 0.3;
    private final int MAX_ITERATIONS_WITHOUT_IMPROVEMENT = 1000;

    protected final List<Map<Integer, Integer>> orders;
    protected final List<Map<Integer, Integer>> aisles;
    protected final int nItems;
    protected final int waveSizeLB;
    protected final int waveSizeUB;

    private final Random random;

    // Caches para otimização
    private final Map<Integer, Set<Integer>> orderToAislesCache;
    private final Map<Integer, Integer> orderToUnitsCache;
    private final Map<Set<Integer>, Set<Integer>> ordersToAislesCache;

    public ChallengeSolver(
            List<Map<Integer, Integer>> orders,
            List<Map<Integer, Integer>> aisles,
            int nItems,
            int waveSizeLB,
            int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;
        this.random = new Random();

        // Inicializar caches
        this.orderToAislesCache = new HashMap<>();
        this.orderToUnitsCache = new HashMap<>();
        this.ordersToAislesCache = new HashMap<>();

        precomputeOrderData();
    }

    /**
     * Pré-computa dados dos pedidos para otimização
     */
    private void precomputeOrderData() {
        for (int orderIndex = 0; orderIndex < orders.size(); orderIndex++) {
            Map<Integer, Integer> order = orders.get(orderIndex);

            // Cache das unidades totais do pedido
            int totalUnits = order.values().stream().mapToInt(Integer::intValue).sum();
            orderToUnitsCache.put(orderIndex, totalUnits);

            // Cache dos corredores necessários para cada pedido
            Set<Integer> requiredAisles = new HashSet<>();
            for (int itemIndex : order.keySet()) {
                for (int aisle = 0; aisle < aisles.size(); aisle++) {
                    if (aisles.get(aisle).containsKey(itemIndex)) {
                        requiredAisles.add(aisle);
                    }
                }
            }
            orderToAislesCache.put(orderIndex, requiredAisles);
        }
    }

    public ChallengeSolution solve(StopWatch stopWatch) {
        ChallengeSolution currentSolution = generateInitialSolution();
        ChallengeSolution bestSolution = currentSolution;

        // Se a solução inicial não for viável, começar com solução vazia
        if (!isSolutionFeasible(currentSolution)) {
            currentSolution = new ChallengeSolution(new HashSet<>(), new HashSet<>());
        }

        int iterationsWithoutImprovement = 0;
        double bestObjective = computeObjectiveFunction(bestSolution);

        while (getRemainingTime(stopWatch) > 0 && iterationsWithoutImprovement < MAX_ITERATIONS_WITHOUT_IMPROVEMENT) {
            // Fase de destruição com intensidade adaptativa
            double destroyRatio = MIN_DESTROY_RATIO +
                    (MAX_DESTROY_RATIO - MIN_DESTROY_RATIO) * (iterationsWithoutImprovement / (double) MAX_ITERATIONS_WITHOUT_IMPROVEMENT);

            ChallengeSolution destroyedSolution = destroy(currentSolution, destroyRatio);
            ChallengeSolution newSolution = repair(destroyedSolution);

            if (newSolution != null && isSolutionFeasible(newSolution)) {
                double currentObjective = computeObjectiveFunction(currentSolution);
                double newObjective = computeObjectiveFunction(newSolution);

                // Aceitação com critério de melhoria
                if (newObjective > currentObjective ||
                        (newObjective == currentObjective && newSolution.orders().size() > currentSolution.orders().size())) {

                    currentSolution = newSolution;
                    iterationsWithoutImprovement = 0;

                    if (newObjective > bestObjective) {
                        bestSolution = newSolution;
                        bestObjective = newObjective;
                    }
                } else {
                    iterationsWithoutImprovement++;
                }
            } else {
                iterationsWithoutImprovement++;
            }
        }

        return isSolutionFeasible(bestSolution) ? bestSolution :
                new ChallengeSolution(new HashSet<>(), new HashSet<>());
    }

    /**
     * Calcula os corredores necessários para um conjunto de pedidos (com cache)
     */
    private Set<Integer> calculateAislesForOrders(Set<Integer> orderSet) {
        // Verificar cache primeiro
        if (ordersToAislesCache.containsKey(orderSet)) {
            return new HashSet<>(ordersToAislesCache.get(orderSet));
        }

        Set<Integer> aisles = orderSet.stream()
                .map(orderToAislesCache::get)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        // Adicionar ao cache se o conjunto não for muito grande
        if (orderSet.size() <= 20) {
            ordersToAislesCache.put(new HashSet<>(orderSet), new HashSet<>(aisles));
        }

        return aisles;
    }

    /**
     * Fase de destruição melhorada com intensidade adaptativa
     */
    private ChallengeSolution destroy(ChallengeSolution solution, double destroyRatio) {
        Set<Integer> currentOrders = new HashSet<>(solution.orders());

        if (currentOrders.isEmpty()) {
            return solution;
        }

        // Estratégias de destruição alternadas
        boolean useDispersionStrategy = random.nextBoolean();

        if (useDispersionStrategy) {
            return destroyByDispersion(currentOrders, destroyRatio);
        } else {
            return destroyByEfficiency(currentOrders, destroyRatio);
        }
    }

    /**
     * Destruição baseada na dispersão pelos corredores
     */
    private ChallengeSolution destroyByDispersion(Set<Integer> currentOrders, double destroyRatio) {
        // Ordenar por número de corredores (mais dispersos primeiro)
        List<Integer> sortedOrders = currentOrders.stream()
                .sorted((o1, o2) -> {
                    int aisles1 = orderToAislesCache.get(o1).size();
                    int aisles2 = orderToAislesCache.get(o2).size();
                    return Integer.compare(aisles2, aisles1); // Decrescente
                })
                .collect(Collectors.toList());

        int numToRemove = Math.max(1, (int) (currentOrders.size() * destroyRatio));

        Set<Integer> remainingOrders = new HashSet<>(currentOrders);
        for (int i = 0; i < numToRemove && i < sortedOrders.size(); i++) {
            remainingOrders.remove(sortedOrders.get(i));
        }

        return new ChallengeSolution(remainingOrders, calculateAislesForOrders(remainingOrders));
    }

    /**
     * Destruição baseada na eficiência (unidades/corredor)
     */
    private ChallengeSolution destroyByEfficiency(Set<Integer> currentOrders, double destroyRatio) {
        List<Integer> sortedOrders = currentOrders.stream()
                .sorted((o1, o2) -> {
                    double eff1 = (double) orderToUnitsCache.get(o1) / orderToAislesCache.get(o1).size();
                    double eff2 = (double) orderToUnitsCache.get(o2) / orderToAislesCache.get(o2).size();
                    return Double.compare(eff1, eff2); // Crescente - remove menos eficientes
                })
                .collect(Collectors.toList());

        int numToRemove = Math.max(1, (int) (currentOrders.size() * destroyRatio));

        Set<Integer> remainingOrders = new HashSet<>(currentOrders);
        for (int i = 0; i < numToRemove && i < sortedOrders.size(); i++) {
            remainingOrders.remove(sortedOrders.get(i));
        }

        return new ChallengeSolution(remainingOrders, calculateAislesForOrders(remainingOrders));
    }

    /**
     * Fase de reparação otimizada
     */
    private ChallengeSolution repair(ChallengeSolution solution) {
        final Set<Integer> initialOrders = new HashSet<>(solution.orders());
        final Set<Integer> currentAisles = calculateAislesForOrders(initialOrders);

        Set<Integer> workingOrders = new HashSet<>(initialOrders); // variável de trabalho
        int currentTotalUnits = initialOrders.stream()
                .mapToInt(orderToUnitsCache::get)
                .sum();

        // Pedidos não selecionados
        List<Integer> unselectedOrders = IntStream.range(0, orders.size())
                .filter(i -> !initialOrders.contains(i)) // usando initialOrders (final)
                .boxed()
                .collect(Collectors.toList());

        // Ordenação melhorada por score
        unselectedOrders.sort((o1, o2) -> {
            double score1 = calculateRepairScore(o1, currentAisles);
            double score2 = calculateRepairScore(o2, currentAisles);
            return Double.compare(score2, score1); // Decrescente
        });

        // Adicionar pedidos greedily
        for (int orderIndex : unselectedOrders) {
            int orderUnits = orderToUnitsCache.get(orderIndex);

            if (currentTotalUnits + orderUnits <= waveSizeUB) {
                Set<Integer> tempOrders = new HashSet<>(workingOrders);
                tempOrders.add(orderIndex);

                Set<Integer> tempAisles = calculateAislesForOrders(tempOrders);
                ChallengeSolution candidate = new ChallengeSolution(tempOrders, tempAisles);

                if (isSolutionFeasible(candidate)) {
                    workingOrders = tempOrders; // atualiza a variável de trabalho
                    currentTotalUnits += orderUnits;
                }
            }
        }

        return new ChallengeSolution(workingOrders, calculateAislesForOrders(workingOrders));
    }

    /**
     * Calcula score para reparação considerando múltiplos fatores
     */
    private double calculateRepairScore(int orderIndex, Set<Integer> currentAisles) {
        int units = orderToUnitsCache.get(orderIndex);
        Set<Integer> orderAisles = orderToAislesCache.get(orderIndex);

        Set<Integer> newAisles = new HashSet<>(orderAisles);
        newAisles.removeAll(currentAisles);

        int newAisleCount = newAisles.size();
        int sharedAisleCount = orderAisles.size() - newAisleCount;

        // Score considera: unidades, novos corredores penalizam, corredores compartilhados bonificam
        return (double) units / Math.max(1, newAisleCount) + sharedAisleCount * 0.5;
    }

    /**
     * Geração de solução inicial melhorada
     */
    private ChallengeSolution generateInitialSolution() {
        // Tentar múltiplas estratégias de construção inicial
        List<ChallengeSolution> candidates = new ArrayList<>();

        // Estratégia 1: Greedy por eficiência
        candidates.add(generateGreedyByEfficiency());

        // Estratégia 2: Greedy por unidades
        candidates.add(generateGreedyByUnits());

        // Estratégia 3: Random
        candidates.add(generateRandomSolution());

        // Retornar a melhor solução viável
        return candidates.stream()
                .filter(this::isSolutionFeasible)
                .max(Comparator.comparingDouble(this::computeObjectiveFunction))
                .orElse(new ChallengeSolution(new HashSet<>(), new HashSet<>()));
    }

    private ChallengeSolution generateGreedyByEfficiency() {
        List<Integer> ordersByEfficiency = IntStream.range(0, orders.size())
                .boxed()
                .sorted((o1, o2) -> {
                    double eff1 = (double) orderToUnitsCache.get(o1) / orderToAislesCache.get(o1).size();
                    double eff2 = (double) orderToUnitsCache.get(o2) / orderToAislesCache.get(o2).size();
                    return Double.compare(eff2, eff1);
                })
                .collect(Collectors.toList());

        return buildSolutionFromOrderedList(ordersByEfficiency);
    }

    private ChallengeSolution generateGreedyByUnits() {
        List<Integer> ordersByUnits = IntStream.range(0, orders.size())
                .boxed()
                .sorted((o1, o2) -> Integer.compare(orderToUnitsCache.get(o2), orderToUnitsCache.get(o1)))
                .collect(Collectors.toList());

        return buildSolutionFromOrderedList(ordersByUnits);
    }

    private ChallengeSolution generateRandomSolution() {
        List<Integer> randomOrders = IntStream.range(0, orders.size())
                .boxed()
                .collect(Collectors.toList());
        Collections.shuffle(randomOrders, random);

        return buildSolutionFromOrderedList(randomOrders);
    }

    private ChallengeSolution buildSolutionFromOrderedList(List<Integer> orderedOrders) {
        Set<Integer> selectedOrders = new HashSet<>();
        int totalUnits = 0;

        for (int orderIndex : orderedOrders) {
            int orderUnits = orderToUnitsCache.get(orderIndex);

            if (totalUnits + orderUnits <= waveSizeUB) {
                Set<Integer> tempOrders = new HashSet<>(selectedOrders);
                tempOrders.add(orderIndex);

                Set<Integer> tempAisles = calculateAislesForOrders(tempOrders);
                ChallengeSolution candidate = new ChallengeSolution(tempOrders, tempAisles);

                if (isSolutionFeasible(candidate)) {
                    selectedOrders = tempOrders;
                    totalUnits += orderUnits;
                }
            }
        }

        return new ChallengeSolution(selectedOrders, calculateAislesForOrders(selectedOrders));
    }

    // Métodos auxiliares mantidos com otimizações menores
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

        // Calcular unidades demandadas
        for (int order : selectedOrders) {
            orders.get(order).forEach((item, quantity) -> totalUnitsPicked[item] += quantity);
        }

        // Calcular unidades disponíveis
        for (int aisle : visitedAisles) {
            aisles.get(aisle).forEach((item, quantity) -> totalUnitsAvailable[item] += quantity);
        }

        // Verificar limites de wave
        int totalUnits = Arrays.stream(totalUnitsPicked).sum();
        if (totalUnits < waveSizeLB || totalUnits > waveSizeUB) {
            return false;
        }

        // Verificar disponibilidade de itens
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

        int totalUnitsPicked = selectedOrders.stream()
                .mapToInt(orderToUnitsCache::get)
                .sum();

        return (double) totalUnitsPicked / Math.max(1, visitedAisles.size());
    }
}