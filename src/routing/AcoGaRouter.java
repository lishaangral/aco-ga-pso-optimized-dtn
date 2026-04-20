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
import core.MessageListener; 

import routing.ActiveRouter;
import routing.MessageRouter;

/**
 * Ablation Study: ACO + GA router for THE ONE (No PSO).
 *
 * This "Blind Jump" model lacks kinematic velocity and dynamic replication pressure.
 * ACO: historical memory via pheromone + encounter history.
 * GA : diversified candidate ranking among the best relays.
 * Replication pressure is static (0.60) mimicking standard restricted flooding.
 */
public class AcoGaRouter extends ActiveRouter {

    public static final String ALPHA1_S = "alpha1";
    public static final String ALPHA2_S = "alpha2";
    public static final String ALPHA3_S = "alpha3";
    public static final String OMEGA1_S = "omega1";

    public static final String PHEROMONE_EVAPORATION_S = "pheromoneEvaporation";
    public static final String PHEROMONE_BOOST_S = "pheromoneBoost";
    public static final String PHEROMONE_CAP_S = "pheromoneCap";
    public static final String PHEROMONE_AGE_INTERVAL_S = "pheromoneAgeInterval";

    public static final String GA_POPULATION_SIZE_S = "gaPopulationSize";
    public static final String GA_CROSSOVER_RATE_S = "gaCrossoverRate";
    public static final String GA_MUTATION_RATE_S = "gaMutationRate";
    public static final String EXPLORE_BOOST_S = "exploreBoost";

    public static final String INITIAL_COPIES_S = "initialCopies";
    public static final String CONGESTION_THRESHOLD_S = "congestionThreshold";
    public static final String TTL_URGENCY_THRESHOLD_S = "ttlUrgencyThreshold";
    public static final String RESCUE_URGENCY_THRESHOLD_S = "rescueUrgencyThreshold";
    public static final String STRONG_RELAY_THRESHOLD_S = "strongRelayThreshold";
    public static final String LINK_SPEED_NORM_S = "linkSpeedNorm";
    public static final String STABILITY_NORM_S = "stabilityNormSeconds";

    public static final String CLASS_BOUNDARY_1_S = "classBoundary1";
    public static final String CLASS_BOUNDARY_2_S = "classBoundary2";
    public static final String CLASS_BOUNDARY_3_S = "classBoundary3";

    private static final String MSG_COPIES_PROP = "AcoGaRouter.copies";
    private static final String MSG_CLASS_MASK_PROP = "AcoGaRouter.classMask";

    private double alpha1;
    private double alpha2;
    private double alpha3;
    private double omega1;

    private double pheromoneEvaporation;
    private double pheromoneBoost;
    private double pheromoneCap;
    private double pheromoneAgeInterval;

    private int gaPopulationSize;
    private double gaCrossoverRate;
    private double gaMutationRate;
    private double exploreBoost;

    private int initialCopies;
    private double congestionThreshold;
    private double ttlUrgencyThreshold;
    private double rescueUrgencyThreshold;
    private double strongRelayThreshold;
    private double linkSpeedNorm;
    private double stabilityNormSeconds;

    private int classBoundary1;
    private int classBoundary2;
    private int classBoundary3;

    private Map<Integer, Double> pheromones;
    private Map<Integer, Double> encounterCounts;
    private Map<Integer, Double> contactStarts;
    private Map<Integer, Double> contactDurationEma;

    private Map<Integer, Integer> pendingSenderCopies;
    private Map<Integer, Integer> pendingOldClassMasks;
    private Set<String> deliveredIds;

    private double lastPheromoneAge;
    private Random rng;

