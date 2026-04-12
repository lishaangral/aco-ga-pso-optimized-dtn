package routing;

import core.*;
import java.util.*;

/**
 Bio-inspired Probabilistic Opportunistic Routing (Hybrid-Swarm)
 Exact strict implementation matching equations from Azzoug & Boukra.
 */
public class HybridSwarmRouter extends ActiveRouter {

    // --- Hyperparameters ---
    private double alpha1 = 0.33; 
    private double alpha2 = 0.33; 
    private double alpha3 = 0.34; 
    
    private double omega1 = 0.5;  
    private double omega2 = 0.5;  

    // GSO Luciferin Update Parameters (Eq. 12)
    private double lambda = 0.4; // Luciferin decay factor
    private double gamma = 0.6;  // Luciferin enhancement constant
    private Map<DTNHost, Double> luciferinTable;

    public static final int L_COPIES = 6; 
    public static final double RESET_TIME = 3600.0; // 3600 seconds loop
    public static final double THRESHOLD_TH = 3600.0; // FP Threshold window

    // --- Node Statistics Tracking ---
    private int nbRelays = 0;
    private int nbTargets = 0;

    // Using LinkedLists to track timestamps for the 'Threshold Th' sliding window
    private LinkedList<Double> replicaTimestamps;
    private LinkedList<Double> forwardTimestamps;
    
    private double totalBufferTime = 0.0;
    private double totalTravelTime = 0.0;
    private double lastResetTime = 0.0; // Tracks the 3600s loop

    // Limit how many times THIS node will replicate a single message
    public static final int MAX_LOCAL_REPLICAS = 2; 
    private Map<String, Integer> localForwardCount = new HashMap<>();

    // Random number generator for GSO stochastic selection
    private Random rng;

    public HybridSwarmRouter(Settings s) {
        super(s);
        this.replicaTimestamps = new LinkedList<>();
        this.forwardTimestamps = new LinkedList<>();
        this.luciferinTable = new HashMap<>();
        this.rng = new Random();

        // Safely check the Group settings for injected variables
        if (s.contains("alpha1")) this.alpha1 = s.getDouble("alpha1");
        if (s.contains("alpha2")) this.alpha2 = s.getDouble("alpha2");
        if (s.contains("alpha3")) this.alpha3 = s.getDouble("alpha3");
        if (s.contains("omega1")) this.omega1 = s.getDouble("omega1");
        if (s.contains("omega2")) this.omega2 = s.getDouble("omega2");
    }

    public HybridSwarmRouter(HybridSwarmRouter r) {
        super(r);
        this.nbRelays = r.nbRelays;
        this.nbTargets = r.nbTargets;
        this.replicaTimestamps = new LinkedList<>(r.replicaTimestamps);
        this.forwardTimestamps = new LinkedList<>(r.forwardTimestamps);
        this.luciferinTable = new HashMap<>(r.luciferinTable);
        this.rng = new Random();
        
        // Pass the injected weights to the cloned vehicles!
        this.alpha1 = r.alpha1;
        this.alpha2 = r.alpha2;
        this.alpha3 = r.alpha3;
        this.omega1 = r.omega1;
        this.omega2 = r.omega2;
    }

    @Override
    public MessageRouter replicate() {
        return new HybridSwarmRouter(this);
    }
    
    @Override
    public Message messageTransferred(String id, DTNHost from) {
        Message m = super.messageTransferred(id, from);
        this.nbRelays++;
        return m;
    }

    @Override
    public int receiveMessage(Message m, DTNHost from) {
        int received = super.receiveMessage(m, from);
        
        if (received == MessageRouter.RCV_OK) {
            if (m.getTo() == getHost()) {
                this.nbTargets++;
            }
            m.addProperty("receiveTime", SimClock.getTime());
            
            boolean isGSOPhase = m.getHopCount() >= L_COPIES;
            double fitness = isGSOPhase ? 
                             calculateGSOFitness(this, m.getTo()) : 
                             calculateACOFitness(this);
            m.addProperty("swarmFitness", fitness);
        }
        return received;
    }

