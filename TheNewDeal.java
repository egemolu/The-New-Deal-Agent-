import genius.core.AgentID;
import genius.core.Bid;
import genius.core.BidIterator;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;

import java.util.*;

public class TheNewDeal extends AbstractNegotiationParty {
    private Bid lastReceivedBid = null;
    private Bid currentBid = null;
    private ArrayList<Bid> bidDataset;
    private int counter = 1;
    private int roundCounter = 0;
    private int ratio = 0;
    private int bidIndex = 0;
    private Bid[] allPossibleBids;
    private Double[] sortedUtilityArray;
    private int issueCount;
    private HashMap<Bid, Double> estimatedUtils = new HashMap<>();
    private HashMap<Value, Double> valuesAverage = new HashMap<>();
    private HashMap<Bid, Double> allUtils = new HashMap<>();
    private int[] issueWeights;
    private int[] issueUtils;
    private ArrayList<Double> issueFinalWeights = new ArrayList<>();

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);
        Bid sampleBid = generateRandomBid();
        IssueDiscrete[] issueDiscretes = new IssueDiscrete[sampleBid.getIssues().size()];
        ValueDiscrete[][] valueDiscretes = new ValueDiscrete[issueDiscretes.length][];
        issueCount = issueDiscretes.length;
        issueWeights = new int[issueCount];
        issueUtils = new int[issueCount];
        BidIterator iterator = new BidIterator(utilitySpace.getDomain());
        ArrayList<Bid> bids = new ArrayList<>();
        List<Bid> bidOrder = userModel.getBidRanking().getBidOrder();
        IssueValueItems(sampleBid, issueDiscretes, valueDiscretes);
        EstimateIssueWeight(bidOrder, issueWeights, issueUtils);
        initArrayAndRatio(iterator, bids);
        double step = EstimateEqualDifferenceUtilities(userModel.getBidRanking().getHighUtility(), userModel.getBidRanking().getLowUtility(), bidOrder.size());
        EstimateEven(bidOrder, step);
        HashMap<Value, double[]> mp = new HashMap<>();
        ValuesWithTotalCounter(mp, bidOrder);
        GenerateValuesAverage(mp);
        NormalizeUtils();
        sortedUtilityArray = createUtilArray();
        SortUtilArray(sortedUtilityArray);
        TestRanking(bids);
    }
    public Bid findBid(Double util){
        for (Map.Entry<Bid, Double> entry : allUtils.entrySet()) {
            if(entry.getValue().equals(util)){
                return entry.getKey();
            }
        }
        return null;
    }
    public Double[] createUtilArray(){
        Double[] arr = new Double[allUtils.size()];
        int i = 0;
        for (Map.Entry<Bid, Double> entry : allUtils.entrySet()) {
            arr[i] = entry.getValue();
            i++;
        }
        return arr;
    }
    public double CalculateTotalUtility() {
        double totalUtilities = 0;
        for (Bid b : allPossibleBids) {
            double bidUtil = 0;
            for (int c = 1; c <= issueCount; c++) {
                Value v = b.getValue(c);
                bidUtil += valuesAverage.get(v) * issueFinalWeights.get(c - 1);
            }
            totalUtilities += bidUtil;
        }
        return totalUtilities;
    }

    public void TestRanking(ArrayList<Bid> bids){
        for(Bid b : bids){
            log("Bid: " + b.getValues() + " ---> Utility: " + getUtility(b) + "Estimated Utility: " + allUtils.get(b));
        }
    }

    public void NormalizeUtils() {
        double total = CalculateTotalUtility();
        for (Bid b : allPossibleBids) {
            double bidUtil = 0;
            for (int c = 1; c <= issueCount; c++) {
                Value v = b.getValue(c);
                bidUtil += valuesAverage.get(v) * issueFinalWeights.get(c - 1);
            }
            allUtils.put(b, bidUtil / total);
        }
    }

    public void ValuesWithTotalCounter(HashMap<Value, double[]> map, List<Bid> bidOrder) {
        for (int i = 0; i < bidOrder.size(); i++) {
            double util = estimatedUtils.get(bidOrder.get(i));
            Bid b = bidOrder.get(i);
            for (int c = 1; c <= issueCount; c++) {
                Value v = b.getValue(c);
                if (!map.containsKey(v)) {
                    double[] arr = {util / issueFinalWeights.get(c - 1), 1.0};
                    map.put(v, arr);
                } else {
                    double[] arr = map.get(v);
                    arr[1] += 1.0;
                    arr[0] = arr[0] + (util / issueFinalWeights.get(c - 1));
                    map.put(v, arr);
                }
            }
        }
    }

    public void GenerateValuesAverage(HashMap<Value, double[]> map) {
        for (Map.Entry<Value, double[]> entry : map.entrySet()) {
            double average = entry.getValue()[0] / entry.getValue()[1];
            valuesAverage.put(entry.getKey(), average);
        }
    }

    public double EstimateEqualDifferenceUtilities(double maxUtil, double minUtil, int itemCount) {
        return (maxUtil - minUtil) / (itemCount - 1);
    }

    public void EstimateEven(List<Bid> bidOrder, double step) {
        double startUtil = userModel.getBidRanking().getLowUtility();
        estimatedUtils.put(bidOrder.get(0), startUtil);
        for (int i = 1; i < bidOrder.size() - 1; i++) {
            estimatedUtils.put(bidOrder.get(i), startUtil + step);
            startUtil += step;
        }
        estimatedUtils.put(bidOrder.get(bidOrder.size() - 1), userModel.getBidRanking().getHighUtility());
    }

    public void EstimateIssueWeight(List<Bid> bidOrder, int[] issueWeights, int[] issueUtils) {
        Bid tmp = generateRandomBid();
        for (int i = 1; i < issueCount + 1; i++) {
            for (int j = 0; j < bidOrder.size() - 1; j++) {
                if (j < bidOrder.size() - 1)
                    tmp = bidOrder.get(j + 1);
                if (bidOrder.get(j).getValue(i) == tmp.getValue(i)) {
                    issueWeights[i - 1]++;
                    if (issueUtils[i - 1] < issueWeights[i - 1])
                        issueUtils[i - 1] = issueWeights[i - 1];
                }
                if (bidOrder.get(j).getValue(i) != tmp.getValue(i))
                    issueWeights[i - 1] = 0;
            }
        }
        double sum = 0;
        for (int i : issueUtils) {
            sum = sum + i;
        }
        for (int i : issueUtils) {
            issueFinalWeights.add(i / sum);
        }
    }

    public void IssueValueItems(Bid sampleBid, IssueDiscrete[] issueDiscretes, ValueDiscrete[][] valueDiscretes) {
        for (int i = 0; i < sampleBid.getIssues().size(); i++) {
            IssueDiscrete issueDiscrete = (IssueDiscrete) sampleBid.getIssues().get(i);
            issueDiscretes[i] = issueDiscrete;
            valueDiscretes[i] = new ValueDiscrete[issueDiscretes[i].getValues().size()];
            for (int j = 0; j < issueDiscretes[i].getValues().size(); j++) {
                valueDiscretes[i][j] = issueDiscretes[i].getValues().get(j);
            }
        }
    }

    public void SortUtilArray(Double[] utilArr) {
        quicksort(utilArr, 0, allPossibleBids.length - 1);
    }

    public int partition(Double arr[], int low, int high) {
        double pivot = arr[high];
        int i = (low - 1);
        for (int j = low; j < high; j++) {
            if (arr[j] <= pivot) {
                i++;
                Double temp = arr[i];
                arr[i] = arr[j];
                arr[j] = temp;
            }
        }
        Double temp = arr[i + 1];
        arr[i + 1] = arr[high];
        arr[high] = temp;
        return i + 1;
    }

    public void quicksort(Double arr[], int low, int high) {
        if (low < high) {
            int pi = partition(arr, low, high);
            quicksort(arr, low, pi - 1);
            quicksort(arr, pi + 1, high);
        }
    }

    public void initArrayAndRatio(BidIterator iterator, ArrayList<Bid> bids) {
        int j = 0;
        while (iterator.hasNext()) {
            Bid bid = iterator.next();
            bids.add(bid);
            j++;
        }
        allPossibleBids = new Bid[j];
        for (int i = 0; i < j; i++) {
            allPossibleBids[i] = bids.get(i);
        }
        ratio = (int) ((timeline.getTotalTime() - 1) / allPossibleBids.length);
    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> validActions) {
        if (getLastReceivedAction() instanceof Offer)
            lastReceivedBid = ((Offer) getLastReceivedAction()).getBid();
        double ourBidUtil = sortedUtilityArray[sortedUtilityArray.length - 1 - bidIndex];
        double ourBidUtil2 = sortedUtilityArray[sortedUtilityArray.length - 2 - bidIndex];
        double opponentBidUtil = lastReceivedBid != null ? allUtils.get(lastReceivedBid) : 0;

        if (roundCounter == timeline.getTotalTime() - 2) {
            return new Accept(getPartyId(), lastReceivedBid);
        } else if ((ourBidUtil <= opponentBidUtil || ourBidUtil2 <= opponentBidUtil) && lastReceivedBid != null ) {
            return new Accept(getPartyId(), lastReceivedBid);
        } else {
            if (counter == ratio) {
                counter = 1;
                bidIndex++;
                if(bidIndex == sortedUtilityArray.length / 2){
                    bidIndex = 0;
                }
            }
            counter++;
            roundCounter++;
            return new Offer(getPartyId(), findBid(ourBidUtil));
        }
    }

    @Override
    public void receiveMessage(AgentID sender, Action action) {
        super.receiveMessage(sender, action);
        if (action instanceof Offer) {
            if (bidDataset == null)
                bidDataset = new ArrayList<>();

        }
    }

    @Override
    public AbstractUtilitySpace estimateUtilitySpace() {
        return super.estimateUtilitySpace();
    }

    @Override
    public String getDescription() {
        return "ANAC2019";
    }

    private static void log(String s) {
        System.out.println(s);
    }
}