    public AcoGaRouter(Settings s) {
        super(s);

        this.alpha1 = getDoubleOrDefault(s, ALPHA1_S, 0.30);
        this.alpha2 = getDoubleOrDefault(s, ALPHA2_S, 0.38);
        this.alpha3 = getDoubleOrDefault(s, ALPHA3_S, 0.32);
        this.omega1 = getDoubleOrDefault(s, OMEGA1_S, 0.55);

        this.pheromoneEvaporation = getDoubleOrDefault(s, PHEROMONE_EVAPORATION_S, 0.03);
        this.pheromoneBoost = getDoubleOrDefault(s, PHEROMONE_BOOST_S, 0.24);
        this.pheromoneCap = getDoubleOrDefault(s, PHEROMONE_CAP_S, 3.0);
        this.pheromoneAgeInterval = getDoubleOrDefault(s, PHEROMONE_AGE_INTERVAL_S, 20.0);

        this.gaPopulationSize = getIntOrDefault(s, GA_POPULATION_SIZE_S, 8);
        this.gaCrossoverRate = getDoubleOrDefault(s, GA_CROSSOVER_RATE_S, 0.78);
        this.gaMutationRate = getDoubleOrDefault(s, GA_MUTATION_RATE_S, 0.12);
        this.exploreBoost = getDoubleOrDefault(s, EXPLORE_BOOST_S, 0.24);

        this.initialCopies = getIntOrDefault(s, INITIAL_COPIES_S, 12);
        this.congestionThreshold = getDoubleOrDefault(s, CONGESTION_THRESHOLD_S, 0.85);
        this.ttlUrgencyThreshold = getDoubleOrDefault(s, TTL_URGENCY_THRESHOLD_S, 0.25);
        this.rescueUrgencyThreshold = getDoubleOrDefault(s, RESCUE_URGENCY_THRESHOLD_S, 0.65);
        this.strongRelayThreshold = getDoubleOrDefault(s, STRONG_RELAY_THRESHOLD_S, 0.72);
        this.linkSpeedNorm = getDoubleOrDefault(s, LINK_SPEED_NORM_S, 250000.0);
        this.stabilityNormSeconds = getDoubleOrDefault(s, STABILITY_NORM_S, 60.0);

        this.classBoundary1 = getIntOrDefault(s, CLASS_BOUNDARY_1_S, 80);
        this.classBoundary2 = getIntOrDefault(s, CLASS_BOUNDARY_2_S, 90);
        this.classBoundary3 = getIntOrDefault(s, CLASS_BOUNDARY_3_S, 100);

        initState();
    }

    protected AcoGaRouter(AcoGaRouter r) {
        super(r);

        this.alpha1 = r.alpha1;
        this.alpha2 = r.alpha2;
        this.alpha3 = r.alpha3;
        this.omega1 = r.omega1;

        this.pheromoneEvaporation = r.pheromoneEvaporation;
        this.pheromoneBoost = r.pheromoneBoost;
        this.pheromoneCap = r.pheromoneCap;
        this.pheromoneAgeInterval = r.pheromoneAgeInterval;

        this.gaPopulationSize = r.gaPopulationSize;
        this.gaCrossoverRate = r.gaCrossoverRate;
        this.gaMutationRate = r.gaMutationRate;
        this.exploreBoost = r.exploreBoost;

        this.initialCopies = r.initialCopies;
        this.congestionThreshold = r.congestionThreshold;
        this.ttlUrgencyThreshold = r.ttlUrgencyThreshold;
        this.rescueUrgencyThreshold = r.rescueUrgencyThreshold;
        this.strongRelayThreshold = r.strongRelayThreshold;
        this.linkSpeedNorm = r.linkSpeedNorm;
        this.stabilityNormSeconds = r.stabilityNormSeconds;

        this.classBoundary1 = r.classBoundary1;
        this.classBoundary2 = r.classBoundary2;
        this.classBoundary3 = r.classBoundary3;

        initState();
    }

    private void initState() {
        this.pheromones = new HashMap<Integer, Double>();
        this.encounterCounts = new HashMap<Integer, Double>();
        this.contactStarts = new HashMap<Integer, Double>();
        this.contactDurationEma = new HashMap<Integer, Double>();

        this.pendingSenderCopies = new HashMap<Integer, Integer>();
        this.pendingOldClassMasks = new HashMap<Integer, Integer>();
        this.deliveredIds = new HashSet<String>();

        this.lastPheromoneAge = 0.0;
        this.rng = new Random(19);
    }

