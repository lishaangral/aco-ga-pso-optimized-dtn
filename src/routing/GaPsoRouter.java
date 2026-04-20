package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

/**
 * Ablation Study: GA + PSO router for THE ONE (No ACO).
 *
 * This "Amnesia" model lacks historical memory. 
 * Initial candidates are scored using pure Geographic greedy forwarding.
 * GA: diversifies candidate ranking.
 * PSO: adaptive replication pressure and soft score nudging.
 */
public class GaPsoRouter extends ActiveRouter {

    public static final String OMEGA1_S = "omega1";
    public static final String OMEGA2_S = "omega2";

    public static final String GA_POPULATION_SIZE_S = "gaPopulationSize";
    public static final String GA_CROSSOVER_RATE_S = "gaCrossoverRate";
    public static final String GA_MUTATION_RATE_S = "gaMutationRate";
    public static final String EXPLORE_BOOST_S = "exploreBoost";

    public static final String PSO_INERTIA_S = "psoInertia";
    public static final String PSO_C1_S = "psoC1";
    public static final String PSO_C2_S = "psoC2";
    public static final String VELOCITY_CLAMP_S = "velocityClamp";

    public static final String INITIAL_COPIES_S = "initialCopies";
    public static final String MIN_COPIES_S = "minCopies";
    public static final String MAX_COPIES_S = "maxCopies";
    public static final String CONGESTION_THRESHOLD_S = "congestionThreshold";
    public static final String TTL_URGENCY_THRESHOLD_S = "ttlUrgencyThreshold";
    public static final String RESCUE_URGENCY_THRESHOLD_S = "rescueUrgencyThreshold";
    public static final String STRONG_RELAY_THRESHOLD_S = "strongRelayThreshold";
    public static final String LINK_SPEED_NORM_S = "linkSpeedNorm";

    public static final String CLASS_BOUNDARY_1_S = "classBoundary1";
    public static final String CLASS_BOUNDARY_2_S = "classBoundary2";
    public static final String CLASS_BOUNDARY_3_S = "classBoundary3";

    private static final String MSG_COPIES_PROP = "GaPsoRouter.copies";
    private static final String MSG_CLASS_MASK_PROP = "GaPsoRouter.classMask";

    private double omega1;
    private double omega2;

    private int gaPopulationSize;
    private double gaCrossoverRate;
    private double gaMutationRate;
    private double exploreBoost;

    private double psoInertia;
    private double psoC1;
    private double psoC2;
    private double velocityClamp;

    private int initialCopies;
    private int minCopies;
    private int maxCopies;
    private double congestionThreshold;
    private double ttlUrgencyThreshold;
    private double rescueUrgencyThreshold;
    private double strongRelayThreshold;
    private double linkSpeedNorm;

    private int classBoundary1;
    private int classBoundary2;
    private int classBoundary3;

    private Map<String, Double> scoreVelocities;
    private Map<String, Double> scorePersonalBests;
    private Map<String, Double> scoreGlobalBests;

    private Map<String, Double> copyVelocities;
    private Map<String, Double> copyPersonalBests;
    private Map<String, Double> copyGlobalBests;

    private Map<Integer, Integer> pendingSenderCopies;
    private Map<Integer, Integer> pendingOldClassMasks;
    private Set<String> deliveredIds;

    private Random rng;