    @Override
    public void update() {
        super.update();
        
        // The 3600-Second History Reset
        double currentTime = SimClock.getTime();
        if (currentTime - lastResetTime >= RESET_TIME) {
            this.nbRelays = 0;
            this.nbTargets = 0;
            this.replicaTimestamps.clear();
            this.forwardTimestamps.clear();
            this.lastResetTime = currentTime;
        }

        //  Prune sliding window for FP Threshold
        while (!replicaTimestamps.isEmpty() && currentTime - replicaTimestamps.peek() > THRESHOLD_TH) {
            replicaTimestamps.poll();
        }
        while (!forwardTimestamps.isEmpty() && currentTime - forwardTimestamps.peek() > THRESHOLD_TH) {
            forwardTimestamps.poll();
        }

        if (isTransferring() || !canStartTransfer()) return;
        if (exchangeDeliverableMessages() != null) return; 

        List<Connection> connections = getConnections();
        if (connections.isEmpty()) return;

        List<Message> messages = new ArrayList<>(getMessageCollection());
        messages.sort(new BufferPriorityComparator());

        for (Message m : messages) {
            // Paper omitted the transition list. Defaulting to Hop Count.
            boolean isGSOPhase = m.getHopCount() >= L_COPIES;

            if (isGSOPhase) {
                executeGSOLocalSearch(m, connections);
            } else {
                executeACOExploration(m, connections);
            }
        }
    }

    @Override
    protected void transferDone(Connection con) {
        Message m = con.getMessage();
        if (m != null) {
            // Increment how many times this host has forwarded this specific ID
            int count = localForwardCount.getOrDefault(m.getId(), 0) + 1;
            localForwardCount.put(m.getId(), count);

            // Update stats
            double rcvTime = m.getProperty("receiveTime") != null ? (double) m.getProperty("receiveTime") : m.getCreationTime();
            this.totalBufferTime += (SimClock.getTime() - rcvTime);
            this.totalTravelTime += (SimClock.getTime() - m.getCreationTime());

            // THE FIX: If global hop count is high OR this node has reached its local limit
            if ((m.getHopCount() >= L_COPIES || count >= MAX_LOCAL_REPLICAS) && hasMessage(m.getId())) {
                this.deleteMessage(m.getId(), false);
            }
        }
        super.transferDone(con);
    }
 
    private void executeACOExploration(Message m, List<Connection> connections) {
        // PAPER FACT: Exploration should be broader than exploitation.
        // If we've already reached the GSO threshold, stop ACO.
        if (m.getHopCount() >= L_COPIES) return;

        double myFitness = calculateACOFitness(this);
        int localCopiesSent = localForwardCount.getOrDefault(m.getId(), 0);

        // INCREASED QUOTA: Allow up to 2 copies per node regardless of hop count.
        // This is the standard "Restricted" limit used in many VDTN papers.
        int quota = 2; 

        for (Connection con : connections) {
            if (localCopiesSent >= quota) break; 

            DTNHost contact = con.getOtherNode(getHost());
            double peerFit = calculateACOFitness((HybridSwarmRouter) contact.getRouter());

            // ALIGNMENT: Peer must be better, OR the message must be very "young" (hop 0).
            // This ensures the source node (the creator) successfully seeds the network.
            boolean isSource = m.getHopCount() == 0;

            if ((peerFit > myFitness || isSource) && !contact.getRouter().hasMessage(m.getId())) {
                if (canStartTransfer()) {
                    startTransfer(m, con);
                    this.replicaTimestamps.add(SimClock.getTime());
                    this.forwardTimestamps.add(SimClock.getTime());
                    
                    // CRITICAL CHANGE: Remove the 'return'. 
                    // Allow the node to give copies to multiple neighbors in one contact.
                    localCopiesSent++; 
                }
            }
        }
    }
    
    // Probabilistic Selection & Luciferin Decay 
    private void executeGSOLocalSearch(Message m, List<Connection> connections) {
        double hostFitness = calculateGSOFitness(this, m.getTo());
        updateLuciferin(m.getTo(), hostFitness);
        double hostLuciferin = getLuciferin(m.getTo());
        
        List<Tuple<Connection, Double>> validCandidates = new ArrayList<>();
        double sumDifferences = 0.0;

        for (Connection con : connections) {
            DTNHost contact = con.getOtherNode(getHost());
            HybridSwarmRouter peerRouter = (HybridSwarmRouter) contact.getRouter();

            // Calculate and continuously decay peer's luciferin via Eq. 12
            double candidateFitness = calculateGSOFitness(peerRouter, m.getTo());
            peerRouter.updateLuciferin(m.getTo(), candidateFitness);
            double candidateLuciferin = peerRouter.getLuciferin(m.getTo());
            
            // GSO Rule (Eq. 19): Eliminate contacts with lower luciferin than host
            if (candidateLuciferin > hostLuciferin) {
                if (!contact.getRouter().hasMessage(m.getId())) {
                    double diff = candidateLuciferin - hostLuciferin;
                    validCandidates.add(new Tuple<>(con, diff));
                    sumDifferences += diff; // Sum denominator for Eq. 18
                }
            }
        }

        if (validCandidates.isEmpty() || !canStartTransfer()) return;

        // Eq 18: Stochastic Roulette Wheel Selection
        double randomPoint = rng.nextDouble() * sumDifferences;
        double runningSum = 0.0;
        Connection selectedCon = null;

        for (Tuple<Connection, Double> tuple : validCandidates) {
            runningSum += tuple.getValue();
            if (runningSum >= randomPoint) {
                selectedCon = tuple.getKey();
                break;
            }
        }

        if (selectedCon != null) {
            startTransfer(m, selectedCon);
            this.forwardTimestamps.add(SimClock.getTime());
        }
    }