    @Override
    public void init(DTNHost host, List<MessageListener> mListeners) {
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
        int addr = other.getAddress();

        if (con.isUp()) {
            reinforcePheromone(other, this.pheromoneBoost * 0.35);
            incrementEncounter(other);
            this.contactStarts.put(Integer.valueOf(addr), Double.valueOf(SimClock.getTime()));

            if (other.getRouter() instanceof AcoGaRouter) {
                syncDelivered((AcoGaRouter) other.getRouter());
            }
        } else {
            Double start = this.contactStarts.remove(Integer.valueOf(addr));
            if (start != null) {
                double duration = Math.max(0.0, SimClock.getTime() - start.doubleValue());
                Double old = this.contactDurationEma.get(Integer.valueOf(addr));
                double ema = old == null ? duration : (0.70 * old.doubleValue() + 0.30 * duration);
                this.contactDurationEma.put(Integer.valueOf(addr), Double.valueOf(ema));
            }
        }
    }

    @Override
    public void update() {
        super.update();
        agePheromones();
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

            HybridChoice current = selectByAcoGa(m, candidates, congestion);

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
            c.pheromoneNorm = normalize(getPheromone(other), this.pheromoneCap);
            c.encounterNorm = normalize(getEncounterCount(other), getMaxEncounterCount());
            c.predictability = clamp(0.55 * c.pheromoneNorm + 0.45 * c.encounterNorm);
            c.distanceProgress = computeDistanceProgress(selfDistance, distance(other, m.getTo()));
            c.bufferHeadroom = 1.0 - clamp(other.getBufferOccupancy() / 100.0);
            c.linkQuality = clamp(con.getSpeed() / this.linkSpeedNorm);
            c.contactStability = normalize(getContactDurationEma(other), this.stabilityNormSeconds);
            c.ttlUrgency = getTtlUrgency(m);
            c.baseScore = computeAcoScore(c);
            candidates.add(c);
        }

