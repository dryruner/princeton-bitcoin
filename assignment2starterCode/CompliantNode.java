import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {
  private double p_graph_;
  private double p_malicious_;
  private double p_tx_;
  private int current_round_;
  private boolean[] followees_;
  private HashSet<Transaction> initial_tx_;
  private boolean is_received_from_setup_ = false;
  private HashSet<Transaction> sent_to_follower_;
  private HashSet<Candidate> received_from_followee_;

  public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
    p_graph_ = p_graph;
    p_malicious_ = p_malicious;
    p_tx_ = p_txDistribution;
    current_round_ = numRounds;
  }

  public void setFollowees(boolean[] followees) {
    followees_ = Arrays.copyOf(followees, followees.length);
  }

  // This method will only be called once in Simulation during the initial setup
  // stage, which assigns transactions to each node per p_tx_.
  public void setPendingTransaction(Set<Transaction> pendingTransactions) {
    initial_tx_ = new HashSet<Transaction>(pendingTransactions);
    is_received_from_setup_ = true;
  }

  // Initial implementation: forward every tx received from followee to
  // all followers.
  public Set<Transaction> sendToFollowers() {
    sent_to_follower_ = new HashSet<Transaction>(initial_tx_);
    if (is_received_from_setup_) {
      return sent_to_follower_;
    }
    for (Candidate c : received_from_followee_) {
      // Need to see if the sender is from my followee.
      if (followees_[c.sender]){
        sent_to_follower_.add(c.tx);
      }
    }
    return sent_to_follower_;
  }

  public void receiveFromFollowees(Set<Candidate> candidates) {
    if (is_received_from_setup_) {
      is_received_from_setup_ = false;
    }
    received_from_followee_ = new HashSet<Candidate>(candidates);
  }
}
