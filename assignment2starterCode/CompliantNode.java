import java.util.*;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
  final private double p_graph_;
  final private double p_malicious_;
  final private double p_tx_;
  final private int total_round_;
  private boolean[] followees_;
  private HashSet<Transaction> sent_to_follower_;

  public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
    p_graph_ = p_graph;
    p_malicious_ = p_malicious;
    p_tx_ = p_txDistribution;
    total_round_ = numRounds;
  }

  public void setFollowees(boolean[] followees) {
    followees_ = Arrays.copyOf(followees, followees.length);
  }

  // This method will only be called once in Simulation during the initial setup
  // stage, which assigns transactions to each node per p_tx_.
  public void setPendingTransaction(Set<Transaction> pendingTransactions) {
    sent_to_follower_ = new HashSet<Transaction>(pendingTransactions);
  }

  public Set<Transaction> sendToFollowers() {
    return sent_to_follower_;
  }

  // Initial implementation (81 points): Broadcast every (valid )message ever
  // received and treat them as consensus set.
  public void receiveFromFollowees(Set<Candidate> candidates) {
    for (Candidate c : candidates) {
      // Check to see if the sender is from my followee.
      if (followees_[c.sender] && !sent_to_follower_.contains(c.tx)) {
        sent_to_follower_.add(c.tx);
      }
    }
  }
}