        return candidates;
    }

    private HybridChoice selectByAcoGa(Message m, List<Candidate> candidates, double congestion) {
        Collections.sort(candidates, new Comparator<Candidate>() {
            public int compare(Candidate a, Candidate b) {
                return (a.baseScore > b.baseScore) ? -1 : ((a.baseScore < b.baseScore) ? 1 : 0);
            }
        });

        int popSize = Math.min(this.gaPopulationSize, candidates.size());
        List<Candidate> population = new ArrayList<Candidate>(candidates.subList(0, popSize));

        applyGa(population);

        Candidate best = null;
        for (Candidate c : population) {
            // Missing PSO: Final score is just the ACO-GA blend
            c.finalScore = clamp((1.0 - this.omega1) * c.baseScore + this.omega1 * c.gaScore);
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
        
        // Missing PSO: Static replication pressure
        choice.copyPressure = 0.60; 
        
        choice.utility = choice.finalScore + 0.15 * choice.copyPressure;
        return choice;
    }

    private void applyGa(List<Candidate> population) {
        if (population.isEmpty()) {
            return;
        }

        Candidate parentA = population.get(0);
        Candidate parentB = population.get(population.size() > 1 ? 1 : 0);
        boolean sameClassTop = parentA.classId == parentB.classId;

        double offspring = this.gaCrossoverRate * (
                0.40 * parentA.baseScore +
                0.20 * parentB.baseScore +
                0.15 * parentA.classNovelty +
                0.15 * parentA.contactStability +
                0.10 * parentB.bufferHeadroom)
                + (1.0 - this.gaCrossoverRate) * (0.60 * parentA.predictability + 0.40 * parentB.distanceProgress);
        offspring = clamp(offspring);

        for (Candidate c : population) {
            double inherited = 0.55 * c.baseScore + 0.20 * offspring + 0.15 * c.classNovelty + 0.10 * c.contactStability;
            c.gaScore = clamp(inherited);

            double adaptiveMutation = this.gaMutationRate;
            if (sameClassTop) {
                adaptiveMutation += 0.05;
            }
            adaptiveMutation += 0.05 * (1.0 - c.baseScore);

            if (this.rng.nextDouble() < adaptiveMutation) {
                double mutation = this.exploreBoost * (0.60 * (1.0 - c.predictability) + 0.40 * c.classNovelty);
                mutation *= (0.65 + 0.35 * this.rng.nextDouble());
                c.gaScore = clamp(c.gaScore + mutation);
            }
        }
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

        double reward = this.pheromoneBoost * (0.45 + 0.55 * choice.finalScore + 0.20 * choice.classNovelty);
        reinforcePheromone(other, reward);
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
            reinforcePheromone(other, this.pheromoneBoost + 0.25);
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

        reinforcePheromone(other, this.pheromoneBoost * 0.60);
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

    private double computeAcoScore(Candidate c) {
        double historical = 0.60 * c.predictability + 0.40 * c.pheromoneNorm;
        double mobility = 0.55 * c.distanceProgress + 0.45 * c.contactStability;
        double resources = 0.60 * c.bufferHeadroom + 0.40 * c.linkQuality;
        double score = this.alpha1 * historical
                + this.alpha2 * mobility
                + this.alpha3 * resources
                + 0.08 * c.classNovelty
                + 0.05 * c.ttlUrgency * c.distanceProgress;
        return clamp(score);
    }

    private void agePheromones() {
        if (SimClock.getTime() - this.lastPheromoneAge < this.pheromoneAgeInterval) {
            return;
        }

        List<Integer> keys = new ArrayList<Integer>(this.pheromones.keySet());
        for (Integer key : keys) {
            double aged = this.pheromones.get(key).doubleValue() * (1.0 - this.pheromoneEvaporation);
            if (aged < 0.0001) {
                this.pheromones.remove(key);
            } else {
                this.pheromones.put(key, Double.valueOf(aged));
            }
        }
        this.lastPheromoneAge = SimClock.getTime();
    }

    private void syncDelivered(AcoGaRouter other) {
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

    private void incrementEncounter(DTNHost other) {
        Integer key = Integer.valueOf(other.getAddress());
        Double value = this.encounterCounts.get(key);
        this.encounterCounts.put(key, Double.valueOf(value == null ? 1.0 : value.doubleValue() + 1.0));
    }

    private double getEncounterCount(DTNHost other) {
        Double value = this.encounterCounts.get(Integer.valueOf(other.getAddress()));
        return value == null ? 0.0 : value.doubleValue();
    }

    private double getMaxEncounterCount() {
        double max = 1.0;
        for (Double value : this.encounterCounts.values()) {
            if (value.doubleValue() > max) {
                max = value.doubleValue();
            }
        }
        return max;
    }

    private double getContactDurationEma(DTNHost other) {
        Double value = this.contactDurationEma.get(Integer.valueOf(other.getAddress()));
        return value == null ? 0.0 : value.doubleValue();
    }

    private void reinforcePheromone(DTNHost other, double amount) {
        Integer key = Integer.valueOf(other.getAddress());
        double oldValue = getPheromone(other);
        double updated = oldValue + Math.max(0.0, amount);
        if (updated > this.pheromoneCap) {
            updated = this.pheromoneCap;
        }
        this.pheromones.put(key, Double.valueOf(updated));
    }

    private double getPheromone(DTNHost other) {
        Double value = this.pheromones.get(Integer.valueOf(other.getAddress()));
        return value == null ? 0.0 : value.doubleValue();
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

    private static double normalize(double value, double maxValue) {
        if (maxValue <= 0.0) {
            return 0.0;
        }
        return clamp(value / maxValue);
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

    private static double getDoubleOrDefault(Settings s, String key, double def) {
        return s.contains(key) ? s.getDouble(key) : def;
    }

    private static int getIntOrDefault(Settings s, String key, int def) {
        return s.contains(key) ? s.getInt(key) : def;
    }

    @Override
    public MessageRouter replicate() {
        return new AcoGaRouter(this);
    }

    private static class Candidate {
        private Message message;
        private Connection connection;
        private DTNHost other;
        private int classId;
        private double classNovelty;
        private double pheromoneNorm;
        private double encounterNorm;
        private double predictability;
        private double distanceProgress;
        private double bufferHeadroom;
        private double linkQuality;
        private double contactStability;
        private double ttlUrgency;
        private double baseScore;
        private double gaScore;
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