    // --- Math Implementation ---

    public void updateLuciferin(DTNHost destination, double currentFitness) {
        double currentL = getLuciferin(destination);
        // Eq 12: L_j(t+1) = (1 - lambda) * L_j(t) + gamma * f(Pos)
        double newL = (1.0 - lambda) * currentL + (gamma * currentFitness);
        this.luciferinTable.put(destination, newL);
    }

    public double getLuciferin(DTNHost destination) {
        return this.luciferinTable.getOrDefault(destination, 0.0);
    }

    private double calculateACOFitness(HybridSwarmRouter peer) {
        double hp = 0, fp = 0, sp = 0;
        
        if ((peer.nbRelays + peer.nbTargets) > 0) {
            hp = (double) peer.nbRelays / (peer.nbRelays + peer.nbTargets);
        }
        
        // Uses the sliding window list sizes instead of infinite ints
        int validReplicas = peer.replicaTimestamps.size();
        int validForwards = peer.forwardTimestamps.size();
        
        if (validForwards > 0) {
            fp = (double) validReplicas / validForwards;
            fp = Math.min(fp, 1.0); 
        }
        
        if (peer.totalTravelTime > 0) {
            sp = 1.0 - (peer.totalBufferTime / peer.totalTravelTime);
            sp = Math.max(0.0, sp); 
        } else {
            sp = 1.0; 
        }
        
        return (alpha1 * hp) + (alpha2 * fp) + (alpha3 * sp);
    }

    private double calculateGSOFitness(HybridSwarmRouter peer, DTNHost destination) {
        double hf = 0, mf = 0;
        
        if ((peer.nbRelays + peer.nbTargets) > 0) {
            hf = (double) peer.nbTargets / (peer.nbRelays + peer.nbTargets);
        }
        
        Coord destLoc = destination.getLocation();
        Coord myLoc = peer.getHost().getLocation();
        double distance = myLoc.distance(destLoc);
        mf = 1.0 / (1.0 + distance); 
        
        return (omega1 * hf) + (omega2 * mf);
    }

    private class BufferPriorityComparator implements Comparator<Message> {
        @Override
        public int compare(Message m1, Message m2) {
            boolean m1GSO = m1.getHopCount() >= L_COPIES;
            boolean m2GSO = m2.getHopCount() >= L_COPIES;
            
            if (m1GSO && !m2GSO) return -1;
            if (!m1GSO && m2GSO) return 1;
            
            double fit1 = m1.getProperty("swarmFitness") != null ? (double) m1.getProperty("swarmFitness") : 0.0;
            double fit2 = m2.getProperty("swarmFitness") != null ? (double) m2.getProperty("swarmFitness") : 0.0;
            if (fit1 != fit2) {
                return Double.compare(fit2, fit1); 
            }
            
            if (m1.getHopCount() != m2.getHopCount()) {
                return Integer.compare(m1.getHopCount(), m2.getHopCount());
            }
            
            if (m1.getSize() != m2.getSize()) {
                return Integer.compare(m2.getSize(), m1.getSize());
            }
            
            double m1ReceiveTime = m1.getProperty("receiveTime") != null ? (double) m1.getProperty("receiveTime") : SimClock.getTime();
            double m2ReceiveTime = m2.getProperty("receiveTime") != null ? (double) m2.getProperty("receiveTime") : SimClock.getTime();
            
            double m1Time = SimClock.getTime() - m1ReceiveTime;
            double m2Time = SimClock.getTime() - m2ReceiveTime;
            return Double.compare(m2Time, m1Time);
        }
    }

    private class Tuple<K, V> {
        private K key;
        private V value;
        public Tuple(K key, V value) { this.key = key; this.value = value; }
        public K getKey() { return key; }
        public V getValue() { return value; }
    }
}
            