    public GaPsoRouter(Settings s) {
        super(s);

        this.omega1 = getDoubleOrDefault(s, OMEGA1_S, 0.55);
        this.omega2 = getDoubleOrDefault(s, OMEGA2_S, 0.60);

        this.gaPopulationSize = getIntOrDefault(s, GA_POPULATION_SIZE_S, 8);
        this.gaCrossoverRate = getDoubleOrDefault(s, GA_CROSSOVER_RATE_S, 0.78);
        this.gaMutationRate = getDoubleOrDefault(s, GA_MUTATION_RATE_S, 0.12);
        this.exploreBoost = getDoubleOrDefault(s, EXPLORE_BOOST_S, 0.24);

        this.psoInertia = getDoubleOrDefault(s, PSO_INERTIA_S, 0.65);
        this.psoC1 = getDoubleOrDefault(s, PSO_C1_S, 1.55);
        this.psoC2 = getDoubleOrDefault(s, PSO_C2_S, 1.55);
        this.velocityClamp = getDoubleOrDefault(s, VELOCITY_CLAMP_S, 0.35);

        this.initialCopies = getIntOrDefault(s, INITIAL_COPIES_S, 12);
        this.minCopies = getIntOrDefault(s, MIN_COPIES_S, 4);
        this.maxCopies = getIntOrDefault(s, MAX_COPIES_S, 16);
        this.congestionThreshold = getDoubleOrDefault(s, CONGESTION_THRESHOLD_S, 0.85);
        this.ttlUrgencyThreshold = getDoubleOrDefault(s, TTL_URGENCY_THRESHOLD_S, 0.25);
        this.rescueUrgencyThreshold = getDoubleOrDefault(s, RESCUE_URGENCY_THRESHOLD_S, 0.65);
        this.strongRelayThreshold = getDoubleOrDefault(s, STRONG_RELAY_THRESHOLD_S, 0.72);
        this.linkSpeedNorm = getDoubleOrDefault(s, LINK_SPEED_NORM_S, 250000.0);

        this.classBoundary1 = getIntOrDefault(s, CLASS_BOUNDARY_1_S, 80);
        this.classBoundary2 = getIntOrDefault(s, CLASS_BOUNDARY_2_S, 90);
        this.classBoundary3 = getIntOrDefault(s, CLASS_BOUNDARY_3_S, 100);

        initState();
    }

    protected GaPsoRouter(GaPsoRouter r) {
        super(r);

        this.omega1 = r.omega1;
        this.omega2 = r.omega2;

        this.gaPopulationSize = r.gaPopulationSize;
        this.gaCrossoverRate = r.gaCrossoverRate;
        this.gaMutationRate = r.gaMutationRate;
        this.exploreBoost = r.exploreBoost;

        this.psoInertia = r.psoInertia;
        this.psoC1 = r.psoC1;
        this.psoC2 = r.psoC2;
        this.velocityClamp = r.velocityClamp;

        this.initialCopies = r.initialCopies;
        this.minCopies = r.minCopies;
        this.maxCopies = r.maxCopies;
        this.congestionThreshold = r.congestionThreshold;
        this.ttlUrgencyThreshold = r.ttlUrgencyThreshold;
        this.rescueUrgencyThreshold = r.rescueUrgencyThreshold;
        this.strongRelayThreshold = r.strongRelayThreshold;
        this.linkSpeedNorm = r.linkSpeedNorm;

        this.classBoundary1 = r.classBoundary1;
        this.classBoundary2 = r.classBoundary2;
        this.classBoundary3 = r.classBoundary3;

        initState();
    }

    private void initState() {
        this.scoreVelocities = new HashMap<String, Double>();
        this.scorePersonalBests = new HashMap<String, Double>();
        this.scoreGlobalBests = new HashMap<String, Double>();

        this.copyVelocities = new HashMap<String, Double>();
        this.copyPersonalBests = new HashMap<String, Double>();
        this.copyGlobalBests = new HashMap<String, Double>();

        this.pendingSenderCopies = new HashMap<Integer, Integer>();
        this.pendingOldClassMasks = new HashMap<Integer, Integer>();
        this.deliveredIds = new HashSet<String>();

        this.rng = new Random(19);
    }

    @Override
    public void init(DTNHost host, List mListeners) {
        super.init(host, mListeners);
        this.rng = new Random(host.getAddress() * 2654435761L + 31L);
    }

    @Override
    public boolean createNewMessage(Message m) {
        makeRoomForNewMessage(m.getSize());
        ensureCopiesProperty(m, this.initialCopies);
        ensureClassMaskProperty(m, 1 << getMobilityClass(getHost()));
        return super.createNewMessage(m);
    }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        DTNHost other = con.getOtherNode(getHost());

        if (con.isUp()) {
            if (other.getRouter() instanceof GaPsoRouter) {
                syncDelivered((GaPsoRouter) other.getRouter());
            }
        }
    }

    @Override
    public void update() {
        super.update();
        purgeDeliveredLocally();

        if (!canStartTransfer() || isTransferring()) {
            return;
        }

        if (exchangeDeliverableMessages() != null) {
            return;
        }

        HybridChoice choice = findBestHybridChoice();
        if (choice != null) {
            startHybridTransfer(choice);
        }
    }

    private HybridChoice findBestHybridChoice() {
        HybridChoice best = null;
        double congestion = clamp(getHost().getBufferOccupancy() / 100.0);
        boolean hostCongested = congestion >= this.congestionThreshold;

        Collection<Message> msgs = getMessageCollection();
        for (Message m : msgs) {
            if (m.getTo() == getHost()) {
                continue;
            }
            if (this.deliveredIds.contains(m.getId())) {
                continue;
            }

            List<Candidate> candidates = collectCandidates(m);
            if (candidates.isEmpty()) {
                continue;
            }

            double ttlUrgency = getTtlUrgency(m);
            boolean fullHybrid = hostCongested || ttlUrgency >= this.ttlUrgencyThreshold || getCopies(m) > 1;
            HybridChoice current = fullHybrid ? selectByGaPso(m, candidates, congestion) : selectByGeoOnly(m, candidates, congestion);

            if (current != null && (best == null || current.utility > best.utility)) {
                best = current;
            }
        }

        return best;
    }

    private List<Candidate> collectCandidates(Message m) {
        List<Candidate> candidates = new ArrayList<Candidate>();
        double selfDistance = distance(getHost(), m.getTo());
        int classMask = getClassMask(m);

        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());

            if (other == m.getTo()) {
                continue;
            }
            if (other.getRouter().hasMessage(m.getId())) {
                continue;
            }
            if (other.getRouter() instanceof ActiveRouter) {
                if (((ActiveRouter) other.getRouter()).isTransferring()) {
                    continue;
                }
            }

            Candidate c = new Candidate();
            c.message = m;
            c.connection = con;
            c.other = other;
            c.classId = getMobilityClass(other);
            c.classNovelty = ((classMask & (1 << c.classId)) == 0) ? 1.0 : 0.0;
            c.distanceProgress = computeDistanceProgress(selfDistance, distance(other, m.getTo()));
            c.bufferHeadroom = 1.0 - clamp(other.getBufferOccupancy() / 100.0);
            c.linkQuality = clamp(con.getSpeed() / this.linkSpeedNorm);
            c.ttlUrgency = getTtlUrgency(m);
            
            // Replaced ACO with pure Geographic Greedy Initialization
            c.baseScore = computeGeoScore(c);
            candidates.add(c);
        }

        return candidates;
    }

    private HybridChoice selectByGeoOnly(Message m, List<Candidate> candidates, double congestion) {
        Candidate best = null;
        for (Candidate c : candidates) {
            c.gaScore = c.baseScore;
            c.psoScore = c.baseScore;
            c.finalScore = c.baseScore;
            if (best == null || c.finalScore > best.finalScore) {
                best = c;
            }
        }
        if (best == null) {
            return null;
        }

        HybridChoice choice = new HybridChoice();
        choice.message = m;
        choice.connection = best.connection;
        choice.other = best.other;
        choice.classNovelty = best.classNovelty;
        choice.finalScore = best.finalScore;
        choice.copyPressure = computeCopyPressure(m, candidates, congestion, best.finalScore);
        choice.utility = choice.finalScore + 0.15 * choice.copyPressure;
        return choice;
    }

    private HybridChoice selectByGaPso(Message m, List<Candidate> candidates, double congestion) {
        Collections.sort(candidates, new Comparator<Candidate>() {
            public int compare(Candidate a, Candidate b) {
                return (a.baseScore > b.baseScore) ? -1 : ((a.baseScore < b.baseScore) ? 1 : 0);
            }
        });

        int popSize = Math.min(this.gaPopulationSize, candidates.size());
        List<Candidate> population = new ArrayList<Candidate>(candidates.subList(0, popSize));

        applyGa(population);
        applyCandidatePso(m, population);

        Candidate best = null;
        for (Candidate c : population) {
            double gaBlend = (1.0 - this.omega1) * c.baseScore + this.omega1 * c.gaScore;
            c.finalScore = clamp((1.0 - this.omega2) * gaBlend + this.omega2 * c.psoScore);
            if (best == null || c.finalScore > best.finalScore) {
                best = c;
            }
        }
        if (best == null) {
            return null;
        }

        HybridChoice choice = new HybridChoice();
        choice.message = m;
        choice.connection = best.connection;
        choice.other = best.other;
        choice.classNovelty = best.classNovelty;
        choice.finalScore = best.finalScore;
        choice.copyPressure = computeCopyPressure(m, population, congestion, best.finalScore);
        choice.utility = choice.finalScore + 0.20 * choice.copyPressure + 0.05 * best.classNovelty;
        return choice;
    }

    // Pure Geographic/Greedy Score (No ACO Memory)
    private double computeGeoScore(Candidate c) {
        double score = 0.70 * c.distanceProgress + 0.15 * c.bufferHeadroom + 0.15 * c.linkQuality;
        return clamp(score + 0.05 * c.ttlUrgency * c.distanceProgress);
    }

    private void applyGa(List<Candidate> population) {
        if (population.isEmpty()) {
            return;
        }

        Candidate parentA = population.get(0);
        Candidate parentB = population.get(population.size() > 1 ? 1 : 0);
        boolean sameClassTop = parentA.classId == parentB.classId;

        // Adjusted offspring crossover to rely on distance instead of historical predictability/stability
        double offspring = this.gaCrossoverRate * (
                0.50 * parentA.baseScore +
                0.20 * parentB.baseScore +
                0.20 * parentA.classNovelty +
                0.10 * parentB.bufferHeadroom)
                + (1.0 - this.gaCrossoverRate) * (0.60 * parentA.distanceProgress + 0.40 * parentB.distanceProgress);
        offspring = clamp(offspring);

        for (Candidate c : population) {
            double inherited = 0.60 * c.baseScore + 0.25 * offspring + 0.15 * c.classNovelty;
            c.gaScore = clamp(inherited);

            double adaptiveMutation = this.gaMutationRate;
            if (sameClassTop) {
                adaptiveMutation += 0.05;
            }
            adaptiveMutation += 0.05 * (1.0 - c.baseScore);

            if (this.rng.nextDouble() < adaptiveMutation) {
                // Mutation now heavily offsets poor distance progress
                double mutation = this.exploreBoost * (0.60 * (1.0 - c.distanceProgress) + 0.40 * c.classNovelty);
                mutation *= (0.65 + 0.35 * this.rng.nextDouble());
                c.gaScore = clamp(c.gaScore + mutation);
            }
        }
    }

    private void applyCandidatePso(Message m, List<Candidate> population) {
        String destinationKey = Integer.toString(m.getTo().getAddress());
        double currentBest = 0.0;

        for (Candidate c : population) {
            if (c.gaScore > currentBest) {
                currentBest = c.gaScore;
            }
        }

        Double global = this.scoreGlobalBests.get(destinationKey);
        if (global == null || currentBest > global.doubleValue()) {
            global = Double.valueOf(currentBest);
            this.scoreGlobalBests.put(destinationKey, global);
        }

        for (Candidate c : population) {
            String particleKey = destinationKey + "@" + c.other.getAddress();
            double current = c.gaScore;

            Double pbest = this.scorePersonalBests.get(particleKey);
            if (pbest == null || current > pbest.doubleValue()) {
                pbest = Double.valueOf(current);
                this.scorePersonalBests.put(particleKey, pbest);
            }

            Double oldVelocity = this.scoreVelocities.get(particleKey);
            if (oldVelocity == null) {
                oldVelocity = Double.valueOf(0.0);
            }

            double r1 = this.rng.nextDouble();
            double r2 = this.rng.nextDouble();
            double velocity = this.psoInertia * oldVelocity.doubleValue()
                    + this.psoC1 * r1 * (pbest.doubleValue() - current)
                    + this.psoC2 * r2 * (global.doubleValue() - current);
            velocity = clampSigned(velocity, this.velocityClamp);
            this.scoreVelocities.put(particleKey, Double.valueOf(velocity));
            c.psoScore = clamp(current + velocity + 0.04 * c.classNovelty);
        }
    }

    private double computeCopyPressure(Message m, List<Candidate> population, double congestion, double bestScore) {
        double ttlUrgency = getTtlUrgency(m);
        double diversity = getTopClassDiversity(population);
        double distanceProg = getTopDistanceProgress(population); // Swapped out encounter strength for distance progress
        double novelty = getTopNovelty(population);

        double desired = 0.28
                + 0.30 * ttlUrgency
                + 0.22 * (1.0 - bestScore)
                + 0.12 * diversity
                + 0.08 * distanceProg
                + 0.08 * novelty
                - 0.20 * congestion;
        desired = clamp(desired);

        String destinationKey = Integer.toString(m.getTo().getAddress());
        Double pbest = this.copyPersonalBests.get(destinationKey);
        if (pbest == null || desired > pbest.doubleValue()) {
            pbest = Double.valueOf(desired);
            this.copyPersonalBests.put(destinationKey, pbest);
        }

        Double gbest = this.copyGlobalBests.get(destinationKey);
        if (gbest == null || desired > gbest.doubleValue()) {
            gbest = Double.valueOf(desired);
            this.copyGlobalBests.put(destinationKey, Double.valueOf(desired));
        }

        Double oldVelocity = this.copyVelocities.get(destinationKey);
        if (oldVelocity == null) {
            oldVelocity = Double.valueOf(0.0);
        }

        double r1 = this.rng.nextDouble();
        double r2 = this.rng.nextDouble();
        double velocity = this.psoInertia * oldVelocity.doubleValue()
                + this.psoC1 * r1 * (pbest.doubleValue() - desired)
                + this.psoC2 * r2 * (gbest.doubleValue() - desired);
        velocity = clampSigned(velocity, this.velocityClamp);
        this.copyVelocities.put(destinationKey, Double.valueOf(velocity));

        double pressure = clamp(desired + velocity);
        if (ttlUrgency >= this.rescueUrgencyThreshold) {
            pressure = Math.max(pressure, 0.72);
        }
        return pressure;
    }

    private void startHybridTransfer(HybridChoice choice) {
        Message m = choice.message;
        Connection con = choice.connection;
        DTNHost other = choice.other;
        int originalCopies = getCopies(m);
        int originalClassMask = getClassMask(m);

        if (other != m.getTo()) {
            if (originalCopies <= 1) {
                return;
            }

            int receiverCopies = decideReceiverCopies(m, originalCopies, choice.copyPressure, choice.finalScore, choice.classNovelty);
            receiverCopies = Math.max(1, Math.min(receiverCopies, originalCopies - 1));
            int senderCopies = Math.max(1, originalCopies - receiverCopies);

            updateCopies(m, receiverCopies);
            updateClassMask(m, originalClassMask | (1 << getMobilityClass(other)));
            this.pendingSenderCopies.put(Integer.valueOf(m.getUniqueId()), Integer.valueOf(senderCopies));
            this.pendingOldClassMasks.put(Integer.valueOf(m.getUniqueId()), Integer.valueOf(originalClassMask));
        }

        int ret = startTransfer(m, con);
        if (ret != RCV_OK) {
            restoreAfterFailedStart(m, originalCopies, originalClassMask);
            return;
        }
    }

    private int decideReceiverCopies(Message m, int currentCopies, double copyPressure, double finalScore, double novelty) {
        double ttlUrgency = getTtlUrgency(m);

        if (ttlUrgency >= this.rescueUrgencyThreshold || finalScore < this.strongRelayThreshold) {
            return (int) Math.ceil(currentCopies * 0.65);
        }
        if (copyPressure >= 0.75) {
            return (int) Math.ceil(currentCopies * 0.55);
        }
        if (copyPressure >= 0.45) {
            if (novelty > 0.5 && currentCopies > 4) {
                return Math.max(2, (int) Math.ceil(currentCopies * 0.35));
            }
            return Math.max(1, (int) Math.ceil(currentCopies * 0.35));
        }
        if (novelty > 0.5 && currentCopies > 3) {
            return 2;
        }
        return 1;
    }

    private void restoreAfterFailedStart(Message m, int originalCopies, int originalClassMask) {
        this.pendingSenderCopies.remove(Integer.valueOf(m.getUniqueId()));
        this.pendingOldClassMasks.remove(Integer.valueOf(m.getUniqueId()));
        if (hasMessage(m.getId())) {
            Message local = getMessage(m.getId());
            if (local != null) {
                updateCopies(local, originalCopies);
                updateClassMask(local, originalClassMask);
            }
        }
    }

    @Override
    protected void transferDone(Connection con) {
        Message sent = con.getMessage();
        if (sent == null) {
            return;
        }

        DTNHost other = con.getOtherNode(getHost());
        Integer senderCopies = this.pendingSenderCopies.remove(Integer.valueOf(sent.getUniqueId()));
        this.pendingOldClassMasks.remove(Integer.valueOf(sent.getUniqueId()));

        if (other == sent.getTo()) {
            this.deliveredIds.add(sent.getId());
            if (hasMessage(sent.getId())) {
                deleteMessage(sent.getId(), false);
            }
            return;
        }

        if (senderCopies != null && hasMessage(sent.getId())) {
            Message local = getMessage(sent.getId());
            if (local != null) {
                updateCopies(local, senderCopies.intValue());
            }
        }
    }

    @Override
    protected void transferAborted(Connection con) {
        Message sent = con.getMessage();
        if (sent == null) {
            return;
        }

        Integer senderCopies = this.pendingSenderCopies.remove(Integer.valueOf(sent.getUniqueId()));
        Integer oldMask = this.pendingOldClassMasks.remove(Integer.valueOf(sent.getUniqueId()));
        if (senderCopies == null) {
            return;
        }

        int receiverCopies = getCopies(sent);
        int restored = senderCopies.intValue() + receiverCopies;

        if (hasMessage(sent.getId())) {
            Message local = getMessage(sent.getId());
            if (local != null) {
                updateCopies(local, restored);
                if (oldMask != null) {
                    updateClassMask(local, oldMask.intValue());
                }
            }
        }
    }

    private void syncDelivered(GaPsoRouter other) {
        if (other == null) {
            return;
        }
        if (!this.deliveredIds.isEmpty()) {
            other.deliveredIds.addAll(this.deliveredIds);
        }
        if (!other.deliveredIds.isEmpty()) {
            this.deliveredIds.addAll(other.deliveredIds);
        }
        purgeDeliveredLocally();
        other.purgeDeliveredLocally();
    }

    private void purgeDeliveredLocally() {
        if (this.deliveredIds.isEmpty()) {
            return;
        }
        List<String> toDelete = new ArrayList<String>();
        for (Message m : getMessageCollection()) {
            if (this.deliveredIds.contains(m.getId())) {
                toDelete.add(m.getId());
            }
        }
        for (String id : toDelete) {
            if (hasMessage(id)) {
                deleteMessage(id, false);
            }
        }
    }

    private int getCopies(Message m) {
        Object value = m.getProperty(MSG_COPIES_PROP);
        if (value == null) {
            ensureCopiesProperty(m, this.initialCopies);
            return this.initialCopies;
        }
        return ((Integer) value).intValue();
    }

    private void ensureCopiesProperty(Message m, int copies) {
        Object value = m.getProperty(MSG_COPIES_PROP);
        if (value == null) {
            m.addProperty(MSG_COPIES_PROP, Integer.valueOf(copies));
        }
    }

    private void updateCopies(Message m, int copies) {
        if (m.getProperty(MSG_COPIES_PROP) == null) {
            m.addProperty(MSG_COPIES_PROP, Integer.valueOf(copies));
        } else {
            m.updateProperty(MSG_COPIES_PROP, Integer.valueOf(copies));
        }
    }

    private int getClassMask(Message m) {
        Object value = m.getProperty(MSG_CLASS_MASK_PROP);
        if (value == null) {
            int mask = 1 << getMobilityClass(getHost());
            ensureClassMaskProperty(m, mask);
            return mask;
        }
        return ((Integer) value).intValue();
    }

    private void ensureClassMaskProperty(Message m, int classMask) {
        Object value = m.getProperty(MSG_CLASS_MASK_PROP);
        if (value == null) {
            m.addProperty(MSG_CLASS_MASK_PROP, Integer.valueOf(classMask));
        }
    }

    private void updateClassMask(Message m, int classMask) {
        if (m.getProperty(MSG_CLASS_MASK_PROP) == null) {
            m.addProperty(MSG_CLASS_MASK_PROP, Integer.valueOf(classMask));
        } else {
            m.updateProperty(MSG_CLASS_MASK_PROP, Integer.valueOf(classMask));
        }
    }

    private int getMobilityClass(DTNHost host) {
        int addr = host.getAddress();
        if (addr < this.classBoundary1) {
            return 0;
        }
        if (addr < this.classBoundary2) {
            return 1;
        }
        if (addr < this.classBoundary3) {
            return 2;
        }
        return 3;
    }

    private double getTtlUrgency(Message m) {
        if (this.msgTtl <= 0 || this.msgTtl == Message.INFINITE_TTL) {
            return 0.0;
        }
        double remaining = m.getTtl();
        if (remaining == Integer.MAX_VALUE) {
            return 0.0;
        }
        return clamp(1.0 - (remaining / (double) this.msgTtl));
    }

    private double getTopClassDiversity(List<Candidate> population) {
        int limit = Math.min(3, population.size());
        Set<Integer> classes = new HashSet<Integer>();
        for (int i = 0; i < limit; i++) {
            classes.add(Integer.valueOf(population.get(i).classId));
        }
        return clamp(classes.size() / 3.0);
    }

    private double getTopDistanceProgress(List<Candidate> population) {
        int limit = Math.min(3, population.size());
        if (limit == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < limit; i++) {
            sum += population.get(i).distanceProgress;
        }
        return clamp(sum / limit);
    }

    private double getTopNovelty(List<Candidate> population) {
        int limit = Math.min(3, population.size());
        double max = 0.0;
        for (int i = 0; i < limit; i++) {
            if (population.get(i).classNovelty > max) {
                max = population.get(i).classNovelty;
            }
        }
        return max;
    }

    private double computeDistanceProgress(double selfDistance, double otherDistance) {
        if (selfDistance <= 0.0) {
            return 1.0;
        }
        double gain = (selfDistance - otherDistance) / (selfDistance + 1.0);
        return clamp(Math.max(0.0, gain));
    }

    private double distance(DTNHost a, DTNHost b) {
        Coord ca = a.getLocation();
        Coord cb = b.getLocation();
        return ca.distance(cb);
    }

    private static double clamp(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    private static double clampSigned(double v, double limit) {
        if (v > limit) {
            return limit;
        }
        if (v < -limit) {
            return -limit;
        }
        return v;
    }

    private static double getDoubleOrDefault(Settings s, String key, double def) {
        return s.contains(key) ? s.getDouble(key) : def;
    }

    private static int getIntOrDefault(Settings s, String key, int def) {
        return s.contains(key) ? s.getInt(key) : def;
    }

    @Override
    public MessageRouter replicate() {
        return new GaPsoRouter(this);
    }

    private static class Candidate {
        private Message message;
        private Connection connection;
        private DTNHost other;
        private int classId;
        private double classNovelty;
        private double distanceProgress;
        private double bufferHeadroom;
        private double linkQuality;
        private double ttlUrgency;
        private double baseScore;
        private double gaScore;
        private double psoScore;
        private double finalScore;
    }

    private static class HybridChoice {
        private Message message;
        private Connection connection;
        private DTNHost other;
        private double classNovelty;
        private double finalScore;
        private double copyPressure;
        private double utility;
    }
}